// =============================================================================
// jenkins/windows/nnt-jkn-win-dns-sync.groovy — DNS Zone Sync Pipeline
// =============================================================================
// JOB NAME: nnt-jkn-win-dns-sync
//
// PURPOSE:
// Syncs all A records from nnt.com (source of truth) to nanonetech.com and
// any other target zones defined in group_vars/ad_dns_sync/vars.yml.
// Idempotent — adds and updates only, never deletes from target zones.
//
// SCHEDULE: Daily at 3am. Run manually after adding records to nnt.com.
//
// EXECUTION MODEL:
// Jenkins (hmvlapjkn001) SSHs into the Ansible control node (hmvlapans001)
// and runs the playbook from there. Ansible connects to dc01 via WinRM.
// Jenkins never touches the DC directly.
//
// PRE-REQUISITE ON ans001:
//   ansible-galaxy collection install ansible.windows
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-win-dns-sync
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/windows/nnt-jkn-win-dns-sync.groovy
//   7. Save
// =============================================================================

pipeline {

    agent any

    triggers {
        // Run daily at 3am — after any overnight DNS changes in nnt.com.
        cron('0 3 * * *')
    }

    environment {
        ANSIBLE_NODE      = '10.10.1.31'
        ANSIBLE_USER      = 'root'
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        ANSIBLE_SSH_CRED  = 'ansible-node-ssh-key'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Sync repo to control node') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && git pull'
                    """
                }
            }
        }

        stage('DNS Zone Sync') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && ansible-playbook playbooks/windows/dns_zone_sync.yml \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'DNS zone sync completed successfully.'
        }
        failure {
            echo 'Pipeline failed — check console output above for details.'
        }
    }
}
