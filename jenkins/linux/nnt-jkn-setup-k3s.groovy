// =============================================================================
// nnt-jkn-setup-k3s.groovy — k3s Cluster Setup Pipeline
// =============================================================================
// Deploys k3s to the homelab k8s cluster:
//   hmvlapk8s001 (10.10.1.61) — control plane
//   hmvlapk8s002 (10.10.1.62) — worker
//   hmvlapk8s003 (10.10.1.63) — worker
//
// Control plane is configured first; workers join using the generated node token.
// Idempotent — safe to re-run if a node needs to be re-added.
// =============================================================================

pipeline {
    agent { label 'ansible' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify connectivity') {
            steps {
                withCredentials([file(credentialsId: 'ansible-vault-pass', variable: 'VAULT_PASS_FILE')]) {
                    sh """
                        ansible k8s_nodes \
                          -i inventory/ \
                          --vault-password-file \$VAULT_PASS_FILE \
                          -m ping
                    """
                }
            }
        }

        stage('Deploy k3s') {
            steps {
                withCredentials([file(credentialsId: 'ansible-vault-pass', variable: 'VAULT_PASS_FILE')]) {
                    sh """
                        ansible-playbook \
                          -i inventory/ \
                          --vault-password-file \$VAULT_PASS_FILE \
                          playbooks/linux/setup_k3s.yml
                    """
                }
            }
        }

        stage('Verify cluster') {
            steps {
                withCredentials([file(credentialsId: 'ansible-vault-pass', variable: 'VAULT_PASS_FILE')]) {
                    sh """
                        ansible k8s_control_plane \
                          -i inventory/ \
                          --vault-password-file \$VAULT_PASS_FILE \
                          -m command \
                          -a 'k3s kubectl get nodes -o wide'
                    """
                }
            }
        }

    }

    post {
        success {
            echo "k3s cluster is up. All 3 nodes should be Ready."
            echo "To use kubectl locally: copy /etc/rancher/k3s/k3s.yaml from hmvlapk8s001, replace 127.0.0.1 with 10.10.1.61."
        }
        failure {
            echo "Pipeline failed. Check the output above — control plane must complete before workers can join."
        }
    }
}
