// =============================================================================
// jenkins/linux/nnt-jkn-lin-drift-detect.groovy
// =============================================================================
// JOB NAME: nnt-jkn-lin-drift-detect
//
// PURPOSE:
//   Nightly Ansible --check run against all Linux hosts to detect configuration
//   drift from the baseline (common role + login_banner role).
//
//   If any host has drifted, the pipeline fails → Prometheus picks up the
//   JenkinsBuildFailed alert → email to infra-ops@nanonetech.com.
//
//   No changes are made to any host — this is a read-only check.
//
// WHAT COUNTS AS DRIFT:
//   Any task in the common or login_banner role that would change state:
//     - Timezone mismatch
//     - Base package missing
//     - NTP config changed
//     - SELinux mode changed
//     - Login banner content changed
//     - sshd Banner directive missing
//
// SCHEDULE: Daily at 3am (6 hours after Sunday patching window).
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-lin-drift-detect
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/linux/nnt-jkn-lin-drift-detect.groovy
//   7. Save
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
                    // --check: dry run, no changes applied.
                    // Ansible exits 0 even when tasks would change — parse PLAY RECAP for drift.
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

                    // PLAY RECAP lines with changed > 0 indicate drift.
                    // Format: "hostname : ok=N changed=N unreachable=N failed=N ..."
                    def driftedHosts = output.readLines().findAll { line ->
                        line =~ /:\s+ok=\d+.*changed=[1-9]/
                    }

                    if (driftedHosts) {
                        error(
                            "DRIFT DETECTED on ${driftedHosts.size()} host(s):\n" +
                            driftedHosts.join('\n') +
                            "\n\nRun with --check --diff to see exact changes needed."
                        )
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'No drift detected — all hosts match baseline.'
        }
        failure {
            echo 'Drift detected or playbook error. Check console output for details.'
        }
    }
}
