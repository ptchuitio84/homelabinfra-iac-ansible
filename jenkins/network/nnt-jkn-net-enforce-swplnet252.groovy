// =============================================================================
// jenkins/network/nnt-jkn-net-enforce-swplnet252.groovy
// =============================================================================
// JOB NAME: nnt-jkn-net-enforce-swplnet252
//
// PURPOSE:
// Enforces the baseline config on swplnet252 (Arista 7050SX-64, EOS 4.28).
// Pulls both the Ansible repo and the network configs repo on ans001,
// then runs the enforce_baseline playbook. Saves to startup-config only
// if changes were applied.
//
// SCHEDULE: Daily at 4am. Run manually after editing the baseline in
//           homelabinfra-network-configs.
//
// EXECUTION MODEL:
// Jenkins SSHs into ans001 (10.10.1.31). Ansible connects to swplnet252
// via eAPI (HTTPS). Jenkins never touches the switch directly.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-net-enforce-swplnet252
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/network/nnt-jkn-net-enforce-swplnet252.groovy
//   7. Save
// =============================================================================

pipeline {

    agent any

    triggers {
        cron('0 4 * * *')
    }

    environment {
        ANSIBLE_NODE         = '10.10.1.31'
        ANSIBLE_USER         = 'root'
        ANSIBLE_REPO_PATH    = '/opt/homelabinfra-iac-ansible'
        NETWORK_CONFIGS_PATH = '/opt/homelabinfra-network-configs'
        ANSIBLE_SSH_CRED     = 'ansible-node-ssh-key'
        VAULT_PASS_FILE      = '/root/.ansible/vault_pass.txt'
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

        stage('Sync repos to control node') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && git pull && \
                             cd ${NETWORK_CONFIGS_PATH} && git pull'
                    """
                }
            }
        }

        stage('Enforce baseline — swplnet252') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                                playbooks/network/enforce_baseline_swplnet252.yml \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'swplnet252 baseline enforcement completed successfully.'
        }
        failure {
            echo 'Pipeline failed — check console output above for details.'
        }
    }
}
