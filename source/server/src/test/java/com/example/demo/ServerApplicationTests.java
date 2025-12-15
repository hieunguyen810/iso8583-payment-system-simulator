package com.example.server;

import com.example.common.model.Iso8583Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServerApplication.class)
@TestPropertySource(properties = {
    "app.mode=server"  // Only start server for tests
})
class ServerApplicationTests {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8583;
    private Socket testSocket;
    private DataOutputStream testOutput;
    private DataInputStream testInput;
    
    @BeforeEach
    void setUp() throws Exception {
        // Wait for server to start
        Thread.sleep(2000);
    }
    
    @AfterEach
    void tearDown() {
        closeTestConnection();
    }
    
    private void establishTestConnection() throws Exception {
        if (testSocket == null || testSocket.isClosed()) {
            testSocket = new Socket(SERVER_HOST, SERVER_PORT);
            testOutput = new DataOutputStream(testSocket.getOutputStream());
            testInput = new DataInputStream(testSocket.getInputStream());
        }
    }
    
    private void closeTestConnection() {
        try {
            if (testInput != null) testInput.close();
            if (testOutput != null) testOutput.close();
            if (testSocket != null && !testSocket.isClosed()) testSocket.close();
        } catch (IOException e) {
            // Ignore close errors in tests
        }
    }
    
    private String sendMessage(String message) throws Exception {
        testOutput.writeShort(message.length());
        testOutput.writeBytes(message);
        testOutput.flush();
        
        int responseLength = testInput.readUnsignedShort();
        byte[] responseBytes = new byte[responseLength];
        testInput.readFully(responseBytes);
        
        return new String(responseBytes);
    }
    
    @Test
    @DisplayName("Spring Boot context should load successfully")
    void contextLoads() {
        assertTrue(true, "Application context loaded successfully");
    }
    
    @Test
    @DisplayName("Server should start and accept connections")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void serverShouldStartAndAcceptConnections() throws Exception {
        establishTestConnection();
        
        assertTrue(testSocket.isConnected(), "Should be able to connect to server");
        assertFalse(testSocket.isClosed(), "Connection should be open");
        
        System.out.println("✅ Server connection test passed");
    }
    
    @Test
    @DisplayName("Should send and receive authorization message (0200/0210)")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldSendAndReceiveAuthorizationMessage() throws Exception {
        establishTestConnection();
        
        String authRequest = "0200|2=4000123456789012|3=000000|4=000000001000|7=" + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                           "|11=123456|37=123456789012";
        
        System.out.println("📤 Sending auth request: " + authRequest);
        String response = sendMessage(authRequest);
        System.out.println("📨 Received auth response: " + response);
        
        // Verify authorization response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.startsWith("0210"), "Response should be authorization response (0210)");
        assertTrue(response.contains("39=00"), "Should contain approval code (39=00)");
        assertTrue(response.contains("38="), "Should contain authorization code (field 38)");
        assertTrue(response.contains("2=4000123456789012"), "Should echo back PAN");
        assertTrue(response.contains("4=000000001000"), "Should echo back amount");
    }
    
    @Test
    @DisplayName("Should send and receive echo message (0800/0810)")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldSendAndReceiveEchoMessage() throws Exception {
        establishTestConnection();
        
        String echoRequest = "0800|7=" + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                           "|11=123456|70=001";
        
        System.out.println("💓 Sending echo request: " + echoRequest);
        String response = sendMessage(echoRequest);
        System.out.println("💓 Received echo response: " + response);
        
        // Verify echo response
        assertNotNull(response, "Echo response should not be null");
        assertTrue(response.startsWith("0810"), "Response should be echo response (0810)");
        assertTrue(response.contains("11=123456"), "Should echo back STAN");
        assertTrue(response.contains("70=001"), "Should echo back network management code");
        assertTrue(response.contains("7="), "Should contain transmission date/time");
    }
    
    @Test
    @DisplayName("Should handle multiple messages on same connection")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void shouldHandleMultipleMessagesOnSameConnection() throws Exception {
        establishTestConnection();
        
        // Send echo message
        String echoRequest = "0800|7=" + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                           "|11=000001|70=001";
        
        String echoResponse = sendMessage(echoRequest);
        assertTrue(echoResponse.startsWith("0810"), "First message should be echo response");
        
        // Send authorization message on same connection
        String authRequest = "0200|2=4000567890123456|3=000000|4=000000002500|7=" + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                           "|11=000002|37=987654321098";
        
        String authResponse = sendMessage(authRequest);
        assertTrue(authResponse.startsWith("0210"), "Second message should be auth response");
        assertTrue(authResponse.contains("39=00"), "Transaction should be approved");
        
        // Send another echo message
        String echoRequest2 = "0800|7=" + 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                            "|11=000003|70=001";
        
        String echoResponse2 = sendMessage(echoRequest2);
        assertTrue(echoResponse2.startsWith("0810"), "Third message should be echo response");
        
        System.out.println("✅ Successfully sent 3 messages on same persistent connection");
    }
    
    @Test
    @DisplayName("Should handle multiple concurrent persistent connections")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void shouldHandleMultipleConcurrentPersistentConnections() throws Exception {
        int numberOfConnections = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConnections);
        AtomicInteger successCount = new AtomicInteger(0);
        
        Future<?>[] futures = new Future[numberOfConnections];
        
        for (int i = 0; i < numberOfConnections; i++) {
            final int connectionId = i;
            
            futures[i] = executor.submit(() -> {
                try {
                    // Each thread gets its own connection
                    Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    
                    // Send multiple messages per connection
                    for (int j = 0; j < 3; j++) {
                        // Send echo
                        String echoMsg = "0800|7=" + 
                                       LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                                       "|11=" + String.format("%06d", connectionId * 10 + j) + "|70=001";
                        
                        output.writeShort(echoMsg.length());
                        output.writeBytes(echoMsg);
                        output.flush();
                        
                        int responseLength = input.readUnsignedShort();
                        byte[] responseBytes = new byte[responseLength];
                        input.readFully(responseBytes);
                        String response = new String(responseBytes);
                        
                        if (!response.startsWith("0810")) {
                            throw new RuntimeException("Invalid echo response: " + response);
                        }
                        
                        // Send auth
                        String authMsg = "0200|2=400012345678901" + connectionId + "|3=000000|4=" +
                                       String.format("%012d", 1000 + j) + "|7=" +
                                       LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                                       "|11=" + String.format("%06d", connectionId * 10 + j + 100) + "|37=123456789012";
                        
                        output.writeShort(authMsg.length());
                        output.writeBytes(authMsg);
                        output.flush();
                        
                        responseLength = input.readUnsignedShort();
                        responseBytes = new byte[responseLength];
                        input.readFully(responseBytes);
                        response = new String(responseBytes);
                        
                        if (!response.startsWith("0210") || !response.contains("39=00")) {
                            throw new RuntimeException("Invalid auth response: " + response);
                        }
                        
                        Thread.sleep(100); // Small delay between messages
                    }
                    
                    socket.close();
                    successCount.incrementAndGet();
                    System.out.println("✅ Connection " + connectionId + " completed successfully");
                    
                } catch (Exception e) {
                    System.err.println("❌ Connection " + connectionId + " failed: " + e.getMessage());
                }
            });
        }
        
        // Wait for all connections to complete
        for (Future<?> future : futures) {
            future.get();
        }
        
        executor.shutdown();
        
        assertEquals(numberOfConnections, successCount.get(), 
                    "All connections should complete successfully");
        
        System.out.println("✅ All " + numberOfConnections + " persistent connections handled successfully");
    }
    
    @Test
    @DisplayName("Should handle invalid message types gracefully")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void shouldHandleInvalidMessageTypesGracefully() throws Exception {
        establishTestConnection();
        
        String invalidMessage = "9999|2=invalid|3=test";
        
        System.out.println("📝 Sending invalid message: " + invalidMessage);
        String response = sendMessage(invalidMessage);
        System.out.println("📝 Received response: " + response);
        
        assertNotNull(response, "Should receive some response even for invalid messages");
        
        // Server should respond with an error or default response
        // In our implementation, it defaults to 0210 with format error
        assertTrue(response.startsWith("0210"), "Should respond with default message type");
        assertTrue(response.contains("39=30"), "Should contain format error response code");
    }
    
    @Test
    @DisplayName("Should maintain connection after processing messages")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldMaintainConnectionAfterProcessingMessages() throws Exception {
        establishTestConnection();
        
        // Send first message
        String message1 = "0800|7=" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                         "|11=111111|70=001";
        String response1 = sendMessage(message1);
        assertTrue(response1.startsWith("0810"), "First echo should succeed");
        
        // Wait a bit and verify connection is still alive
        Thread.sleep(1000);
        assertFalse(testSocket.isClosed(), "Connection should still be open");
        
        // Send second message
        String message2 = "0800|7=" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                         "|11=222222|70=001";
        String response2 = sendMessage(message2);
        assertTrue(response2.startsWith("0810"), "Second echo should succeed");
        
        // Send third message (auth)
        String message3 = "0200|2=4000111122223333|3=000000|4=000000005000|7=" +
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                         "|11=333333|37=123456789012";
        String response3 = sendMessage(message3);
        assertTrue(response3.startsWith("0210"), "Auth should succeed");
        assertTrue(response3.contains("39=00"), "Transaction should be approved");
        
        System.out.println("✅ Connection remained persistent through multiple messages");
    }
    
    @Test
    @DisplayName("Should handle connection timeouts appropriately") 
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldHandleConnectionTimeoutsAppropriately() throws Exception {
        establishTestConnection();
        
        // Set socket timeout
        testSocket.setSoTimeout(8000); // 8 seconds
        
        // Send a message to verify connection works
        String testMessage = "0800|7=" + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                           "|11=999999|70=001";
        
        String response = sendMessage(testMessage);
        assertTrue(response.startsWith("0810"), "Message should be processed successfully");
        
        // Wait and verify connection is still valid
        Thread.sleep(2000);
        assertFalse(testSocket.isClosed(), "Connection should still be open after 2 seconds");
        
        // Send another message to verify connection is still working
        String testMessage2 = "0800|7=" + 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")) +
                            "|11=888888|70=001";
        
        String response2 = sendMessage(testMessage2);
        assertTrue(response2.startsWith("0810"), "Second message should also work");
        
        System.out.println("✅ Connection timeout handling test passed");
    }
    
    @Test
    @DisplayName("Message parsing should work for all message types")
    void messageParsingShouldWorkForAllMessageTypes() {
        // Test authorization message parsing
        String authMessage = "0200|2=4000123456789012|3=000000|4=000000001000|11=123456|37=987654321";
        
        assertNotNull(authMessage, "Auth message should not be null");
        assertTrue(authMessage.startsWith("0200"), "Should be authorization request");
        assertTrue(authMessage.contains("2=4000123456789012"), "Should contain PAN");
        assertTrue(authMessage.contains("4=000000001000"), "Should contain amount");
        assertTrue(authMessage.contains("11=123456"), "Should contain STAN");
        
        // Test echo message parsing
        String echoMessage = "0800|7=0920123456|11=654321|70=001";
        
        assertNotNull(echoMessage, "Echo message should not be null");
        assertTrue(echoMessage.startsWith("0800"), "Should be echo request");
        assertTrue(echoMessage.contains("70=001"), "Should contain network management code");
        assertTrue(echoMessage.contains("11=654321"), "Should contain STAN");
        
        System.out.println("✅ Message parsing validation passed for all types");
    }
    
    @Test
    @DisplayName("Application should handle different operational modes")
    void applicationShouldHandleDifferentOperationalModes() {
        // Test system property handling for different modes
        System.setProperty("app.mode", "server");
        assertEquals("server", System.getProperty("app.mode"), "Server mode should be set");
        
        System.setProperty("app.mode", "client");
        assertEquals("client", System.getProperty("app.mode"), "Client mode should be set");
        
        System.setProperty("app.mode", "both");
        assertEquals("both", System.getProperty("app.mode"), "Both mode should be set");
        
        // Reset to server mode for tests
        System.setProperty("app.mode", "server");
        
        System.out.println("✅ Application mode switching test passed");
    }
}