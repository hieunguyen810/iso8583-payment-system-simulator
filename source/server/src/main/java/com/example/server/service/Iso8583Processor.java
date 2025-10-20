package com.example.server.service;

import com.example.common.model.Iso8583Message;
import com.example.common.model.ValidationResult;
import com.example.common.parser.Iso8583Parser;
import com.example.server.metrics.TransactionMetrics;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class Iso8583Processor {
    
    private final TransactionMetrics transactionMetrics;
    private final Tracer tracer;
    
    public Iso8583Processor(TransactionMetrics transactionMetrics, Tracer tracer) {
        this.transactionMetrics = transactionMetrics;
        this.tracer = tracer;
    }
    
    public Iso8583Message processMessage(Iso8583Message request) {
        String stan = request.getField(11) != null ? request.getField(11) : "unknown";
        
        Span span = tracer.spanBuilder("iso8583.server.process_message")
                .setAttribute("message.mti", request.getMti())
                .setAttribute("message.stan", stan)
                .setAttribute("iso8583.stan", stan)
                .setAttribute("iso8583.correlation_id", stan)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            System.out.println("🔄 Iso8583Processor.processMessage called with MTI: " + request.getMti());
            
            // Validate incoming message
            ValidationResult validation = Iso8583Parser.validateMessage(request);
            if (!validation.isValid()) {
                span.setStatus(StatusCode.ERROR, "Invalid message format");
                System.err.println("❌ Invalid message: " + String.join(", ", validation.getErrors()));
                Iso8583Message errorResponse = new Iso8583Message();
                errorResponse.setMti("0210");
                errorResponse.addField(39, "30"); // Format error
                return errorResponse;
            }
        
        Iso8583Message response = new Iso8583Message();
        String requestMti = request.getMti();
        System.out.println("📊 TransactionMetrics instance: " + transactionMetrics);

        if ("0200".equals(requestMti)) {
            response.setMti("0210");
            copyField(request, response, 2);
            copyField(request, response, 3);
            copyField(request, response, 4);
            copyField(request, response, 7);
            copyField(request, response, 11);
            copyField(request, response, 37);
            response.addField(38, generateApprovalCode());
            response.addField(39, "00");
            System.out.println("💳 Processed authorization request - APPROVED");
        } else if ("0800".equals(requestMti)) {
            response.setMti("0810");
            copyField(request, response, 7);
            copyField(request, response, 11);
            copyField(request, response, 70);
            response.addField(7, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
            System.out.println("💓 Processed echo request - Connection alive");
        } else if ("0210".equals(requestMti)) {
            System.out.println("Authorize Successfully");
            // Don't return null, return the original message so timer can check it
            return request;
        }
        else {
            response.setMti("0210");
            response.addField(39, "30");
            transactionMetrics.incrementFailed();
            System.err.println("⚠️ Unknown message type: " + requestMti);
        }
            span.setAttribute("response.mti", response.getMti())
                .setAttribute("response.code", response.getField(39) != null ? response.getField(39) : "unknown")
                .setStatus(StatusCode.OK);
            
            return response;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
    private static void copyField(Iso8583Message source, Iso8583Message target, int fieldNumber) {
        String value = source.getField(fieldNumber);
        if (value != null) {
            target.addField(fieldNumber, value);
        }
    }
    private static String generateApprovalCode() {
            return String.format("%06d", (int) (Math.random() * 999999));
        }
}