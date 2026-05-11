// =============================================================================
// jenkins/svc-ops/nnt-svc-ops-update-plex.groovy
// =============================================================================
// JOB NAME:   nnt-svc-ops/update-plex
// FOLDER:     nnt-svc-ops
//
// PURPOSE:
//   Upgrade Plex Media Server on HMPPLXAP002 to the latest available version
//   using the official Plex yum repository (public — no credentials required).
//   Reports current → new version in the build log.
//
// TARGET:
//   Host:    hmpplxap002.nnt.com (10.100.7.10)
//   Group:   plex_servers
//   Service: plexmediaserver
//
// JENKINS SETUP:
//   1. Open folder: nnt-svc-ops
//   2. New Item → Pipeline → name: update-plex
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: git@github.com:ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/svc-ops/nnt-svc-ops-update-plex.groovy
//   8. Save
// =============================================================================

pipeline {

    agent { label 'ans001' }

    environment {
        ANSIBLE_HOST_KEY_CHECKING = 'False'
        ANSIBLE_FORCE_COLOR       = 'true'
        ANSIBLE_REPO_PATH         = '/opt/homelabinfra-iac-ansible'
        ANSIBLE_REPO_URL          = 'git@github.com:ptchuitio84/homelabinfra-iac-ansible.git'
    }

    parameters {
        string(
            name:         'REASON',
            defaultValue: '',
            description:  'Required — why are you updating Plex? (e.g. "1.43.1 released — library scanner fix")'
        )
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Validate') {
            steps {
                script {
                    if (!params.REASON?.trim()) {
                        error('REASON is required. Describe why you are updating Plex.')
                    }
                    echo "Operator : ${env.BUILD_USER_ID ?: 'unknown'}"
                    echo "Reason   : ${params.REASON}"
                    echo "Target   : hmpplxap002.nnt.com (plex_servers)"
                    echo "Node     : ${env.NODE_NAME}"
                }
            }
        }

        stage('Prepare repo') {
            steps {
                sh """
                    if [ ! -d "${ANSIBLE_REPO_PATH}/.git" ]; then
                        echo "Repo not found at ${ANSIBLE_REPO_PATH} — cloning..."
                        git clone ${ANSIBLE_REPO_URL} ${ANSIBLE_REPO_PATH}
                    else
                        echo "Repo found — pulling latest..."
                        cd ${ANSIBLE_REPO_PATH} && git pull
                    fi
                """
            }
        }

        stage('Upgrade plexmediaserver') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && ansible-playbook playbooks/linux/update_plex.yml \\
                        -i inventory/ \\
                        -l plex_servers \\
                        -v
                """
            }
        }

    }

    post {
        success {
            echo "plexmediaserver upgraded successfully on HMPPLXAP002. Reason: ${params.REASON}"
        }
        failure {
            echo "Upgrade FAILED on HMPPLXAP002. Check the Ansible output above. Service may be stopped — verify manually."
        }
    }
}
