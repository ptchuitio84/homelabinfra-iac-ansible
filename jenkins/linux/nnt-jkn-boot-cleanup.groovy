// =============================================================================
// jenkins/linux/nnt-jkn-boot-cleanup.groovy
// =============================================================================
// JOB NAME: nnt-jkn-boot-cleanup
//
// PURPOSE:
// Removes old kernels to reclaim /boot space and expands root LVM volumes
// across all OL9 Linux VMs. Safe to run repeatedly — all operations are
// idempotent. Plex server (OL7) is excluded automatically.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-boot-cleanup
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/linux/nnt-jkn-boot-cleanup.groovy
//   7. Save
// =============================================================================

pipeline {

    agent { label 'ans001' }

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

        stage('Boot cleanup + disk expansion') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                        playbooks/linux/boot_cleanup.yml \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

    }

    post {
        success {
            echo 'Boot cleanup and disk expansion complete across all Linux VMs.'
        }
        failure {
            echo 'Cleanup failed on one or more hosts — review console output.'
        }
    }
}
