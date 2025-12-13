# Kafka to ActiveMQ Migration Guide

## Overview
This guide explains how to migrate from Kafka to ActiveMQ for reduced resource consumption.

## Resource Comparison

### Before (Kafka)
- **Zookeeper**: ~256MB RAM
- **Kafka**: ~512MB RAM  
- **Total**: ~768MB RAM + 2 CPUs

### After (ActiveMQ)
- **ActiveMQ**: ~256MB RAM
- **Total**: ~256MB RAM + 0.5 CPU

**Resource Savings: ~66% less memory, 75% less CPU**

## Migration Steps

### 1. Use ActiveMQ Profile
```bash
# Start with ActiveMQ
./start-activemq.sh

# Or manually with profile
mvn spring-boot:run -Dspring-boot.run.profiles=activemq
```

### 2. Key Changes Made
- Replaced `spring-kafka` with `spring-boot-starter-activemq`
- Created ActiveMQ configurations
- Updated message processors
- Switched from topics to queues
- Simplified connection pooling

### 3. Configuration Changes
- **Kafka Topics** → **ActiveMQ Queues**
  - `iso8583-requests` → `iso8583.requests`
  - `iso8583-responses` → `iso8583.responses`

### 4. Code Changes
- `@KafkaListener` → `@JmsListener`
- `KafkaTemplate` → `JmsTemplate`
- Removed partitioning logic (not needed for demo)

## Benefits for Demo Environment

1. **Lower Memory Usage**: 66% reduction
2. **Simpler Setup**: No Zookeeper required
3. **Web Console**: Built-in management at http://localhost:8161
4. **Faster Startup**: ActiveMQ starts in ~10 seconds vs Kafka's ~30 seconds
5. **Better for Low Volume**: More efficient for demo traffic patterns

## When to Use Each

### Use ActiveMQ When:
- Development/demo environments
- Low message volume (<1000 msg/sec)
- Simple messaging patterns
- Resource constraints
- Quick setup needed

### Use Kafka When:
- Production environments
- High throughput (>10000 msg/sec)
- Stream processing needed
- Multiple consumers per topic
- Event sourcing patterns

## Rollback Plan
To switch back to Kafka:
1. Use original profiles: `mvn spring-boot:run` (no profile)
2. Start Kafka: `docker-compose -f docker-compose-lightweight.yml up -d`
3. Both implementations coexist, controlled by profiles