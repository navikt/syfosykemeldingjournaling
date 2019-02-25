#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfosmsak'
        DISABLE_SLACK_MESSAGES = true
        ZONE = 'fss'
        DOCKER_SLUG='syfo'
        FASIT_ENVIRONMENT='q1'
    }

    stages {
        stage('initialize') {
            steps {
                init action: 'default'
                script {
                    sh(script: './gradlew clean')
                    def applicationVersionGradle = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
                    env.APPLICATION_VERSION = "${applicationVersionGradle}-${env.COMMIT_HASH_SHORT}"
                    if (applicationVersionGradle.endsWith('-SNAPSHOT')) {
                        env.APPLICATION_VERSION = "${applicationVersionGradle}.${env.BUILD_ID}-${env.COMMIT_HASH_SHORT}"
                    } else {
                        env.DEPLOY_TO = 'production'
                    }
                    init action: 'updateStatus', applicationName: env.APPLICATION_NAME, applicationVersion: env.APPLICATION_VERSION
                }
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
                slackStatus status: 'passed'
            }
        }
        stage('Create kafka topics') {
            steps {
                sh 'echo TODO'
                // TODO
            }
        }
        stage('push docker image') {
            steps {
                dockerUtils action: 'createPushImage'
            }
        }
        stage('deploy to preprod') {
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'preprod-fss', placeholderFile: "preprod.env"
            }
        }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deployApp action: 'kubectlDeploy', cluster: 'prod-fss', placeholderFile: "prod.env"
                githubStatus action: 'tagRelease'
            }
        }
    }
    post {
        always {
            postProcess action: 'always'
        }
        success {
            postProcess action: 'success'
        }
        failure {
            postProcess action: 'failure'
        }
    }
}
