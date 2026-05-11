// =============================================================================
// jenkins/svc-ops/nnt-svc-ops-update-plex.groovy
// =============================================================================
// JOB NAME:   nnt-svc-ops/update-plex
// FOLDER:     nnt-svc-ops
//
// PURPOSE:
//   Upgrade Plex Media Server on HMPPLXAP002 to a specified version.
//   Downloads the RPM directly on the target host, stops the service,
//   installs, restarts, and verifies the new version is running.
//
// TARGET:
//   Host:    hmpplxap002.nnt.com (10.100.7.10)
//   Group:   plex_servers
//   Service: plexmediaserver
//
// HOW TO FIND THE VERSION STRING:
//   1. Open Plex Web → Settings → Troubleshooting → "Show release notes"
//      OR go to plex.tv/media-server-downloads
//   2. Copy the full version including build hash: e.g. 1.43.1.10611-abc123de
//   3. Paste into the VERSION parameter below
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
            name:         'VERSION',
            defaultValue: '',
            description:  'Required — full Plex version string including build hash (e.g. 1.43.1.10611-abc123de). Get it from plex.tv/media-server-downloads.'
        )
        string(
            name:         'REASON',
            defaultValue: '',
            description:  'Required — why are you updating Plex? (e.g. "Security fix in 1.43.1")'
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
                    if (!params.VERSION?.trim()) {
                        error('VERSION is required. Provide the full Plex version string including build hash.')
                    }
                    if (!params.REASON?.trim()) {
                        error('REASON is required. Describe why you are updating Plex.')
                    }
                    echo "Operator : ${env.BUILD_USER_ID ?: 'unknown'}"
                    echo "Version  : ${params.VERSION}"
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
                        -e plex_version=${params.VERSION} \\
                        -v
                """
            }
        }

    }

    post {
        success {
            echo "plexmediaserver upgraded to ${params.VERSION} on HMPPLXAP002. Reason: ${params.REASON}"
        }
        failure {
            echo "Upgrade FAILED on HMPPLXAP002. Check the Ansible output above. Service may be stopped — verify manually."
        }
    }
}
