// =============================================================================
// jenkins/infra/nnt-jkn-provision-vm.groovy
// =============================================================================
// JOB NAME: nnt-jkn-provision-vm
//
// PURPOSE:
// Zero-touch VM provisioning pipeline. Accepts a JSON payload defining the
// VM to build, allocates the next available IP from NetBox, clones the VM
// from the golden template via Ansible + VMware Guest Customization, then
// runs the service-specific configuration playbook.
//
// FLOW:
//   JSON input → NetBox API (next available IP) → provision_vm role → service playbook
//
// JSON PARAMETER (VM_SPEC):
//   {
//     "vm_name":     "hmvlapweb001",       // vCenter name + OS hostname
//     "cpu":         2,
//     "ram_mb":      4096,
//     "os_template": "PLTMPOL0903302026",  // vCenter template name
//     "esxi_host":   "hsplv021.nnt.com",   // target ESXi host
//     "datastore":   "LUNPLV163001",        // target datastore
//     "role":        "webserver"            // maps to playbooks/linux/setup_<role>.yml
//   }
//
// NETBOX:
//   Allocates next available IP from prefix 10.10.0.0/23 (INFRACORENET).
//   Registers the IP in NetBox with the VM name as description.
//   Requires a NetBox API token stored in Jenkins credentials as 'netbox-api-token'.
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-provision-vm
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/infra/nnt-jkn-provision-vm.groovy
//   7. Save
//   Add credentials: netbox-api-token (Secret text)
// =============================================================================

pipeline {

    agent { label 'ans001' }

    parameters {
        text(
            name: 'VM_SPEC',
            defaultValue: '''\
{
  "vm_name":     "hmvlapweb001",
  "cpu":         2,
  "ram_mb":      4096,
  "os_template": "PLTMPOL0903302026",
  "esxi_host":   "hsplv021.nnt.com",
  "datastore":   "LUNPLV163001",
  "gateway":     "10.10.0.1",
  "role":        "webserver"
}''',
            description: 'VM specification JSON'
        )
    }

    environment {
        ANSIBLE_REPO_PATH = '/opt/homelabinfra-iac-ansible'
        VAULT_PASS_FILE   = '/root/.ansible/vault_pass.txt'
        NETBOX_URL        = 'http://10.10.1.71'
        NETBOX_PREFIX     = '10.10.0.0/23'
        NETBOX_TOKEN      = credentials('netbox-api-token')
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }

    stages {

        stage('Parse VM spec') {
            steps {
                script {
                    def spec = new groovy.json.JsonSlurperClassic().parseText(params.VM_SPEC)
                    env.VM_NAME     = spec.vm_name
                    env.VM_CPU      = spec.cpu.toString()
                    env.VM_RAM      = spec.ram_mb.toString()
                    env.VM_TEMPLATE = spec.os_template
                    env.VM_HOST     = spec.esxi_host
                    env.VM_DS       = spec.datastore
                    env.VM_GATEWAY  = spec.gateway ?: '10.10.0.1'
                    env.VM_ROLE     = spec.role
                    echo "Building: ${env.VM_NAME} | ${env.VM_CPU}vCPU ${env.VM_RAM}MB | role: ${env.VM_ROLE}"
                }
            }
        }

        stage('Sync repo to control node') {
            steps {
                sh "cd ${ANSIBLE_REPO_PATH} && git pull"
            }
        }

        stage('Pre-flight check') {
            steps {
                script {
                    // Block if a prior build with this VM name succeeded
                    def build = currentBuild.previousBuild
                    while (build != null) {
                        if (build.result == 'SUCCESS') {
                            def buildVars = build.buildVariables
                            if (buildVars?.VM_SPEC) {
                                def priorSpec = new groovy.json.JsonSlurperClassic().parseText(buildVars.VM_SPEC)
                                if (priorSpec.vm_name == env.VM_NAME) {
                                    error("ABORTED: ${env.VM_NAME} was successfully provisioned in a prior build. Remove it from vCenter and NetBox first if this is intentional.")
                                }
                            }
                        }
                        build = build.previousBuild
                    }

                    // Clean up any stale NetBox IP left over from a failed prior run
                    def checkResponse = sh(
                        script: """
                            curl -s -H "Authorization: Token ${env.NETBOX_TOKEN}" \
                                 -H "Content-Type: application/json" \
                                 "${env.NETBOX_URL}/api/ipam/ip-addresses/?description=${env.VM_NAME}"
                        """,
                        returnStdout: true
                    ).trim()

                    def checkJson = new groovy.json.JsonSlurperClassic().parseText(checkResponse)
                    if (checkJson.count > 0) {
                        def staleId = checkJson.results[0].id.toString()
                        def staleIP = checkJson.results[0].address
                        echo "Stale NetBox IP found for ${env.VM_NAME} (${staleIP}) from failed run — reclaiming."
                        sh """
                            curl -s -X DELETE \
                                 -H "Authorization: Token ${env.NETBOX_TOKEN}" \
                                 "${env.NETBOX_URL}/api/ipam/ip-addresses/${staleId}/"
                        """
                    }

                    echo "Pre-flight passed — ${env.VM_NAME} cleared to build."
                }
            }
        }

        stage('Allocate IP from NetBox') {
            steps {
                script {
                    // URL-encode the prefix for the API call
                    def prefixEncoded = env.NETBOX_PREFIX.replace('/', '%2F')

                    // Get the prefix ID from NetBox
                    def prefixResponse = sh(
                        script: """
                            curl -s -H "Authorization: Token ${env.NETBOX_TOKEN}" \
                                 -H "Content-Type: application/json" \
                                 "${env.NETBOX_URL}/api/ipam/prefixes/?prefix=${prefixEncoded}"
                        """,
                        returnStdout: true
                    ).trim()

                    def prefixJson = new groovy.json.JsonSlurperClassic().parseText(prefixResponse)
                    def prefixId = prefixJson.results[0].id
                    echo "NetBox prefix ID: ${prefixId}"

                    // Request next available IP from that prefix
                    def allocResponse = sh(
                        script: """
                            curl -s -X POST \
                                 -H "Authorization: Token ${env.NETBOX_TOKEN}" \
                                 -H "Content-Type: application/json" \
                                 -d '{"description": "${env.VM_NAME}"}' \
                                 "${env.NETBOX_URL}/api/ipam/prefixes/${prefixId}/available-ips/"
                        """,
                        returnStdout: true
                    ).trim()

                    def allocJson = new groovy.json.JsonSlurperClassic().parseText(allocResponse)
                    // Strip CIDR suffix — provision_vm expects bare IP
                    env.VM_IP    = allocJson.address.split('/')[0]
                    env.NETBOX_IP_ID = allocJson.id.toString()
                    echo "Allocated IP: ${env.VM_IP} (NetBox ID: ${env.NETBOX_IP_ID})"
                }
            }
        }

        stage('Create DNS record') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                        playbooks/windows/dns_add_record.yml \
                        --vault-password-file ${VAULT_PASS_FILE} \
                        --extra-vars "dns_record_name=${env.VM_NAME} dns_record_ip=${env.VM_IP}"
                """
            }
        }

        stage('Provision VM') {
            steps {
                sh """
                    cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                        playbooks/infra/provision_vm.yml \
                        --vault-password-file ${VAULT_PASS_FILE} \
                        --extra-vars "vm_name=${env.VM_NAME} \
                                      vm_hostname=${env.VM_NAME} \
                                      vm_ip=${env.VM_IP} \
                                      vm_gateway=${env.VM_GATEWAY} \
                                      vm_vcpus=${env.VM_CPU} \
                                      vm_memory_mb=${env.VM_RAM} \
                                      vm_template=${env.VM_TEMPLATE} \
                                      vm_esxi_host=${env.VM_HOST} \
                                      vm_datastore=${env.VM_DS}"
                """
            }
        }

        stage('Wait for VM ready') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    sh """
                        sleep 30
                        while ! ssh -o StrictHostKeyChecking=no -o BatchMode=yes -o ConnectTimeout=5 root@${env.VM_IP} 'exit' 2>/dev/null; do
                            echo 'Waiting for SSH...'
                            sleep 10
                        done
                        echo 'SSH ready.'
                    """
                }
            }
        }

        stage('Configure service') {
            steps {
                script {
                    def playbook = "playbooks/linux/setup_${env.VM_ROLE}.yml"
                    sh """
                        cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                            ${playbook} \
                            --vault-password-file ${VAULT_PASS_FILE} \
                            -i ${env.VM_IP},
                    """
                }
            }
        }

    }

    post {
        success {
            echo "VM ${env.VM_NAME} provisioned at ${env.VM_IP} and configured as ${env.VM_ROLE}."
        }
        failure {
            script {
                if (env.NETBOX_IP_ID) {
                    sh """
                        curl -s -X DELETE \
                             -H "Authorization: Token ${env.NETBOX_TOKEN}" \
                             "${env.NETBOX_URL}/api/ipam/ip-addresses/${env.NETBOX_IP_ID}/"
                    """
                    echo "Released NetBox IP ${env.VM_IP} (ID: ${env.NETBOX_IP_ID}) — IP reclaimed."
                }
                if (env.VM_NAME && env.VM_IP) {
                    sh """
                        cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                            playbooks/windows/dns_remove_record.yml \
                            --vault-password-file ${VAULT_PASS_FILE} \
                            --extra-vars "dns_record_name=${env.VM_NAME} dns_record_ip=${env.VM_IP}" \
                            || true
                    """
                }
                if (env.VM_NAME) {
                    sh """
                        cd ${ANSIBLE_REPO_PATH} && ansible-playbook \
                            playbooks/infra/destroy_vm.yml \
                            -i inventory/ \
                            --vault-password-file ${VAULT_PASS_FILE} \
                            --extra-vars "vm_name=${env.VM_NAME}" \
                            || true
                    """
                    echo "vCenter VM ${env.VM_NAME} destroyed."
                }
                echo "Provisioning failed — all resources cleaned up."
            }
        }
    }
}
