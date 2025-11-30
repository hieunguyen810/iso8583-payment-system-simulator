package com.example.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "iso8583.client.authorization.enabled", havingValue = "true")
public class ResponseConsumerService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private ConnectionService connectionService;

    @KafkaListener(topics = "iso8583-responses", groupId = "client-response-consumer")
    public void consumeResponse(String message) {
        try {
            System.out.println("📥 Received response from Kafka: " + message);
            
            // Check if message is JSON or raw ISO message
            String rawMessage;
            if (message.startsWith("{")) {
                // JSON format
                JsonNode jsonMessage = objectMapper.readTree(message);
                rawMessage = jsonMessage.get("rawMessage").asText();
            } else {
                // Raw ISO message format
                rawMessage = message;
            }
            
            // Send response to all connected ISO 8583 servers
            connectionService.broadcastToConnectedServers(rawMessage);
            System.out.println("✅ Authorization response sent to connected servers: " + rawMessage);
            
        } catch (Exception e) {
            System.err.println("❌ Error processing response: " + e.getMessage());
        }
    }
}