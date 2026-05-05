// =============================================================================
// jenkins/linux/nnt-jkn-lin-lynis.groovy
// =============================================================================
// JOB NAME: nnt-jkn-lin-lynis
//
// PURPOSE:
//   Runs a Lynis security audit across all Linux hosts on a weekly schedule.
//   Installs Lynis from Cisofy repo, audits each host, and writes the
//   hardening index to the node_exporter textfile collector.
//   Prometheus scrapes the metric — Grafana tracks scores over time.
//
// OUTCOMES:
//   SUCCESS — audit complete, hardening index written to all hosts
//   FAILURE — Ansible error or unreachable host
//
// SCHEDULE: Weekly on Sunday at 2am (before drift detect at 3am).
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-lin-lynis
//   2. Place in the nnt-infra-syncs folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/linux/nnt-jkn-lin-lynis.groovy
//   8. Save
// =============================================================================

pipeline {

    agent any

    triggers {
        cron('0 2 * * 0')  // Weekly — Sunday at 2am
    }

    environment {
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '12'))
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sh "cd ${ANSIBLE_REPO_PATH} && git pull"
            }
        }

        stage('Lynis audit') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/linux/run_lynis.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }
    }

    post {
        success {
            echo 'Lynis audit complete — hardening index updated on all hosts.'
        }
        failure {
            echo 'Lynis audit failed. Check console output for unreachable hosts or errors.'
        }
    }
}
