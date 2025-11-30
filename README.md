# ISO 8583 Demo Project
This project simulate the ISO-8583 transaction flow. Contain common domain: issuing bank, accquiring bank and card network scheme. The main purpose of this project is create a sample payment transaction environment. Help me easier to build and practice DevOps.

## Overview

![image](iso8583.drawio.png)

## Modules

### 1. Common Module
**Purpose**: Shared ISO 8583 message models and parsing utilities
- `Iso8583Message`: Core message model with field mapping
- `Iso8583Parser`: Message parsing and field extraction utilities

### 2. Client Module
**Purpose**: REST API gateway for web console interaction

### 3. Server Module
**Purpose**: ISO 8583 message processing server


### 4. Authorize Module
**Purpose**: Transaction authorization service


### 5. Simulator Module
**Purpose**: Transaction simulation and testing


### 6. Console (Web UI)
**Purpose**: Web-based management interface


## Prerequisites

- Java 17+
- Maven 3.6+
- NodeJS 20+
- Apache Kafka (optional, for authorization)
- Docker (optional, for containerized deployment)

## Optional Services

### Authorization Service (Kafka Required)
1. Start Kafka:
```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka
bin/kafka-server-start.sh config/server.properties
```

## License

This project is for demonstration purposes.