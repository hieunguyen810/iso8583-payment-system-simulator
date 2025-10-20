package com.example.client.controller;

import com.example.client.model.ApiResponse;
import com.example.client.model.ConnectionInfo;
import com.example.client.service.ConnectionService;
import com.example.common.model.Iso8583Message;
import com.example.common.parser.Iso8583Parser;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iso8583")
@CrossOrigin(origins = "*")
public class Iso8583Controller {

    @Autowired
    private ConnectionService connectionService;
    
    @Autowired
    private Tracer tracer;

    @GetMapping("/connections")
    public List<ConnectionInfo> getConnections() {
        return connectionService.getAllConnections();
    }

    @PostMapping("/connections")
    public ApiResponse addConnection(@RequestBody ConnectionInfo connectionInfo) {
        try {
            connectionService.addConnection(connectionInfo);
            return new ApiResponse(true, "Connection added successfully");
        } catch (Exception e) {
            return new ApiResponse(false, e.getMessage());
        }
    }

    @PostMapping("/connections/{connectionId}/connect")
    public ApiResponse connect(@PathVariable String connectionId) {
        try {
            connectionService.connect(connectionId);
            return new ApiResponse(true, "Connected successfully");
        } catch (Exception e) {
            return new ApiResponse(false, e.getMessage());
        }
    }

    @PostMapping("/connections/{connectionId}/disconnect")
    public ApiResponse disconnect(@PathVariable String connectionId) {
        try {
            connectionService.disconnect(connectionId);
            return new ApiResponse(true, "Disconnected successfully");
        } catch (Exception e) {
            return new ApiResponse(false, e.getMessage());
        }
    }

    @DeleteMapping("/connections/{connectionId}")
    public ApiResponse removeConnection(@PathVariable String connectionId) {
        try {
            connectionService.removeConnection(connectionId);
            return new ApiResponse(true, "Connection removed successfully");
        } catch (Exception e) {
            return new ApiResponse(false, e.getMessage());
        }
    }

    @PostMapping("/connections/{connectionId}/echo")
    public ApiResponse sendEcho(@PathVariable String connectionId) {
        try {
            String[] result = connectionService.sendEcho(connectionId);
            return new ApiResponse(true, "Echo sent successfully", result[0], result[1]);
        } catch (Exception e) {
            return new ApiResponse(false, e.getMessage());
        }
    }

    @PostMapping("/connections/{connectionId}/send")
    public ApiResponse sendMessage(@PathVariable String connectionId, @RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        String stan = "unknown";
        
        // Extract STAN for correlation
        try {
            Iso8583Message parsedMsg = Iso8583Parser.parseMessage(message);
            stan = parsedMsg.getField(11) != null ? parsedMsg.getField(11) : "unknown";
        } catch (Exception e) {
            // Continue with unknown STAN if parsing fails
        }
        
        Span span = tracer.spanBuilder("http.request.send_message")
                .setAttribute("http.method", "POST")
                .setAttribute("connection.id", connectionId)
                .setAttribute("iso8583.stan", stan)
                .setAttribute("iso8583.correlation_id", stan)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("message.content", message != null ? message.substring(0, Math.min(message.length(), 100)) : "null");
            
            String[] result = connectionService.sendMessage(connectionId, message);
            span.setStatus(StatusCode.OK);
            return new ApiResponse(true, "Message sent successfully", result[0], result[1]);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return new ApiResponse(false, e.getMessage());
        } finally {
            span.end();
        }
    }
}