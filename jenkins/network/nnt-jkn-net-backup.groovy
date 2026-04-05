// =============================================================================
// jenkins/network/nnt-jkn-net-backup.groovy — Network Device Backup Pipeline
// =============================================================================
// JOB NAME: nnt-jkn-net-backup
//
// PURPOSE:
// Pulls the running configuration from all network devices (Cisco, Arista)
// and saves timestamped backups to network/backups/ on the control node.
//
// SCHEDULE: Every 5 minutes — polls GitHub for commits, runs on change.
//           Also runs on schedule via cron trigger (daily at 2am).
//
// EXECUTION MODEL:
// Jenkins (hmvlapjkn001) SSHs into the Ansible control node (hmvlapans001)
// and runs the playbook from there. Ansible connects to network devices.
// Jenkins never touches network devices directly.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-net-backup
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/network/nnt-jkn-net-backup.groovy
//   7. Save
// =============================================================================

pipeline {

    agent any

    triggers {
        // Poll GitHub every 5 minutes for new commits.
        pollSCM('H/5 * * * *')

        // Also run daily at 2am regardless of commits — ensures a fresh
        // backup exists even on days with no code changes.
        cron('0 2 * * *')
    }

    environment {
        ANSIBLE_NODE      = '10.10.1.31'
        ANSIBLE_USER      = 'root'
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        ANSIBLE_SSH_CRED  = 'ansible-node-ssh-key'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
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
                sshagent(credentials: [ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && git pull'
                    """
                }
            }
        }

        stage('Network Backup') {
            steps {
                sshagent(credentials: [ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && ansible-playbook playbooks/network/network_backup.yml'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'Network backup completed successfully.'
        }
        failure {
            echo 'Pipeline failed — check console output above for details.'
        }
    }
}
