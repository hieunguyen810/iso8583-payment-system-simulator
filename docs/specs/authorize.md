# Authorize Module Specification

## Overview

The Authorize module is a Kafka-based microservice that processes ISO 8583 authorization requests and generates appropriate responses. It acts as a simulated authorization system for payment transactions.

## Architecture

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Messaging**: Apache Kafka
- **Runtime**: Java 17
- **Container**: Docker with Alpine Linux
- **Observability**: OpenTelemetry

### Port Configuration
- **HTTP**: 8082 (health checks and actuator endpoints)
- **Kafka**: Connects to bootstrap servers on port 9092

## Core Functionality

### Message Processing Flow
1. **Consume** authorization requests from `iso8583-requests` topic
2. **Parse** ISO 8583 message using common parser
3. **Process** authorization logic for MTI 0200 (Financial Transaction)
4. **Generate** response with MTI 0210 (Financial Transaction Response)
5. **Publish** response to `iso8583-responses` topic

### Supported Message Types
| MTI | Description | Action |
|-----|-------------|--------|
| 0200 | Financial Transaction Request | Process and respond with 0210 |
| Others | Non-financial messages | Ignored |

## Message Processing Logic

### Authorization Response Generation
```java
// Response fields mapping
MTI: 0210 (Financial Transaction Response)
Field 2: PAN (copied from request)
Field 3: Processing Code (copied from request)  
Field 4: Transaction Amount (copied from request)
Field 7: Transmission Date/Time (current timestamp)
Field 11: STAN (copied from request)
Field 37: RRN (copied from request)
Field 38: Approval Code (6-digit random number)
Field 39: Response Code (00 = Approved)
```

### Business Rules
- **Auto-Approval**: All transactions are automatically approved (Response Code: 00)
- **Approval Code**: Generated as 6-digit random number (000000-999999)
- **Timestamp**: Current system time in MMddHHmmss format
- **Field Preservation**: Key request fields are copied to response

## Configuration

### Application Properties
```properties
spring.application.name=authorize
server.port=8082

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=authorize-service
spring.kafka.consumer.auto-offset-reset=latest
spring.kafka.consumer.enable-auto-commit=true

# Logging
logging.level.com.example.authorize=DEBUG
```

### Environment Variables
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SPRING_KAFKA_CONSUMER_GROUP_ID=authorize-service
```

## Kafka Integration

### Consumer Configuration
- **Topic**: `iso8583-requests`
- **Group ID**: `authorize-service`
- **Offset Reset**: `latest`
- **Auto Commit**: `true`
- **Deserializer**: StringDeserializer

### Producer Configuration
- **Topic**: `iso8583-responses`
- **Serializer**: StringSerializer
- **Delivery**: Fire-and-forget

### Topic Structure
```bash
# Create topics
kafka-topics.sh --create --topic iso8583-requests --partitions 3 --replication-factor 1
kafka-topics.sh --create --topic iso8583-responses --partitions 3 --replication-factor 1
```

## Dependencies

### Maven Dependencies
```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>common</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

## Development

### Local Development
```bash
cd source/authorize
mvn spring-boot:run
```

### Build
```bash
mvn clean install
```

### Testing with Kafka
```bash
# Start Kafka locally
bin/kafka-server-start.sh config/server.properties

# Send test message
kafka-console-producer.sh --topic iso8583-requests --bootstrap-server localhost:9092
> MTI=0200|2=4000123456789012|3=000000|4=000000001000|11=000001|37=000000000001

# Monitor responses
kafka-console-consumer.sh --topic iso8583-responses --bootstrap-server localhost:9092
```

## Deployment

### Docker Build
```bash
cd source/authorize
mvn clean package
docker build -t iso8583-authorize:latest .
```

### Kubernetes Deployment
```bash
kubectl apply -f k8s/authorize-deployment.yaml
kubectl apply -f k8s/authorize-hpa.yaml
```

### Resource Requirements
- **Memory**: 256Mi (request) / 512Mi (limit)
- **CPU**: 250m (request) / 500m (limit)
- **Replicas**: 3 (matches Kafka partition count)

## Scaling and Performance

### Horizontal Pod Autoscaler
- **Min Replicas**: 2
- **Max Replicas**: 3
- **CPU Target**: 70% utilization
- **Memory Target**: 80% utilization

### Kafka Consumer Scaling
- **Partitions**: 3 (matches deployment replicas)
- **Consumer Group**: Single group for load balancing
- **Processing**: Parallel processing across partitions

## Monitoring and Observability

### Health Checks
```bash
# Liveness probe
GET /actuator/health

# Readiness probe  
GET /actuator/health
```

### OpenTelemetry Integration
- **Tracing**: Automatic span creation for Kafka operations
- **Metrics**: Consumer lag, processing time, error rates
- **Logs**: Structured logging with correlation IDs

### Key Metrics
- Message processing rate
- Authorization response time
- Kafka consumer lag
- Error rate and types

## Error Handling

### Exception Scenarios
- **Parse Errors**: Invalid ISO 8583 message format
- **Kafka Errors**: Connection failures, serialization issues
- **Processing Errors**: Missing required fields

### Error Response Strategy
```java
try {
    // Process authorization
} catch (Exception e) {
    System.err.println("❌ Error processing authorization: " + e.getMessage());
    // Continue processing (no dead letter queue)
}
```

## Security Considerations

### Container Security
- **Non-root User**: Runs as `spring:spring` user
- **Minimal Base Image**: Alpine Linux for reduced attack surface
- **No Exposed Secrets**: Configuration via environment variables

### Network Security
- **Internal Communication**: Kafka communication within cluster
- **Health Endpoints**: Only actuator endpoints exposed
- **No External APIs**: Pure message processing service

## Integration Points

### Upstream Dependencies
- **Client Module**: Publishes requests to `iso8583-requests`
- **Kafka Cluster**: Message broker infrastructure

### Downstream Consumers
- **Client Module**: Consumes responses from `iso8583-responses`
- **Monitoring Systems**: Metrics and tracing data

## Troubleshooting

### Common Issues

#### Kafka Connection Failures
```bash
# Check Kafka connectivity
kubectl logs -f deployment/authorize
# Look for: "❌ Error processing authorization"
```

#### Consumer Lag Issues
```bash
# Monitor consumer group
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group authorize-service
```

#### Message Processing Errors
```bash
# Enable debug logging
logging.level.com.example.authorize=DEBUG
logging.level.org.springframework.kafka=DEBUG
```

### Debug Commands
```bash
# View application logs
kubectl logs -f deployment/authorize

# Check health status
kubectl exec -it deployment/authorize -- curl localhost:8082/actuator/health

# Monitor Kafka topics
kubectl exec -it kafka-pod -- kafka-console-consumer.sh --topic iso8583-responses --bootstrap-server localhost:9092
```

## Future Enhancements

### Planned Features
- **Advanced Authorization Rules**: Risk scoring, velocity checks
- **Database Integration**: Transaction history and account validation
- **Dead Letter Queue**: Failed message handling
- **Circuit Breaker**: Resilience patterns for external dependencies
- **Batch Processing**: High-throughput message processing
- **Custom Response Codes**: Configurable decline reasons