def call(Map config = [:]) {
    def skipTests = config.skipTests ?: false
    def workDir = config.workDir ?: '.'
    def profiles = config.profiles ? "-P${config.profiles.join(',')}" : ''
    def jvmArgs = config.jvmArgs ?: '-Xmx2g -XX:+UseG1GC'
    
    validateConfig(config)
    
    dir(workDir) {
        try {
            sh """
                export MAVEN_OPTS="${jvmArgs}"
                mvn clean compile ${profiles} ${skipTests ? '-DskipTests' : ''} \
                    -Dmaven.repo.local=.m2/repository \
                    --batch-mode --no-transfer-progress
            """
        } catch (Exception e) {
            error "Maven build failed: ${e.getMessage()}"
        }
    }
}

private def validateConfig(Map config) {
    if (config.workDir && !fileExists("${config.workDir}/pom.xml")) {
        error "pom.xml not found in ${config.workDir}"
    }
}