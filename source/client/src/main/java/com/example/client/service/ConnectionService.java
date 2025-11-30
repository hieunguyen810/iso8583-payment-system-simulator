package com.example.client.service;

import com.example.client.model.ConnectionInfo;
import com.example.common.model.Iso8583Message;
import com.example.common.model.ValidationResult;
import com.example.common.parser.Iso8583Parser;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.util.AttributeKey;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ConnectionService {

    private final Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();
    private final Map<String, Channel> activeChannels = new ConcurrentHashMap<>();
    private final Map<String, EventLoopGroup> eventLoopGroups = new ConcurrentHashMap<>();
    private final AtomicInteger stanCounter = new AtomicInteger(1);
    
    @Autowired
    private Tracer tracer;
    
    @Autowired
    private Meter meter;
    
    private LongCounter connectionCounter;
    private LongCounter messageCounter;
    
    @PostConstruct
    public void init() {
        connectionCounter = meter.counterBuilder("iso8583_connections_total")
                .setDescription("Total number of ISO 8583 connections")
                .build();
        messageCounter = meter.counterBuilder("iso8583_messages_total")
                .setDescription("Total number of ISO 8583 messages sent")
                .build();
    }

    public List<ConnectionInfo> getAllConnections() {
        return new ArrayList<>(connections.values());
    }

    public void addConnection(ConnectionInfo connectionInfo) {
        connections.put(connectionInfo.getConnectionId(), connectionInfo);
    }

    public void connect(String connectionId) throws Exception {
        Span span = tracer.spanBuilder("iso8583.connection.connect")
                .setAttribute("connection.id", connectionId)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            ConnectionInfo conn = connections.get(connectionId);
            if (conn == null) {
                span.setStatus(StatusCode.ERROR, "Connection not found");
                throw new RuntimeException("Connection not found: " + connectionId);
            }

            if (conn.isConnected()) {
                span.setStatus(StatusCode.ERROR, "Already connected");
                throw new RuntimeException("Already connected: " + connectionId);
            }
            
            span.setAttribute("connection.host", conn.getHost())
                .setAttribute("connection.port", conn.getPort());

            EventLoopGroup group = new NioEventLoopGroup();
            eventLoopGroups.put(connectionId, group);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                            pipeline.addLast(new LengthFieldPrepender(2));
                            pipeline.addLast(new ClientHandler(connectionId));
                        }
                    });

            ChannelFuture future = bootstrap.connect(conn.getHost(), conn.getPort()).sync();
            Channel channel = future.channel();
            activeChannels.put(connectionId, channel);
            conn.setConnected(true);
            
            connectionCounter.add(1, io.opentelemetry.api.common.Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("connection.id"), connectionId,
                io.opentelemetry.api.common.AttributeKey.stringKey("operation"), "connect"
            ));
            
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    public void disconnect(String connectionId) throws Exception {
        ConnectionInfo conn = connections.get(connectionId);
        if (conn == null) {
            throw new RuntimeException("Connection not found: " + connectionId);
        }

        Channel channel = activeChannels.remove(connectionId);
        if (channel != null && channel.isActive()) {
            channel.close().sync();
        }

        EventLoopGroup group = eventLoopGroups.remove(connectionId);
        if (group != null) {
            group.shutdownGracefully();
        }

        conn.setConnected(false);
    }

    public void removeConnection(String connectionId) throws Exception {
        if (connections.containsKey(connectionId)) {
            disconnect(connectionId);
            connections.remove(connectionId);
        }
    }

    public String[] sendEcho(String connectionId) throws Exception {
        Span span = tracer.spanBuilder("iso8583.message.echo")
                .setAttribute("connection.id", connectionId)
                .setAttribute("message.type", "0800")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Channel channel = getActiveChannel(connectionId);
            
            Iso8583Message echoMsg = new Iso8583Message();
            echoMsg.setMti("0800");
            echoMsg.addField(7, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
            String stan = String.format("%06d", stanCounter.getAndIncrement());
            echoMsg.addField(11, stan);
            echoMsg.addField(70, "001");
            
            span.setAttribute("message.stan", stan);
            
            String request = echoMsg.toString();
            String response = sendAndWaitForResponse(channel, request);
            
            messageCounter.add(1, io.opentelemetry.api.common.Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("connection.id"), connectionId,
                io.opentelemetry.api.common.AttributeKey.stringKey("message.type"), "echo"
            ));
            
            span.setStatus(StatusCode.OK);
            return new String[]{request, response};
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${iso8583.client.authorization.enabled:false}")
    private boolean authorizationEnabled;
    
    @Value("${kafka.topic.iso8583.request:iso8583-requests}")
    private String requestTopic;

    public String[] sendMessage(String connectionId, String message) throws Exception {
        // Parse message to get STAN for correlation
        Iso8583Message parsedMsg = Iso8583Parser.parseMessage(message);
        String stan = parsedMsg.getField(11) != null ? parsedMsg.getField(11) : "unknown";
        
        Span span = tracer.spanBuilder("iso8583.message.send")
                .setAttribute("connection.id", connectionId)
                .setAttribute("iso8583.stan", stan)
                .setAttribute("iso8583.correlation_id", stan)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Validate message
            ValidationResult validation = Iso8583Parser.validateMessage(parsedMsg);
            
            span.setAttribute("message.mti", parsedMsg.getMti())
                .setAttribute("message.stan", stan);
            
            if (!validation.isValid()) {
                span.setStatus(StatusCode.ERROR, "Invalid message");
                throw new RuntimeException("Invalid message: " + String.join(", ", validation.getErrors()));
            }
            
            if (authorizationEnabled && kafkaTemplate != null) {
                // Send to Kafka for authorization with field 37 as partition key for load balancing
                String partitionKey = parsedMsg.getField(37);
                if (partitionKey == null) partitionKey = connectionId;
                span.setAttribute("kafka.partition.key", partitionKey)
                    .setAttribute("kafka.topic", requestTopic);
                System.out.println("📤 Sending to Kafka for authorization with key: " + partitionKey);
                kafkaTemplate.send(requestTopic, partitionKey, message);
                
                messageCounter.add(1, io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.stringKey("connection.id"), connectionId,
                    io.opentelemetry.api.common.AttributeKey.stringKey("message.type"), "kafka"
                ));
                
                span.setStatus(StatusCode.OK);
                return new String[]{message, "Sent to authorization service"};
            } else {
                // Direct send to server
                Channel channel = getActiveChannel(connectionId);
                String response = sendAndWaitForResponse(channel, message);
                
                messageCounter.add(1, io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.stringKey("connection.id"), connectionId,
                    io.opentelemetry.api.common.AttributeKey.stringKey("message.type"), "direct"
                ));
                
                span.setStatus(StatusCode.OK);
                return new String[]{message, response};
            }
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private Channel getActiveChannel(String connectionId) throws Exception {
        Channel channel = activeChannels.get(connectionId);
        if (channel == null || !channel.isActive()) {
            throw new RuntimeException("Connection not active: " + connectionId);
        }
        return channel;
    }

    public void broadcastToConnectedServers(String message) {
        activeChannels.forEach((connectionId, channel) -> {
            if (channel != null && channel.isActive()) {
                try {
                    ByteBuf buf = channel.alloc().buffer();
                    buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));
                    channel.writeAndFlush(buf);
                    System.out.println("📤 Sent to " + connectionId + ": " + message);
                } catch (Exception e) {
                    System.err.println("❌ Failed to send to " + connectionId + ": " + e.getMessage());
                }
            }
        });
    }

    private String sendAndWaitForResponse(Channel channel, String message) throws Exception {
        Span span = tracer.spanBuilder("iso8583.client.socket_send")
                .setAttribute("channel.id", channel.id().asShortText())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            
            // Store the future in channel attributes for the handler to complete
            channel.attr(AttributeKey.valueOf("responseFuture")).set(responseFuture);
            
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));
            channel.writeAndFlush(buf);
            
            String response = responseFuture.get(10, TimeUnit.SECONDS);
            span.setStatus(StatusCode.OK);
            return response;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final String connectionId;

        public ClientHandler(String connectionId) {
            this.connectionId = connectionId;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            String message = msg.toString(StandardCharsets.UTF_8);
            
            // Create span for received message
            Span span = tracer.spanBuilder("iso8583.client.socket_receive")
                    .setAttribute("channel.id", ctx.channel().id().asShortText())
                    .startSpan();
            
            try (Scope scope = span.makeCurrent()) {
                // Extract STAN for correlation
                try {
                    Iso8583Message parsedMsg = Iso8583Parser.parseMessage(message);
                    String stan = parsedMsg.getField(11);
                    if (stan != null) {
                        span.setAttribute("iso8583.stan", stan)
                            .setAttribute("iso8583.correlation_id", stan);
                    }
                    span.setAttribute("message.mti", parsedMsg.getMti());
                } catch (Exception e) {
                    // Continue if parsing fails
                }
                
                System.out.println("📨 Received from server: " + message);
            
            // Check if this is a response to a pending request
            CompletableFuture<String> future = ctx.channel().attr(AttributeKey.<CompletableFuture<String>>valueOf("responseFuture")).get();
            if (future != null) {
                // This is a response to our request
                future.complete(message);
                ctx.channel().attr(AttributeKey.valueOf("responseFuture")).set(null);
            } else if (authorizationEnabled && kafkaTemplate != null) {
                // This is an unsolicited message from server - send to Kafka for authorization
                Iso8583Message parsedMsg = Iso8583Parser.parseMessage(message);
                String partitionKey = parsedMsg.getField(37);
                if (partitionKey == null) partitionKey = connectionId;
                System.out.println("📤 Sending unsolicited message to Kafka with key: " + partitionKey);
                kafkaTemplate.send(requestTopic, partitionKey, message);
                } else {
                    // No authorization - just log the message
                    System.out.println("📝 Unsolicited message (no authorization): " + message);
                }
                
                span.setStatus(StatusCode.OK);
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
            } finally {
                span.end();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ConnectionInfo conn = connections.get(connectionId);
            if (conn != null) {
                conn.setConnected(false);
            }
            activeChannels.remove(connectionId);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ConnectionInfo conn = connections.get(connectionId);
            if (conn != null) {
                conn.setConnected(false);
            }
            ctx.close();
        }
    }
}