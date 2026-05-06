// =============================================================================
// jenkins/infra/nnt-jkn-deploy-apps.groovy
// =============================================================================
// JOB NAME: nnt-jkn-deploy-apps
//
// PURPOSE:
//   Deploys standalone application servers that don't belong to the
//   platform, monitoring, or k8s layers.
//
// STAGES:
//   1. Plex      — media server (hmpplxap002)
//   2. Webserver — internal web server / AIgentic Solutions test target (hmvlapweb001)
//
// EXCLUDED (intentional):
//   - Jenkins    — cannot self-deploy; manage manually or via bootstrap script
//   - Ansible node — the control node runs the playbooks; circular dependency
//
// SCHEDULE: Manual trigger only.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-deploy-apps
//   2. Place in the nnt-infra-deploy folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/infra/nnt-jkn-deploy-apps.groovy
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
        timeout(time: 60, unit: 'MINUTES')
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

        stage('Plex') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_plex.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }

        stage('Webserver') {
            steps {
                sshagent(['root']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/setup_webserver.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'App servers deployed successfully.'
        }
        failure {
            echo 'App deployment failed. Check the failed stage in the pipeline view.'
        }
    }
}
