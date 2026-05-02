// =============================================================================
// jenkins/linux/nnt-jkn-lin-patch.groovy
// =============================================================================
// JOB NAME: nnt-jkn-lin-patch
//
// PURPOSE:
// Weekly OS patching for all OL9 Linux VMs (monitoring + jenkins).
// Runs dnf update on each host serially, reboots if required, validates
// systemd health after reboot. Fails loud if any host is unhealthy post-patch.
//
// TARGETS: hmvlapmon001, hmvlapmon002, hmvlapmon003, hmvlapjkn001
// EXCLUDED: hmvlapans001 (Ansible control node — patches itself separately)
//           hmpplxap002  (OL7 Plex — pending OL9 rebuild)
//
// NOTE: Jenkins reboots itself last. The pipeline survives because Ansible
//       on ans001 manages the reboot and waits for Jenkins to come back.
//
// SCHEDULE: Weekly Sunday at 2am.
//
// EXECUTION MODEL:
// Pipeline runs on the ans001 Jenkins agent (10.10.1.31). Ansible runs
// directly on ans001 — no SSH hop from Jenkins. When jkn001 reboots during
// patching, the executor on ans001 stays alive and the build completes cleanly.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-lin-patch
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/linux/nnt-jkn-lin-patch.groovy
//   7. Save
// =============================================================================

pipeline {

    agent any

    triggers {
        cron('0 2 * * 0')  // Sunday at 2am
    }

    environment {
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 2, unit: 'HOURS')  // patching + reboots for all hosts
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Sync repo to control node') {
            steps {
                // Executor runs on ans001 directly — no SSH hop needed
                sh "cd ${ANSIBLE_REPO_PATH} && git pull"
            }
        }

        stage('Patch Linux VMs') {
            steps {
                // Executor runs on ans001 — run ansible-playbook directly, no SSH hop
                sh """
                    cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                        playbooks/linux/patch_linux_vms.yml \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }
    }

    post {
        success {
            echo 'All Linux VMs patched and validated successfully.'
        }
        failure {
            echo 'Patching failed — check console output. At least one host may need manual review.'
        }
    }
}
