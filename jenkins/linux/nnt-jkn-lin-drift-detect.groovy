// =============================================================================
// jenkins/linux/nnt-jkn-lin-drift-detect.groovy
// =============================================================================
// JOB NAME: nnt-jkn-lin-drift-detect
//
// PURPOSE:
//   Nightly Ansible --check run against all Linux hosts to detect configuration
//   drift from the baseline (common role + login_banner role).
//
//   If drift is found, automatically triggers nnt-jkn-lin-enforce-baseline and
//   waits for it to complete. The build succeeds if enforcement succeeds.
//   The build fails only if Ansible errors out or enforcement fails.
//
// OUTCOMES:
//   SUCCESS — no drift detected, OR drift found and successfully remediated
//   FAILURE — Ansible error during check, OR enforcement pipeline failed
//
// SCHEDULE: Daily at 3am (6 hours after Sunday patching window).
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-lin-drift-detect
//   2. Place in the nnt-infra-syncs folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/linux/nnt-jkn-lin-drift-detect.groovy
//   8. Save
// =============================================================================

pipeline {

    agent any

    triggers {
        cron('0 3 * * *')  // Daily at 3am
    }

    environment {
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sh "cd ${ANSIBLE_REPO_PATH} && git pull"
            }
        }

        stage('Baseline drift check') {
            steps {
                script {
                    def rc = sh(
                        script: """
                            cd ${env.ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/drift_detect.yml \
                                -i inventory/ \
                                --check --diff \
                                --vault-password-file ${env.VAULT_PASS_FILE} \
                                2>&1 | tee /tmp/drift_output.txt
                        """,
                        returnStatus: true
                    )

                    def output = sh(script: 'cat /tmp/drift_output.txt', returnStdout: true)

                    if (rc != 0) {
                        error("Ansible exited with error (rc=${rc}). Check console output.")
                    }

                    def driftedHosts = output.readLines().findAll { line ->
                        line =~ /:\s+ok=\d+.*changed=[1-9]/
                    }

                    if (driftedHosts) {
                        echo(
                            "Drift detected on ${driftedHosts.size()} host(s):\n" +
                            driftedHosts.join('\n') +
                            "\n\nTriggering enforcement..."
                        )
                        build job: 'nnt-infra-syncs/nnt-jkn-lin-enforce-baseline',
                              wait: true,
                              propagate: true
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Fleet is clean — no drift, or drift detected and successfully remediated.'
        }
        failure {
            echo 'Check or enforcement failed. See console output for details.'
        }
    }
}
