// =============================================================================
// jenkins/monitoring/nnt-jkn-deploy-monitoring.groovy
// =============================================================================
// JOB NAME: nnt-jkn-deploy-monitoring
//
// PURPOSE:
//   Deploys the full monitoring stack in dependency order.
//   Requires the platform layer (Vault, NFS) to be up first.
//
// STAGES (in dependency order):
//   1.  node_exporter    — fleet-wide host metrics; must exist before Prometheus scrapes
//   2.  Prometheus       — metrics backend (hmvlapmon002)
//   3.  Alertmanager     — alert routing via Exchange email (hmvlapmon002)
//   4.  Grafana          — dashboards (hmvlapmon001)
//   5.  Loki             — log aggregation backend (hmvlapmon003)
//   6.  Promtail         — log shipper fleet-wide → Loki
//   7.  SNMP Exporter    — network switch metrics (hmvlapmon002)
//   8.  VMware Exporter  — vCenter/ESXi metrics (hmvlapmon002)
//   9.  Meraki Exporter  — Meraki MX metrics (hmvlapmon003)
//   10. Unpoller         — UniFi metrics (hmvlapmon003)
//   11. Telegraf vSphere — vSphere telemetry (hmvlapmon002)
//
// SCHEDULE: Manual trigger only — run on environment rebuild or role changes.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-deploy-monitoring
//   2. Place in the nnt-infra-monitoring folder
//   3. Pipeline → Definition: Pipeline script from SCM
//   4. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   5. Credentials: github-pat
//   6. Branch: */main
//   7. Script Path: jenkins/monitoring/nnt-jkn-deploy-monitoring.groovy
//   8. Save
// =============================================================================

pipeline {

    agent any

    environment {
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 90, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync repo') {
            steps {
                sh "cd ${ANSIBLE_REPO_PATH} && git pull"
            }
        }

        stage('node_exporter') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/deploy_node_exporter.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Prometheus') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/setup_prometheus.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Alertmanager') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/linux/setup_alertmanager.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Grafana') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/setup_grafana.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Loki') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/setup_loki.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Promtail') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/deploy_promtail.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('SNMP Exporter') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/setup_snmp_exporter.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('VMware Exporter') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/setup_vmware_exporter.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Meraki Exporter') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/linux/setup_meraki_exporter.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Unpoller') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/linux/setup_unpoller.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }

        stage('Telegraf vSphere') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && \
                    /usr/local/bin/ansible-playbook \
                        playbooks/monitoring/setup_telegraf_vsphere.yml \
                        -i inventory/ \
                        --vault-password-file ${VAULT_PASS_FILE}
                """
            }
        }
    }

    post {
        success {
            echo 'Monitoring stack deployed successfully.'
        }
        failure {
            echo 'Monitoring deployment failed. Check the failed stage in the pipeline view.'
        }
    }
}
