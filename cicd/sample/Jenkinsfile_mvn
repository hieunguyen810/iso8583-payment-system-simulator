pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'b09f464d-5eba-4367-8f6b-67244c638b03',
                    url: 'https://github.com/hieunguyen810/iso8583-demo.git'
            }
        }

        stage('Build') {
            steps {
                dir('source'){
                sh 'mvn clean install -DskipTests=false'
                }
            }
        }
        
        stage('Unit Tests') {
            steps {
                dir('source'){
                // Run only unit tests (exclude integration tests)
                sh 'mvn test -Dtest=Iso8583MessageTest'
                }
            }
            }

        stage('Integration Tests') {
            steps {
                dir('source'){
                // Run integration tests (usually in src/integration-test/java or marked with @IT)
                sh 'mvn verify -Dtest=DemoApplicationTests'
                }
            }
        }

        stage('Package') {
            steps {
                dir('source'){
                sh 'mvn package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully 🚀'
        }
        failure {
            echo 'Pipeline failed 💥'
        }
    }
}