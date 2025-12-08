def call(Map config = [:]) {
    def workDir = config.workDir ?: '.'
    def testPattern = config.testPattern ?: ''
    def coverage = config.coverage ?: true
    def parallel = config.parallel ?: true
    
    dir(workDir) {
        try {
            def coverageArgs = coverage ? 'jacoco:prepare-agent' : ''
            def parallelArgs = parallel ? '-Dparallel=methods -DthreadCount=4' : ''
            
            sh """
                mvn ${coverageArgs} test ${parallelArgs} \
                    ${testPattern ? '-Dtest=' + testPattern : ''} \
                    -Dmaven.test.failure.ignore=false \
                    --batch-mode --no-transfer-progress
            """
            
            if (coverage) {
                sh 'mvn jacoco:report'
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/site/jacoco',
                    reportFiles: 'index.html',
                    reportName: 'Coverage Report'
                ])
            }
        } catch (Exception e) {
            error "Maven tests failed: ${e.getMessage()}"
        } finally {
            publishTestResults testResultsPattern: 'target/surefire-reports/*.xml'
        }
    }
}