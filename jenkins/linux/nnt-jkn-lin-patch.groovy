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
// Jenkins SSHes into ans001 (10.10.1.31). Ansible connects to each target
// VM and patches it. Jenkins never connects to target VMs directly.
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
        ANSIBLE_NODE      = '10.10.1.31'
        ANSIBLE_USER      = 'root'
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        ANSIBLE_SSH_CRED  = 'ansible-node-ssh-key'
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
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && git pull'
                    """
                }
            }
        }

        stage('Patch Linux VMs') {
            steps {
                sshagent(credentials: [env.ANSIBLE_SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${ANSIBLE_USER}@${ANSIBLE_NODE} \
                            'cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                                playbooks/linux/patch_linux_vms.yml \
                                --vault-password-file ${VAULT_PASS_FILE}'
                    """
                }
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
