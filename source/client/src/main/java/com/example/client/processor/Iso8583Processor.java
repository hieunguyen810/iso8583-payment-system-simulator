package com.example.client.processor;

import org.springframework.context.annotation.Profile;

import com.example.common.model.Iso8583Message;
import com.example.common.parser.Iso8583Parser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "iso8583.client.authorization.enabled", havingValue = "true")
@Profile("!activemq")
public class Iso8583Processor {

    private static KafkaTemplate<String, String> kafkaTemplate;
    private static ObjectMapper objectMapper;
    private static String kafkaTopicRequest;
    private static String kafkaTopicResponse;

    public Iso8583Processor(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${kafka.topic.iso8583.request:iso8583-requests}") String topicRequest,
            @Value("${kafka.topic.iso8583.response:iso8583-responses}") String topicResponse) {
        Iso8583Processor.kafkaTemplate = kafkaTemplate;
        Iso8583Processor.kafkaTopicRequest = topicRequest;
        Iso8583Processor.kafkaTopicResponse = topicResponse;
        
        // Initialize ObjectMapper with configurations
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Process incoming ISO 8583 message, parse it, and send to Kafka
     */
    public static void processIncomingMessage(String isoMessage, boolean isConnected) {
        try {
            System.out.println("\n📥 Received ISO 8583 Message: " + isoMessage);
            
            // Parse the ISO 8583 message
            Iso8583Message parsedMessage = Iso8583Parser.parseMessage(isoMessage);
            
            if (parsedMessage == null) {
                System.err.println("❌ Failed to parse ISO 8583 message");
                return;
            }

            // Display parsed message details
            displayMessageDetails(parsedMessage);
            
            // Create enriched message object for Kafka
            Map<String, Object> kafkaMessage = enrichMessage(parsedMessage, isConnected);
            
            // Determine topic based on MTI
            String topic = determineKafkaTopic(parsedMessage.getMti());
            
            // Send to Kafka
            sendToKafka(topic, kafkaMessage, parsedMessage);
            
        } catch (Exception e) {
            System.err.println("❌ Error processing incoming message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Display parsed message details in console
     */
    private static void displayMessageDetails(Iso8583Message message) {
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("📋 Message Type: " + message.getMti() + " (" + getMessageTypeDescription(message.getMti()) + ")");
        System.out.println("───────────────────────────────────────");
        
        Map<Integer, String> fields = message.getFields();
        if (fields != null && !fields.isEmpty()) {
            fields.forEach((fieldNum, value) -> {
                String fieldName = getFieldName(fieldNum);
                System.out.printf("   Field %3d: %-30s = %s%n", fieldNum, fieldName, value);
            });
        }
        
        System.out.println("═══════════════════════════════════════\n");
    }

    /**
     * Enrich message with metadata for Kafka
     */
    private static Map<String, Object> enrichMessage(Iso8583Message message, boolean isConnected) {
        Map<String, Object> enrichedMessage = new HashMap<>();
        
        // Message metadata
        enrichedMessage.put("messageType", message.getMti());
        enrichedMessage.put("messageTypeDescription", getMessageTypeDescription(message.getMti()));
        enrichedMessage.put("receivedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        enrichedMessage.put("connectionStatus", isConnected ? "CONNECTED" : "DISCONNECTED");
        
        // Original message
        enrichedMessage.put("rawMessage", message.toString());
        
        // Parsed fields with descriptive names
        Map<String, Object> parsedFields = new HashMap<>();
        Map<Integer, String> fields = message.getFields();
        
        if (fields != null) {
            fields.forEach((fieldNum, value) -> {
                Map<String, Object> fieldData = new HashMap<>();
                fieldData.put("value", value);
                fieldData.put("description", getFieldName(fieldNum));
                parsedFields.put(String.valueOf(fieldNum), fieldData);
            });
        }
        
        enrichedMessage.put("fields", parsedFields);
        
        // Extract key business fields for easy access
        Map<String, String> businessData = extractBusinessData(message);
        enrichedMessage.put("businessData", businessData);
        
        return enrichedMessage;
    }

    /**
     * Extract key business fields from message
     */
    private static Map<String, String> extractBusinessData(Iso8583Message message) {
        Map<String, String> businessData = new HashMap<>();
        Map<Integer, String> fields = message.getFields();
        
        if (fields == null) return businessData;
        
        // Common business fields
        if (fields.containsKey(2)) businessData.put("pan", maskPan(fields.get(2)));
        if (fields.containsKey(3)) businessData.put("processingCode", fields.get(3));
        if (fields.containsKey(4)) businessData.put("transactionAmount", fields.get(4));
        if (fields.containsKey(11)) businessData.put("stan", fields.get(11));
        if (fields.containsKey(37)) businessData.put("retrievalReferenceNumber", fields.get(37));
        if (fields.containsKey(38)) businessData.put("approvalCode", fields.get(38));
        if (fields.containsKey(39)) businessData.put("responseCode", fields.get(39));
        if (fields.containsKey(41)) businessData.put("terminalId", fields.get(41));
        if (fields.containsKey(42)) businessData.put("merchantId", fields.get(42));
        if (fields.containsKey(49)) businessData.put("currencyCode", fields.get(49));
        
        return businessData;
    }

    /**
     * Mask PAN for security (show only first 6 and last 4 digits)
     */
    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        int length = pan.length();
        return pan.substring(0, 6) + "*".repeat(length - 10) + pan.substring(length - 4);
    }

    /**
     * Determine Kafka topic based on MTI
     */
    private static String determineKafkaTopic(String mti) {
        if (mti == null || mti.length() < 2) {
            return kafkaTopicRequest;
        }
        
        // Check if it's a response message (second digit is 1 or 3)
        char secondDigit = mti.charAt(1);
        if (secondDigit == '1' || secondDigit == '3') {
            return kafkaTopicResponse;
        }
        
        return kafkaTopicRequest;
    }

    /**
     * Send message to Kafka
     */
    private static void sendToKafka(String topic, Map<String, Object> message, Iso8583Message originalMessage) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            // Use STAN as Kafka message key for partitioning
            String messageKey = extractMessageKey(originalMessage);
            
            System.out.println("📤 Sending to Kafka topic: " + topic);
            System.out.println("   Message Key: " + messageKey);
            
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(topic, messageKey, jsonMessage);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    System.out.println("✅ Message sent to Kafka successfully!");
                    System.out.println("   Topic: " + result.getRecordMetadata().topic());
                    System.out.println("   Partition: " + result.getRecordMetadata().partition());
                    System.out.println("   Offset: " + result.getRecordMetadata().offset());
                } else {
                    System.err.println("❌ Failed to send message to Kafka: " + ex.getMessage());
                }
            });
            
        } catch (Exception e) {
            System.err.println("❌ Error serializing/sending message to Kafka: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extract message key for Kafka partitioning (use STAN if available)
     */
    private static String extractMessageKey(Iso8583Message message) {
        Map<Integer, String> fields = message.getFields();
        if (fields != null && fields.containsKey(11)) {
            return fields.get(11); // STAN
        }
        return message.getMti() + "-" + System.currentTimeMillis();
    }

    /**
     * Get human-readable message type description
     */
    private static String getMessageTypeDescription(String mti) {
        if (mti == null || mti.length() != 4) return "Unknown";
        
        switch (mti) {
            case "0100": return "Authorization Request";
            case "0110": return "Authorization Response";
            case "0200": return "Financial Transaction Request";
            case "0210": return "Financial Transaction Response";
            case "0400": return "Reversal Request";
            case "0410": return "Reversal Response";
            case "0800": return "Network Management Request (Echo)";
            case "0810": return "Network Management Response (Echo)";
            case "0420": return "Reversal Advice Response";
            case "0430": return "Reversal Advice Repeat";
            default: return "Unknown Message Type";
        }
    }

    /**
     * Get field name/description
     */
    private static String getFieldName(int fieldNumber) {
        switch (fieldNumber) {
            case 2: return "Primary Account Number (PAN)";
            case 3: return "Processing Code";
            case 4: return "Transaction Amount";
            case 7: return "Transmission Date & Time";
            case 11: return "System Trace Audit Number (STAN)";
            case 12: return "Local Transaction Time";
            case 13: return "Local Transaction Date";
            case 18: return "Merchant Category Code";
            case 22: return "POS Entry Mode";
            case 25: return "POS Condition Code";
            case 37: return "Retrieval Reference Number";
            case 38: return "Approval Code";
            case 39: return "Response Code";
            case 41: return "Terminal ID";
            case 42: return "Merchant ID";
            case 49: return "Currency Code";
            case 70: return "Network Management Info Code";
            default: return "Field " + fieldNumber;
        }
    }
}