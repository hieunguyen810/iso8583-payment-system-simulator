# Observability

## 1.Front-End

**Grafana Faro Integration**

Set ```FARO_ENABLED: "true"``` to enable Grafana Faro.
Faro will send data to Grafana Cloud. (or Alloy for local deployment)


## 2. Spring Boot *

**Actuator Integration**
##### Server
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Transaction Metrics:
    - http://localhost:8080/actuator/metrics/iso8583.transactions.successful
    - http://localhost:8080/actuator/metrics/iso8583.transactions.failed

## 3. OpenTelemetry Integration

The ISO 8583 client module is now integrated with OpenTelemetry for comprehensive observability.

## Features

### Distributed Tracing
- Connection establishment and management
- ISO 8583 message sending (echo and custom messages)
- Kafka message publishing (when authorization is enabled)
- HTTP API requests

### Metrics
- Connection counters by operation type
- Message counters by type (echo, kafka, direct)
- Built-in Spring Boot metrics via Micrometer

### Instrumentation Points
- `iso8583.connection.connect` - Connection establishment
- `iso8583.message.echo` - Echo message operations
- `iso8583.message.send` - Custom message operations
- `http.request.send_message` - HTTP API calls

## Quick Start

### 1. Start Observability Stack
```bash
docker compose -f docker-compose-otel.yml up -d
```

### 2. Start Client Application
```bash
cd source/client
mvn spring-boot:run
```

### 3. Access Dashboards
- **Grafana Alloy UI**: http://localhost:12345 (Telemetry Collection)
- **Prometheus**: http://localhost:9090 (Metrics)
- **Grafana**: http://localhost:3000 (Visualization - admin/admin)

## Configuration

### Application Properties
```properties
# OpenTelemetry Configuration
otel.service.name=iso8583-client
otel.exporter.otlp.endpoint=http://localhost:4317
otel.traces.exporter=otlp
otel.metrics.exporter=otlp
```

### Custom Metrics
- `iso8583_connections_total` - Total connections by operation
- `iso8583_messages_total` - Total messages by type

### Trace Attributes
- `connection.id` - Connection identifier
- `connection.host` - Target host
- `connection.port` - Target port
- `message.mti` - Message Type Indicator
- `message.stan` - System Trace Audit Number
- `message.type` - Message category (echo, kafka, direct)
- `kafka.partition.key` - Kafka partition key
- `kafka.topic` - Kafka topic name

## Usage Examples

### View Connection Traces
1. Create a connection via API
2. Connect to server
3. Check Tempo for `iso8583.connection.connect` spans

### Monitor Message Flow
1. Send messages via API
2. View traces in Tempo showing complete request flow
3. Check Prometheus for message counters

### Kafka Integration Tracing
1. Enable authorization: `iso8583.client.authorization.enabled=true`
2. Send messages - traces will show Kafka publishing
3. Monitor Kafka-related spans and attributes