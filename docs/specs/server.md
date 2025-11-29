# Server Module Specification

## Overview

The Server module is the core ISO 8583 message processing engine that handles socket connections and gRPC communications. It processes financial transactions, manages client connections, and provides transaction persistence with comprehensive observability.

## Architecture

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Networking**: Netty (socket server)
- **RPC**: gRPC (simulator communication)
- **Database**: PostgreSQL with JPA/Hibernate
- **Observability**: OpenTelemetry, Micrometer
- **Runtime**: Java 17

### Port Configuration
- **HTTP**: 8080 (Spring Boot actuator)
- **ISO Socket**: 8583 (Netty server)
- **gRPC**: 9090 (manual gRPC server)

## Project Structure
```
src/main/java/com/example/server/
├── DemoApplication.java
├── server/
│   └── Iso8583Server.java
├── grpc/
│   └── Iso8583ServiceImpl.java
├── service/
│   ├── Iso8583Processor.java
│   └── TransactionTimer.java
├── entity/
│   ├── Transaction.java
│   └── TransactionEvent.java
├── repository/
│   ├── TransactionRepository.java
│   └── TransactionEventRepository.java
├── metrics/
│   ├── TransactionMetrics.java
│   └── ResponseTimeMetrics.java
├── config/
│   ├── GrpcConfig.java
│   └── OpenTelemetryConfig.java
└── controller/
    └── MetricsTestController.java
```

## Core Features

### 1. Netty Socket Server
- **Multi-Client Support**: Concurrent client connections
- **Length-Prefixed Protocol**: 2-byte message length header
- **Connection Management**: Active client tracking and broadcasting
- **Async Processing**: Non-blocking I/O operations

### 2. ISO 8583 Message Processing
- **Message Validation**: Format and field validation
- **Transaction Processing**: Authorization and echo handling
- **Response Generation**: Automatic response creation
- **Field Mapping**: Request-response field correlation

### 3. gRPC Service
- **Simulator Integration**: Receive transactions from simulator
- **Client Broadcasting**: Forward messages to socket clients
- **Database Persistence**: Transaction and event logging
- **Validation**: Message format verification

### 4. Database Integration
- **Transaction Storage**: Complete transaction records
- **Event Logging**: Transaction lifecycle events
- **Configurable**: Enable/disable database writes
- **PostgreSQL**: Production-ready persistence

## Message Processing Logic

### Supported Message Types
| MTI | Description | Response MTI | Action |
|-----|-------------|--------------|--------|
| 0200 | Financial Transaction | 0210 | Process authorization |
| 0800 | Network Management | 0810 | Echo response |
| 0210 | Authorization Response | - | Complete transaction |

### Authorization Processing (0200 → 0210)
```java
// Response fields
MTI: 0210 (Financial Transaction Response)
Field 2: PAN (copied from request)
Field 3: Processing Code (copied from request)
Field 4: Transaction Amount (copied from request)
Field 7: Transmission Date/Time (copied from request)
Field 11: STAN (copied from request)
Field 37: RRN (copied from request)
Field 38: Approval Code (6-digit random)
Field 39: Response Code (00 = Approved)
```

### Echo Processing (0800 → 0810)
```java
// Response fields
MTI: 0810 (Network Management Response)
Field 7: Current timestamp (MMddHHmmss)
Field 11: STAN (copied from request)
Field 70: Network Management Code (copied from request)
```

## Configuration

### Application Properties
```properties
# Server Configuration
spring.application.name=iso8583-server
server.port=8080

# ISO 8583 Server Configuration
iso8583.server.port=8583
iso8583.server.thread-pool-size=10

# gRPC Server Configuration
grpc.server.port=9090

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/iso8583
spring.datasource.username=postgres
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

# OpenTelemetry Configuration
otel.service.name=iso8583-server
otel.exporter.otlp.endpoint=http://localhost:4317
```

### Environment Variables
```bash
POSTGRES_URL=jdbc:postgresql://postgres:5432/iso8583
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password
ISO8583_DATABASE_WRITE_ENABLED=true
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

## Netty Server Implementation

### Channel Pipeline
```java
// Inbound: Length field decoder + String decoder
pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));

// Outbound: Length field prepender + String encoder
pipeline.addLast(new LengthFieldPrepender(2));
pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));

// Business logic handler
pipeline.addLast(new Iso8583ServerHandler());
```

### Connection Management
- **Client Tracking**: ConcurrentHashMap for active connections
- **Broadcasting**: Send messages to all connected clients
- **Graceful Shutdown**: Proper resource cleanup
- **Error Handling**: Connection failure recovery

## gRPC Service Implementation

### Service Definition
```protobuf
service Iso8583Service {
  rpc SendTransaction(TransactionRequest) returns (TransactionResponse);
}

message TransactionRequest {
  string client_id = 1;
  string message = 2;
}

message TransactionResponse {
  bool success = 1;
  string message = 2;
}
```

### Processing Flow
1. **Receive** gRPC transaction from simulator
2. **Validate** ISO 8583 message format
3. **Persist** transaction to database (if enabled)
4. **Broadcast** to all socket clients
5. **Log** transaction events
6. **Respond** to gRPC caller

## Database Schema

### Transaction Entity
```sql
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    source_number VARCHAR(255),
    target_number VARCHAR(255),
    status VARCHAR(50),
    amount DECIMAL(19,2),
    stan VARCHAR(6),
    mti VARCHAR(4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Transaction Event Entity
```sql
CREATE TABLE transaction_events (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT REFERENCES transactions(id),
    event_type VARCHAR(50),
    iso_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Observability

### OpenTelemetry Integration
- **Distributed Tracing**: End-to-end transaction tracing
- **Custom Spans**: ISO 8583 processing spans
- **Correlation**: STAN-based request correlation
- **Metrics**: Transaction processing metrics

### Key Metrics
- Transaction processing rate
- Response time distribution
- Error rates by message type
- Active connection count
- Database operation metrics

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
cd source/server
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

### Database Setup
```bash
# Start PostgreSQL
docker run -d --name postgres \
  -e POSTGRES_DB=iso8583 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 postgres:15
```

## Deployment

### Docker Build
```bash
cd source/server
mvn clean package
docker build -t iso8583-server:latest .
```

### Kubernetes Deployment
```bash
kubectl apply -f k8s/server-deployment.yaml
kubectl apply -f k8s/server-service.yaml
kubectl apply -f k8s/server-grpc-service.yaml
```

### Resource Requirements
- **Memory**: 1Gi (request) / 2Gi (limit)
- **CPU**: 500m (request) / 1000m (limit)

## Security Considerations

### Network Security
- **Internal Communication**: gRPC within cluster
- **Socket Security**: Length-prefixed protocol prevents buffer overflow
- **Database Security**: Connection pooling and prepared statements

### Input Validation
- **ISO 8583 Validation**: Message format validation
- **Field Validation**: Required field checks
- **SQL Injection Prevention**: JPA/Hibernate protection

## Integration Points

### Upstream Dependencies
- **Client Module**: Socket connections for message exchange
- **Simulator Module**: gRPC transactions
- **Common Module**: ISO 8583 parsing and validation

### Downstream Dependencies
- **PostgreSQL**: Transaction persistence
- **OpenTelemetry Collector**: Observability data

## Troubleshooting

### Common Issues

#### Socket Connection Issues
```bash
# Check port availability
netstat -tlnp | grep 8583

# Test socket connectivity
telnet localhost 8583

# View server logs
kubectl logs -f deployment/server
```

#### gRPC Issues
```bash
# Test gRPC service
grpcurl -plaintext localhost:9090 list

# Check gRPC health
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check
```

#### Database Issues
```bash
# Check database connectivity
psql -h localhost -U postgres -d iso8583

# View transaction tables
SELECT * FROM transactions ORDER BY created_at DESC LIMIT 10;
```

### Debug Configuration
```properties
# Enable debug logging
logging.level.com.example.server=DEBUG
logging.level.io.netty=DEBUG
logging.level.io.grpc=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

## Performance Tuning

### Netty Configuration
```properties
# Thread pool sizing
iso8583.server.thread-pool-size=20

# Connection limits
netty.server.max-connections=1000
netty.server.backlog=128
```

### Database Optimization
```properties
# Connection pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# JPA optimization
spring.jpa.properties.hibernate.jdbc.batch_size=25
spring.jpa.properties.hibernate.order_inserts=true
```