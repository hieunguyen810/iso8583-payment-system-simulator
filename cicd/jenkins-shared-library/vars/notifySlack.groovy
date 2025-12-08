def call(Map config) {
    def channel = config.channel ?: '#ci-cd'
    def color = config.color ?: 'good'
    def message = config.message
    def attachments = config.attachments ?: []
    
    if (!message) {
        error "Message is required for Slack notification"
    }
    
    try {
        slackSend(
            channel: channel,
            color: color,
            message: message,
            attachments: attachments
        )
    } catch (Exception e) {
        echo "Failed to send Slack notification: ${e.getMessage()}"
    }
}