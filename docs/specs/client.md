# Client Module Specification

## Overview

The Client module serves as the REST API gateway and connection manager for ISO 8583 communications. It provides web APIs for the console interface and manages socket connections to ISO 8583 servers with optional Kafka integration for authorization workflows.

## Architecture

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Networking**: Netty (async socket client)
- **Messaging**: Apache Kafka (optional)
- **Observability**: OpenTelemetry, Micrometer
- **Runtime**: Java 17

### Port Configuration
- **HTTP API**: 8081 (REST endpoints)
- **Actuator**: 8081 (health, metrics)
- **ISO Server**: 8583 (outbound connections)

## Project Structure
```
src/main/java/com/example/client/
├── DemoApplication.java
├── controller/
│   └── Iso8583Controller.java
├── service/
│   ├── ConnectionService.java
│   └── ResponseConsumerService.java
├── model/
│   ├── ApiResponse.java
│   └── ConnectionInfo.java
├── client/
│   └── Iso8583Client.java
├── config/
│   ├── KafkaConfig.java
│   └── OpenTelemetryConfig.java
└── processor/
    └── Iso8583Processor.java
```

## Core Features

### 1. Connection Management
- **Multi-Connection Support**: Manage multiple ISO 8583 server connections
- **Netty-Based Client**: Asynchronous, high-performance socket connections
- **Connection Pooling**: Efficient resource management
- **Auto-Reconnection**: Resilient connection handling

### 2. REST API Gateway
- **CORS Enabled**: Cross-origin support for web console
- **RESTful Endpoints**: Standard HTTP operations
- **JSON Responses**: Structured API responses
- **Error Handling**: Comprehensive exception management

### 3. Message Processing
- **ISO 8583 Validation**: Message format validation using common module
- **STAN Generation**: Automatic sequence number management
- **Echo Testing**: Network connectivity verification
- **Correlation Tracking**: Request-response correlation via STAN

### 4. Authorization Integration
- **Kafka Producer**: Send messages for authorization
- **Kafka Consumer**: Receive authorization responses
- **Partition Strategy**: Load balancing using field 37 (RRN)
- **Configurable**: Enable/disable authorization workflow

## REST API Endpoints

### Connection Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/iso8583/connections` | List all connections |
| POST | `/api/iso8583/connections` | Create new connection |
| DELETE | `/api/iso8583/connections/{id}` | Remove connection |
| POST | `/api/iso8583/connections/{id}/connect` | Establish connection |
| POST | `/api/iso8583/connections/{id}/disconnect` | Close connection |

### Message Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/iso8583/connections/{id}/send` | Send ISO message |
| POST | `/api/iso8583/connections/{id}/echo` | Send echo test |

### API Response Format
```json
{
  "success": true,
  "message": "Operation completed",
  "request": "MTI=0200|2=4000...",
  "response": "MTI=0210|2=4000..."
}
```

## Configuration

### Application Properties
```properties
# Server Configuration
spring.application.name=client
server.port=8081

# Authorization Configuration
iso8583.client.authorization.enabled=true

# ISO 8583 Server Configuration
iso8583.server.port=8583

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.consumer.group-id=client-response-consumer

# OpenTelemetry Configuration
otel.service.name=iso8583-client
otel.exporter.otlp.endpoint=http://localhost:4317
```

### Environment Variables
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
ISO8583_CLIENT_AUTHORIZATION_ENABLED=true
```

## Message Flow

### Direct Mode (Authorization Disabled)
1. **REST Request** → Client API
2. **Validation** → ISO 8583 message validation
3. **Socket Send** → Direct to ISO server
4. **Response** → Return to caller

### Authorization Mode (Kafka Enabled)
1. **REST Request** → Client API
2. **Validation** → ISO 8583 message validation
3. **Kafka Publish** → Send to `iso8583-requests` topic
4. **Authorization** → Authorize service processes
5. **Kafka Consume** → Receive from `iso8583-responses` topic
6. **Socket Forward** → Send authorized response to server

## Netty Client Implementation

### Connection Features
- **Length-Prefixed Protocol**: 2-byte length header
- **Keep-Alive**: Socket keep-alive enabled
- **Async Processing**: Non-blocking I/O operations
- **Connection Pooling**: Efficient resource management

### Message Handling
```java
// Frame decoder for 2-byte length prefix
pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
pipeline.addLast(new LengthFieldPrepender(2));
pipeline.addLast(new ClientHandler(connectionId));
```

## Observability

### OpenTelemetry Integration
- **Distributed Tracing**: End-to-end request tracing
- **Custom Spans**: ISO 8583 specific spans
- **Correlation IDs**: STAN-based correlation
- **Metrics Collection**: Connection and message metrics

### Key Metrics
- `iso8583_connections_total`: Total connection count
- `iso8583_messages_total`: Total message count
- HTTP request metrics (Spring Boot Actuator)
- Kafka producer/consumer metrics

### Health Checks
```bash
# Application health
GET /actuator/health

# Metrics endpoint
GET /actuator/metrics

# Prometheus metrics
GET /actuator/prometheus
```

## Development

### Local Development
```bash
cd source/client
mvn spring-boot:run
```

### Build
```bash
mvn clean package
```

### Testing
```bash
# Run all tests
mvn test

# Integration tests
mvn test -Dtest=DemoApplicationTests

# Unit tests
mvn test -Dtest=Iso8583MessageTest

# Test with coverage
mvn clean test jacoco:report
```

## Deployment

### Docker Build
```bash
cd source/client
mvn clean package
docker build -t iso8583-client:latest .
```

### Kubernetes Deployment
```bash
kubectl apply -f k8s/client-configmap.yaml
kubectl apply -f k8s/client-deployment.yaml
kubectl apply -f k8s/client-service.yaml
kubectl apply -f k8s/client-ingress.yaml
```

### Resource Requirements
- **Memory**: 512Mi (request) / 1Gi (limit)
- **CPU**: 500m (request) / 1000m (limit)

## Security Considerations

### Input Validation
- **ISO 8583 Validation**: Message format validation
- **Connection Parameters**: Host/port validation
- **CORS Configuration**: Controlled cross-origin access

### Network Security
- **Internal Communication**: Kafka within cluster
- **TLS Support**: Configurable for external connections
- **Authentication**: Ready for integration with auth systems

## Integration Points

### Upstream Dependencies
- **Console Module**: Web UI consuming REST APIs
- **Common Module**: ISO 8583 parsing and validation

### Downstream Dependencies
- **Server Module**: ISO 8583 message processing
- **Authorize Module**: Kafka-based authorization (optional)
- **Kafka Cluster**: Message broker (optional)

## Troubleshooting

### Common Issues

#### Connection Failures
```bash
# Check server connectivity
telnet <server-host> 8583

# View connection logs
kubectl logs -f deployment/client
```

#### Kafka Issues
```bash
# Check Kafka connectivity
kubectl exec -it kafka-pod -- kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor topics
kubectl exec -it kafka-pod -- kafka-console-consumer.sh --topic iso8583-requests --bootstrap-server localhost:9092
```

#### API Issues
```bash
# Test API endpoints
curl -X GET http://localhost:8081/api/iso8583/connections

# Check health
curl http://localhost:8081/actuator/health
```

### Debug Configuration
```properties
# Enable debug logging
logging.level.com.example.client=DEBUG
logging.level.org.springframework.kafka=DEBUG
logging.level.io.netty=DEBUG
```