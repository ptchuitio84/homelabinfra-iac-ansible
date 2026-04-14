// =============================================================================
// jenkins/network/nnt-jkn-net-enforce-swplnet252.groovy
// =============================================================================
// JOB NAME: nnt-jkn-net-enforce-swplnet252
//
// PURPOSE:
// Enforces the baseline config on swplnet252 (Arista 7050SX-64, EOS 4.28).
// Runs a dry-run first — if drift is detected, pauses for manual approval
// before applying changes. If compliant, completes automatically.
//
// APPROVAL GATE:
//   - Dry run detects changed lines → pipeline pauses for up to 24h
//   - Reviewer checks dry-run output in console log → approves or aborts
//   - No drift detected → gate skipped, pipeline completes automatically
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
        timeout(time: 1, unit: 'HOURS')  // covers dry-run + enforce; input gate has its own 24h timeout
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

        stage('Dry Run — swplnet252') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    script {
                        def output = sh(
                            script: """
                                ssh -o StrictHostKeyChecking=no ${env.ANSIBLE_USER}@${env.ANSIBLE_NODE} \
                                    'cd ${env.ANSIBLE_REPO_PATH} && ansible-playbook \
                                        playbooks/network/enforce_baseline_swplnet252.yml \
                                        --vault-password-file ${env.VAULT_PASS_FILE} \
                                        --check 2>&1'
                            """,
                            returnStdout: true
                        ).trim()
                        echo output
                        // Ansible PLAY RECAP: changed=N where N>0 means drift detected
                        env.HAS_CHANGES = (output =~ /changed=[1-9]/) ? 'true' : 'false'
                        echo "Drift detected: ${env.HAS_CHANGES}"
                    }
                }
            }
        }

        stage('Approval Gate') {
            when {
                expression { env.HAS_CHANGES == 'true' }
            }
            steps {
                timeout(time: 24, unit: 'HOURS') {
                    input(
                        message: 'swplnet252 — drift detected. Review dry-run output above, then approve to enforce or abort.',
                        ok: 'Enforce'
                    )
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
        aborted {
            echo 'Enforcement aborted at approval gate — no changes applied to swplnet252.'
        }
    }
}
