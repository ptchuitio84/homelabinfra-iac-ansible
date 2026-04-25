// =============================================================================
// jenkins/infra/nnt-jkn-terraform-apply.groovy
// =============================================================================
// JOB NAME: nnt-jkn-terraform-apply
//
// PURPOSE:
// Runs OpenTofu or Terraform against the homelabinfra-iac-terraform repo.
// Executes exclusively on hmvlaptfm001 (Jenkins agent label: tfm001) —
// isolated from the Ansible control node ans001.
//
// PARAMETERS:
//   TF_BINARY  — which binary to run: tofu (OpenTofu) or terraform (HashiCorp)
//   TF_ACTION  — plan, apply, or destroy
//
// CREDENTIALS REQUIRED IN JENKINS:
//   vault-root-token — Secret text — HashiCorp Vault root token
//                      Used to authenticate the Vault provider at runtime
//
// JENKINS SETUP:
//   1. New Item → Pipeline → name: nnt-jkn-terraform-apply
//   2. Pipeline → Definition: Pipeline script from SCM
//   3. SCM: Git → URL: https://github.com/ptchuitio84/homelabinfra-iac-ansible.git
//   4. Credentials: github-pat
//   5. Branch: */main
//   6. Script Path: jenkins/infra/nnt-jkn-terraform-apply.groovy
//   7. Add credentials: vault-root-token (Secret text)
//   8. Register hmvlaptfm001 as Jenkins agent with label: tfm001
// =============================================================================

pipeline {

    agent { label 'tfm001' }

    parameters {
        choice(
            name: 'TF_BINARY',
            choices: ['tofu', 'terraform'],
            description: 'Which binary to run — tofu (OpenTofu) or terraform (HashiCorp)'
        )
        choice(
            name: 'TF_ACTION',
            choices: ['plan', 'apply', 'destroy'],
            description: 'Terraform action to execute'
        )
    }

    environment {
        TF_REPO_PATH  = '/opt/homelabinfra-iac-terraform'
        VAULT_ADDR    = 'http://10.10.0.44:8200'
        VAULT_TOKEN   = credentials('vault-root-token')
        TF_IN_AUTOMATION = 'true'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Sync Terraform repo') {
            steps {
                sh "cd ${TF_REPO_PATH} && git pull"
            }
        }

        stage('Init') {
            steps {
                sh """
                    cd ${TF_REPO_PATH}
                    ${params.TF_BINARY} init -reconfigure
                """
            }
        }

        stage('Validate') {
            steps {
                sh """
                    cd ${TF_REPO_PATH}
                    ${params.TF_BINARY} validate
                """
            }
        }

        stage('Plan') {
            steps {
                sh """
                    cd ${TF_REPO_PATH}
                    ${params.TF_BINARY} plan -out=tfplan
                """
            }
        }

        stage('Apply') {
            when { expression { params.TF_ACTION == 'apply' } }
            steps {
                sh """
                    cd ${TF_REPO_PATH}
                    ${params.TF_BINARY} apply tfplan
                """
            }
        }

        stage('Destroy') {
            when { expression { params.TF_ACTION == 'destroy' } }
            steps {
                sh """
                    cd ${TF_REPO_PATH}
                    ${params.TF_BINARY} destroy -auto-approve
                """
            }
        }

    }

    post {
        success {
            echo "${params.TF_BINARY} ${params.TF_ACTION} completed successfully."
        }
        failure {
            echo "${params.TF_BINARY} ${params.TF_ACTION} failed — review plan output above."
        }
    }
}
