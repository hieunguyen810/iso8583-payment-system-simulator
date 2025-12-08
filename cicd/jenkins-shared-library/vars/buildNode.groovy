def call(Map config = [:]) {
    def workDir = config.workDir ?: '.'
    def command = config.command ?: 'npm run build'
    def nodeVersion = config.nodeVersion ?: '20'
    
    validateConfig(config)
    
    dir(workDir) {
        try {
            sh """
                export NODE_ENV=production
                export NODE_OPTIONS="--max-old-space-size=4096"
                npm ci --only=production --no-audit --no-fund
                npm audit --audit-level=high
                ${command}
            """
        } catch (Exception e) {
            error "Node.js build failed: ${e.getMessage()}"
        }
    }
}

private def validateConfig(Map config) {
    if (config.workDir && !fileExists("${config.workDir}/package.json")) {
        error "package.json not found in ${config.workDir}"
    }
}