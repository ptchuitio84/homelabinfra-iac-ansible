// =============================================================================
// jenkins/infra/nnt-jkn-deploy-platform.groovy
// =============================================================================
// JOB NAME: nnt-jkn-deploy-platform
//
// PURPOSE:
//   Deploys the core infrastructure platform layer in dependency order.
//   Run this first when building or rebuilding the environment from scratch.
//   All downstream layers (monitoring, k8s, apps) depend on this layer being up.
//
// STAGES (in dependency order):
//   1. Vault        — secrets backend; everything else reads from here
//   2. Minio        — S3-compatible object store; Terraform state + Loki + Velero
//   3. NetBox       — IPAM source of truth
//   4. NFS Server   — shared storage; k8s PVCs depend on this
//   5. Harbor       — private container registry; k8s pulls images from here
//   6. Terraform Node — runner for homelabinfra-iac-vms GitOps pipeline
//
// SCHEDULE: Manual trigger only — run on environment rebuild or role changes.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-deploy-platform
//   2. Place in the nnt-infra-deploy folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/infra/nnt-jkn-deploy-platform.groovy
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
        timeout(time: 90, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sshagent(['root']) {
                    sh "ssh -o StrictHostKeyChecking=no ${ANS001} 'cd ${ANSIBLE_REPO_PATH} && git pull'"
                }
            }
        }

        stage('Vault') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_vault.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('Minio') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_minio.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('NetBox') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_netbox.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('NFS Server') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_nfs_server.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('Harbor') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_harbor.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('Terraform Node') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_terraform_node.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'Platform layer deployed successfully. Ready for monitoring and k8s layers.'
        }
        failure {
            echo 'Platform deployment failed. Check the failed stage — downstream layers cannot proceed until this is resolved.'
        }
    }
}
