// =============================================================================
// jenkins/linux/nnt-jkn-lin-enforce-baseline.groovy
// =============================================================================
// JOB NAME: nnt-jkn-lin-enforce-baseline
//
// PURPOSE:
//   Enforce the Linux baseline (common + login_banner roles) against all Linux
//   hosts. Makes real changes — not a dry run.
//
//   Triggered automatically by nnt-jkn-lin-drift-detect when drift is found.
//   Can also be run manually from Jenkins at any time.
//
//   Fails only if Ansible exits non-zero or any host reports failed > 0 or
//   unreachable > 0. Changed hosts are informational — not a failure condition.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-lin-enforce-baseline
//   2. Place in the nnt-infra-syncs folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/linux/nnt-jkn-lin-enforce-baseline.groovy
//   8. Save
// =============================================================================

pipeline {

    agent any

    environment {
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 45, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sh "cd ${ANSIBLE_REPO_PATH} && git pull"
            }
        }

        stage('Enforce baseline') {
            steps {
                script {
                    def rc = sh(
                        script: """
                            cd ${env.ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/linux/enforce_baseline.yml \
                                -i inventory/ \
                                --vault-password-file ${env.VAULT_PASS_FILE} \
                                2>&1 | tee /tmp/enforce_output.txt
                        """,
                        returnStatus: true
                    )

                    def output = sh(script: 'cat /tmp/enforce_output.txt', returnStdout: true)

                    def failedHosts = output.readLines().findAll { line ->
                        line =~ /:\s+ok=\d+.*(?:failed=[1-9]|unreachable=[1-9])/
                    }

                    def changedHosts = output.readLines().findAll { line ->
                        line =~ /:\s+ok=\d+.*changed=[1-9]/
                    }

                    if (rc != 0) {
                        error("Ansible exited with error (rc=${rc}). Check console output.")
                    }

                    if (failedHosts) {
                        error(
                            "Enforcement failed on ${failedHosts.size()} host(s):\n" +
                            failedHosts.join('\n')
                        )
                    }

                    if (changedHosts) {
                        echo(
                            "Baseline enforced on ${changedHosts.size()} host(s):\n" +
                            changedHosts.join('\n')
                        )
                    } else {
                        echo 'No changes required — all hosts already at baseline.'
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Baseline enforcement complete.'
        }
        failure {
            echo 'Enforcement failed. Check console output for details.'
        }
    }
}
