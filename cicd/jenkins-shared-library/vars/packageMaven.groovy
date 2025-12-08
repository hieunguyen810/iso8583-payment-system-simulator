def call(Map config = [:]) {
    def workDir = config.workDir ?: '.'
    def skipTests = config.skipTests ?: true
    def securityScan = config.securityScan ?: true
    
    dir(workDir) {
        try {
            sh """
                mvn package ${skipTests ? '-DskipTests' : ''} \
                    -Dmaven.javadoc.skip=true \
                    --batch-mode --no-transfer-progress
            """
            
            if (securityScan) {
                sh 'mvn org.owasp:dependency-check-maven:check || true'
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target',
                    reportFiles: 'dependency-check-report.html',
                    reportName: 'Security Report'
                ])
            }
            
            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: false
            
        } catch (Exception e) {
            error "Maven packaging failed: ${e.getMessage()}"
        }
    }
}