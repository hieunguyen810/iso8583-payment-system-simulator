def call(Map config = [:]) {
    def imageName = config.imageName
    def tag = config.tag ?: env.BUILD_NUMBER ?: 'latest'
    def workDir = config.workDir ?: '.'
    def registry = config.registry ?: ''
    def securityScan = config.securityScan ?: true
    def buildArgs = config.buildArgs ?: [:]
    
    validateDockerConfig(config)
    
    dir(workDir) {
        def fullImageName = registry ? "${registry}/${imageName}:${tag}" : "${imageName}:${tag}"
        def buildArgsStr = buildArgs.collect { k, v -> "--build-arg ${k}=${v}" }.join(' ')
        
        try {
            sh """
                docker build \
                    --no-cache \
                    --pull \
                    ${buildArgsStr} \
                    -t ${fullImageName} \
                    --label "build.number=${env.BUILD_NUMBER}" \
                    --label "build.url=${env.BUILD_URL}" \
                    --label "git.commit=${env.GIT_COMMIT}" .
            """
            
            if (securityScan) {
                sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image ${fullImageName} || true"
            }
            
            if (config.push) {
                sh "docker push ${fullImageName}"
                echo "Image pushed: ${fullImageName}"
            }
            
        } catch (Exception e) {
            error "Docker deployment failed: ${e.getMessage()}"
        } finally {
            sh "docker image prune -f || true"
        }
    }
}

private def validateDockerConfig(Map config) {
    if (!config.imageName) {
        error "imageName is required for Docker deployment"
    }
    if (config.workDir && !fileExists("${config.workDir}/Dockerfile")) {
        error "Dockerfile not found in ${config.workDir}"
    }
}