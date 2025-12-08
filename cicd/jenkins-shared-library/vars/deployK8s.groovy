def call(Map config = [:]) {
    def namespace = config.namespace ?: 'default'
    def manifestPath = config.manifestPath
    def kubeconfig = config.kubeconfig ?: ''
    def timeout = config.timeout ?: '300s'
    def dryRun = config.dryRun ?: false
    
    validateK8sConfig(config)
    
    def kubectlCmd = kubeconfig ? "kubectl --kubeconfig=${kubeconfig}" : "kubectl"
    
    try {
        // Validate manifests
        sh "${kubectlCmd} apply --dry-run=client -f ${manifestPath}"
        
        if (dryRun) {
            echo "Dry run completed successfully"
            return
        }
        
        // Create namespace if it doesn't exist
        sh "${kubectlCmd} create namespace ${namespace} --dry-run=client -o yaml | ${kubectlCmd} apply -f - || true"
        
        // Apply manifests with record for rollback
        sh "${kubectlCmd} apply -f ${manifestPath} -n ${namespace} --record"
        
        // Wait for deployment to complete
        sh """
            ${kubectlCmd} wait --for=condition=available \
                --timeout=${timeout} \
                deployment -l app -n ${namespace} || true
        """
        
        // Verify deployment
        sh "${kubectlCmd} get pods -n ${namespace} -l app"
        
    } catch (Exception e) {
        echo "Deployment failed, checking rollback options..."
        sh "${kubectlCmd} rollout history deployment -n ${namespace} || true"
        error "Kubernetes deployment failed: ${e.getMessage()}"
    }
}

private def validateK8sConfig(Map config) {
    if (!config.manifestPath) {
        error "manifestPath is required for Kubernetes deployment"
    }
    if (!fileExists(config.manifestPath)) {
        error "Manifest file not found: ${config.manifestPath}"
    }
}