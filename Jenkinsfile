// =============================================================================
// Jenkinsfile — homelabinfra-iac-ansible Pipeline Definitions
// =============================================================================
// This file defines all CI/CD pipelines for the homelab infrastructure repo.
// Jenkins reads this file automatically when a pipeline job is pointed at
// this repo.
//
// PIPELINES DEFINED HERE:
//   1. Network Backup — runs network_backup.yml on a schedule
//
// HOW IT WORKS:
//   - Jenkins clones this repo using the github-pat credential
//   - Runs ansible-playbook from inside the cloned workspace
//   - Requires Ansible to be installed on the Jenkins server (hmvlapjkn001)
//     OR uses the Ansible control node (hmvlapans001) as the executor
//
// NOTE: Ansible is NOT installed on hmvlapjkn001 — Jenkins SSH's into
// hmvlapans001 to run playbooks. This keeps the control node as the single
// source of truth for all Ansible execution.
// =============================================================================

pipeline {

    // Run on the Jenkins server itself (hmvlapjkn001).
    // Jenkins will SSH to hmvlapans001 to execute Ansible commands.
    agent any

    // ==========================================================================
    // TRIGGERS
    // Poll GitHub every 5 minutes for new commits (H/5 = every 5 min, spread
    // across the minute to reduce load — "H" means Jenkins picks the exact minute).
    // When a commit is detected, the pipeline runs automatically.
    // ==========================================================================
    triggers {
        pollSCM('H/5 * * * *')
    }

    // ==========================================================================
    // ENVIRONMENT
    // Variables available to all stages in this pipeline.
    // ==========================================================================
    environment {
        // SSH credentials for connecting to the Ansible control node.
        // Stored in Jenkins credentials store — never hardcoded here.
        ANSIBLE_NODE      = '10.10.1.31'
        ANSIBLE_USER      = 'root'
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'

        // Jenkins SSH credential ID for the control node.
        // Add this in: Manage Jenkins → Credentials → Add → SSH Username with private key
        // ID must match exactly: ansible-node-ssh-key
        ANSIBLE_SSH_CRED  = 'ansible-node-ssh-key'
    }

    // ==========================================================================
    // OPTIONS
    // ==========================================================================
    options {
        // Keep only the last 10 build logs — prevents disk fill on /var/lib/jenkins.
        buildDiscarder(logRotator(numToKeepStr: '10'))

        // Fail the build if it runs longer than 30 minutes — catches hung playbooks.
        timeout(time: 30, unit: 'MINUTES')

        // Add timestamps to all console output — makes debugging easier.
        timestamps()
    }

    // ==========================================================================
    // STAGES
    // Each stage is a logical step in the pipeline. They run sequentially.
    // If any stage fails, subsequent stages are skipped and the build fails.
    // ==========================================================================
    stages {

        // ----------------------------------------------------------------------
        // Stage 1: Checkout
        // Clone/update the repo in the Jenkins workspace.
        // Uses the github-pat credential configured in Jenkins.
        // ----------------------------------------------------------------------
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ----------------------------------------------------------------------
        // Stage 2: Sync repo to control node
        // Push the latest version of the repo to hmvlapans001 via SSH.
        // The control node runs git pull so it has the latest playbooks
        // before Ansible executes anything.
        // ----------------------------------------------------------------------
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

        // ----------------------------------------------------------------------
        // Stage 3: Network Backup
        // SSH into the Ansible control node and run the network backup playbook.
        // The control node connects to the switches and saves their configs.
        // ----------------------------------------------------------------------
        stage('Network Backup') {
            steps {
                sshagent(credentials: [ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && ansible-playbook playbooks/network_backup.yml'
                    """
                }
            }
        }
    }

    // ==========================================================================
    // POST
    // Actions that run after all stages complete, regardless of success/failure.
    // ==========================================================================
    post {
        success {
            echo 'Network backup completed successfully.'
        }
        failure {
            echo 'Pipeline failed — check console output above for details.'
        }
    }
}
