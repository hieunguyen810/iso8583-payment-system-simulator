def call(Map config) {
    pipeline {
        agent any
        
        options {
            buildDiscarder(logRotator(numToKeepStr: '10'))
            timeout(time: 60, unit: 'MINUTES')
            timestamps()
            ansiColor('xterm')
        }
        
        environment {
            BUILD_VERSION = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(8)}"
            DOCKER_BUILDKIT = '1'
        }
        
        stages {
            stage('Checkout & Validate') {
                steps {
                    script {
                        validatePipelineConfig(config)
                    }
                    checkout scm
                    sh 'git log --oneline -5'
                }
            }
            
            stage('Security Scan') {
                when {
                    expression { config.security?.enabled != false }
                }
                parallel {
                    stage('Secret Scan') {
                        steps {
                            sh 'git secrets --scan || true'
                        }
                    }
                    stage('License Check') {
                        steps {
                            sh 'license-checker --summary || true'
                        }
                    }
                }
            }
            
            stage('Build') {
                steps {
                    script {
                        if (config.buildType == 'maven') {
                            buildMaven(config.build ?: [:])
                        } else if (config.buildType == 'node') {
                            buildNode(config.build ?: [:])
                        } else {
                            error "Unsupported build type: ${config.buildType}"
                        }
                    }
                }
            }
            
            stage('Test') {
                parallel {
                    stage('Unit Tests') {
                        steps {
                            script {
                                if (config.buildType == 'maven') {
                                    testMaven(config.test ?: [:])
                                } else if (config.buildType == 'node') {
                                    testNode(config.test ?: [:])
                                }
                            }
                        }
                    }
                    stage('Integration Tests') {
                        when {
                            expression { config.integrationTest?.enabled == true }
                        }
                        steps {
                            script {
                                if (config.buildType == 'maven') {
                                    testMaven(config.integrationTest)
                                }
                            }
                        }
                    }
                }
            }
            
            stage('Quality Gate') {
                when {
                    expression { config.sonar?.enabled == true }
                }
                steps {
                    script {
                        def sonarConfig = config.sonar ?: [:]
                        withSonarQubeEnv(sonarConfig.server ?: 'SonarQube') {
                            if (config.buildType == 'maven') {
                                sh 'mvn sonar:sonar'
                            }
                        }
                        timeout(time: 10, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }
            }
            
            stage('Package') {
                when {
                    expression { config.buildType == 'maven' }
                }
                steps {
                    packageMaven(config.package ?: [:])
                }
            }
            
            stage('Deploy') {
                when {
                    anyOf {
                        branch 'main'
                        branch 'develop'
                        expression { params.FORCE_DEPLOY == true }
                    }
                }
                steps {
                    script {
                        def deployConfig = config.deploy ?: [:]
                        deployConfig.tag = env.BUILD_VERSION
                        
                        if (deployConfig.type == 'docker') {
                            deployDocker(deployConfig)
                        } else if (deployConfig.type == 'k8s') {
                            deployK8s(deployConfig)
                        } else {
                            error "Unsupported deployment type: ${deployConfig.type}"
                        }
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    if (fileExists('target/surefire-reports/*.xml')) {
                        publishTestResults testResultsPattern: 'target/surefire-reports/*.xml'
                    }
                }
                cleanWs()
            }
            success {
                script {
                    if (config.notifications?.slack) {
                        slackSend(
                            channel: config.notifications.slack.channel,
                            color: 'good',
                            message: "✅ Pipeline succeeded: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
                        )
                    }
                }
            }
            failure {
                script {
                    if (config.notifications?.slack) {
                        slackSend(
                            channel: config.notifications.slack.channel,
                            color: 'danger',
                            message: "❌ Pipeline failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}\n${env.BUILD_URL}"
                        )
                    }
                }
            }
        }
    }
}

private def validatePipelineConfig(Map config) {
    if (!config.buildType) {
        error "buildType is required (maven|node)"
    }
    if (!['maven', 'node'].contains(config.buildType)) {
        error "Unsupported buildType: ${config.buildType}"
    }
}