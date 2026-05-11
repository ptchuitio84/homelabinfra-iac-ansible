// =============================================================================
// jenkins/svc-ops/nnt-svc-ops-restart-plex.groovy
// =============================================================================
// JOB NAME:   nnt-svc-ops/restart-plex
// FOLDER:     nnt-svc-ops
//
// PURPOSE:
//   Restart the Plex Media Server service on HMPPLXAP002.
//   Safe for non-technical operators — requires a reason before executing.
//   All runs are logged in Jenkins build history with operator name and reason.
//
// TARGET:
//   Host:    hmpplxap002.nnt.com (10.100.7.10)
//   Group:   plex_servers
//   Service: plexmediaserver
//
// JENKINS SETUP:
//   1. Open folder: nnt-svc-ops
//   2. New Item → Pipeline → name: restart-plex
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: git@github.com:ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/svc-ops/nnt-svc-ops-restart-plex.groovy
//   8. Save
// =============================================================================

pipeline {

    agent any

    environment {
        ANSIBLE_HOST_KEY_CHECKING = 'False'
        ANSIBLE_FORCE_COLOR       = 'true'
    }

    parameters {
        string(
            name:         'REASON',
            defaultValue: '',
            description:  'Required — why are you restarting Plex? (e.g. "Updated library scan settings")'
        )
    }

    stages {

        stage('Validate') {
            steps {
                script {
                    if (!params.REASON?.trim()) {
                        error('REASON is required. Describe why you are restarting Plex before running this job.')
                    }
                    echo "Operator : ${env.BUILD_USER_ID ?: 'unknown'}"
                    echo "Reason   : ${params.REASON}"
                    echo "Target   : hmpplxap002.nnt.com (plex_servers)"
                    echo "Service  : plexmediaserver"
                }
            }
        }

        stage('Restart plexmediaserver') {
            steps {
                sh """
                    ansible-playbook playbooks/linux/restart_service.yml \\
                        -i inventory/ \\
                        -l plex_servers \\
                        -e service_name=plexmediaserver \\
                        -v
                """
            }
        }

    }

    post {
        success {
            echo "plexmediaserver restarted successfully on HMPPLXAP002. Reason: ${params.REASON}"
        }
        failure {
            echo "Restart FAILED on HMPPLXAP002. Check the Ansible output above."
        }
    }
}
