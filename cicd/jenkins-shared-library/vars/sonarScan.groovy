def call(Map config = [:]) {
    def projectKey = config.projectKey ?: env.JOB_NAME.replaceAll('/', '-')
    def server = config.server ?: 'SonarQube'
    def qualityGate = config.qualityGate ?: true
    
    withSonarQubeEnv(server) {
        if (config.buildType == 'maven') {
            sh """
                mvn sonar:sonar \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectVersion=${env.BUILD_VERSION} \
                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
            """
        } else if (config.buildType == 'node') {
            sh """
                sonar-scanner \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectVersion=${env.BUILD_VERSION} \
                    -Dsonar.sources=src \
                    -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info
            """
        }
    }
    
    if (qualityGate) {
        timeout(time: 10, unit: 'MINUTES') {
            def qg = waitForQualityGate()
            if (qg.status != 'OK') {
                error "Quality gate failed: ${qg.status}"
            }
        }
    }
}