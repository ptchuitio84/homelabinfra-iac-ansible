// =============================================================================
// jenkins/k8s/nnt-jkn-deploy-k8s.groovy
// =============================================================================
// JOB NAME: nnt-jkn-deploy-k8s
//
// PURPOSE:
//   Deploys the full Kubernetes platform layer in dependency order.
//   Requires platform layer (Vault, NFS, Harbor) to be up first.
//   ArgoCD takes over app delivery after this pipeline completes —
//   Keycloak, SonarQube, and any ArgoCD-managed app will auto-sync from
//   homelabinfra-k8s-apps once ArgoCD repo is wired.
//
// STAGES (in dependency order):
//   1. k3s              — cluster bootstrap (control plane + workers)
//   2. MetalLB          — L2 load balancer (pool 10.10.1.200-210)
//   3. Traefik          — ingress controller (10.10.1.200)
//   4. NFS Provisioner  — StorageClass backed by hmvlapnfs001
//   5. ArgoCD           — GitOps controller
//   6. ArgoCD Repo      — wire ArgoCD to homelabinfra-k8s-apps (GitHub PAT from Vault)
//   7. Keycloak SSO     — OIDC wiring for ArgoCD + Grafana (runs after ArgoCD deploys Keycloak)
//
// NOTE:
//   Keycloak SSO stage assumes Keycloak is already deployed and healthy via ArgoCD.
//   If running this pipeline immediately after ArgoCD, allow ~5 min for ArgoCD
//   to sync and Keycloak to become ready before the SSO stage runs.
//
// SCHEDULE: Manual trigger only — run on environment rebuild or role changes.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-deploy-k8s
//   2. Place in the nnt-infra-k8s folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/k8s/nnt-jkn-deploy-k8s.groovy
//   8. Save
// =============================================================================

pipeline {

    agent any

    environment {
        ANS001            = 'root@10.10.1.31'
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh "ssh -o StrictHostKeyChecking=no ${ANS001} 'cd ${ANSIBLE_REPO_PATH} && git pull'"
                }
            }
        }

        stage('k3s') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_k3s.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('MetalLB') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_metallb.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('Traefik') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_traefik.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('NFS Provisioner') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_nfs_provisioner.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('ArgoCD') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_argocd.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('ArgoCD Repo') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_argocd_repo.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('Keycloak SSO') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_keycloak_sso.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'k8s platform deployed. ArgoCD is live — apps in homelabinfra-k8s-apps will auto-sync.'
        }
        failure {
            echo 'k8s deployment failed. Check the failed stage. Each stage depends on the previous.'
        }
    }
}
