package com.example.authorize.service;

import com.example.common.model.Iso8583Message;
import com.example.common.parser.Iso8583Parser;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
@Profile("!activemq")
public class AuthorizationService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Random random = new Random();

    public AuthorizationService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "iso8583-requests", groupId = "authorize-service")
    public void processAuthorizationRequest(String message) {
        try {
            System.out.println("📥 Received authorization request: " + message);
            
            Iso8583Message request = Iso8583Parser.parseMessage(message);
            String mti = request.getMti();
            
            if ("0200".equals(mti)) {
                Iso8583Message response = createAuthorizationResponse(request);
                String responseMessage = response.toString();
                
                System.out.println("📤 Sending authorization response: " + responseMessage);
                kafkaTemplate.send("iso8583-responses", responseMessage);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error processing authorization: " + e.getMessage());
        }
    }

    private Iso8583Message createAuthorizationResponse(Iso8583Message request) {
        Iso8583Message response = new Iso8583Message();
        response.setMti("0210");
        
        // Copy key fields from request
        String field2 = request.getField(2);
        if (field2 != null) response.addField(2, field2);
        
        String field3 = request.getField(3);
        if (field3 != null) response.addField(3, field3);
        
        String field4 = request.getField(4);
        if (field4 != null) response.addField(4, field4);
        
        String field11 = request.getField(11);
        if (field11 != null) response.addField(11, field11);
        
        String field37 = request.getField(37);
        if (field37 != null) response.addField(37, field37);
        
        // Add response fields
        response.addField(7, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        response.addField(38, generateApprovalCode());
        response.addField(39, "00"); // Approved
        
        return response;
    }

    private String generateApprovalCode() {
        return String.format("%06d", random.nextInt(999999));
    }
}