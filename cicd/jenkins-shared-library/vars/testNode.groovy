def call(Map config = [:]) {
    def workDir = config.workDir ?: '.'
    def command = config.command ?: 'npm test'
    def coverage = config.coverage ?: true
    
    dir(workDir) {
        try {
            sh """
                export NODE_ENV=test
                npm run lint || true
                ${coverage ? 'npm run test:coverage' : command}
            """
            
            if (coverage && fileExists('coverage/lcov.info')) {
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'coverage/lcov-report',
                    reportFiles: 'index.html',
                    reportName: 'Coverage Report'
                ])
            }
        } catch (Exception e) {
            error "Node.js tests failed: ${e.getMessage()}"
        } finally {
            if (fileExists('test-results.xml')) {
                publishTestResults testResultsPattern: 'test-results.xml'
            }
        }
    }
}