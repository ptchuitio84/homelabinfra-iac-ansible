// =============================================================================
// jenkins/network/nnt-jkn-net-enforce-swplnet253.groovy
// =============================================================================
// JOB NAME: nnt-jkn-net-enforce-swplnet253
//
// PURPOSE:
// Enforces the baseline config on swplnet253 (Arista 7048T-A, EOS 4.12).
// Pulls both the Ansible repo and the network configs repo on ans001,
// then runs the enforce_baseline playbook. Config is pushed line-by-line
// via configure terminal (EOS 4.12 workaround — no cli_config support).
//
// NOTE: This job always pushes config — no delta tracking on EOS 4.12.
//       It is safe to run repeatedly (EOS ignores no-op lines).
//       No approval gate: check mode is unreliable on EOS 4.12; the gate
//       would fire on every scheduled run regardless of actual drift.
//
// SCHEDULE: Daily at 4am. Run manually after editing the baseline in
//           homelabinfra-network-configs.
//
// EXECUTION MODEL:
// Jenkins SSHs into ans001 (10.10.1.31). Ansible connects to swplnet253
// via SSH (network_cli). Jenkins never touches the switch directly.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-net-enforce-swplnet253
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/network/nnt-jkn-net-enforce-swplnet253.groovy
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
        timeout(time: 30, unit: 'MINUTES')  // EOS 4.12 line-by-line push is slow
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

        stage('Enforce baseline — swplnet253') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                                playbooks/network/enforce_baseline_swplnet253.yml \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'swplnet253 baseline enforcement completed successfully.'
        }
        failure {
            echo 'Pipeline failed — check console output above for details.'
        }
    }
}
