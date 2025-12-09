def call(Map config = [:]) {
    def skipTests = config.skipTests ?: false
    def workDir = config.workDir ?: '.'
    def profiles = config.profiles ? "-P${config.profiles.join(',')}" : ''
    def jvmArgs = config.jvmArgs ?: '-Xmx2g -XX:+UseG1GC'
    def clean = config.clean ?: false
    def modules = config.modules ? "-pl ${config.modules.join(',')}" : ''
    def alsoMake = config.alsoMake ? '-am' : ''
    
    validateConfig(config)
    
    dir(workDir) {
        try {
            sh """
                export MAVEN_OPTS="${jvmArgs}"
                mvn ${clean ? 'clean ' : ''}compile ${profiles} ${skipTests ? '-DskipTests' : ''} \
                    ${modules} ${alsoMake} \
                    -T 1C \
                    -Dmaven.compile.fork=true \
                    -Dmaven.repo.local=/var/lib/jenkins/.m2/repository \
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