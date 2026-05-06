// =============================================================================
// jenkins/monitoring/nnt-jkn-deploy-node-exporter.groovy
// =============================================================================
// JOB NAME: nnt-jkn-deploy-node-exporter
//
// PURPOSE:
//   Deploys or re-deploys Prometheus node_exporter across all Linux hosts.
//   Runs on-demand — use this whenever the node_exporter role changes
//   (version bump, new textfile collector config, service template update).
//   node_exporter is restarted only if the binary or service file changed.
//
// OUTCOMES:
//   SUCCESS — node_exporter deployed and running on all Linux hosts
//   FAILURE — Ansible error or unreachable host
//
// SCHEDULE: Manual trigger only — no cron. Run after role changes.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-deploy-node-exporter
//   2. Place in the nnt-infra-monitoring folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/monitoring/nnt-jkn-deploy-node-exporter.groovy
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
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh "ssh -o StrictHostKeyChecking=no ${ANS001} 'cd ${ANSIBLE_REPO_PATH} && git pull'"
                }
            }
        }

        stage('Deploy node_exporter') {
            steps {
                sshagent(credentials: ['ansible-node-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANS001} \
                            'cd ${ANSIBLE_REPO_PATH} && \
                            /usr/local/bin/ansible-playbook \
                                playbooks/monitoring/deploy_node_exporter.yml \
                                -i inventory/ \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'node_exporter deployed successfully across all Linux hosts.'
        }
        failure {
            echo 'Deployment failed. Check console output for unreachable hosts or errors.'
        }
    }
}
