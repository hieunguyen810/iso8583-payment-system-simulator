# ISO 8583 Demo Project
This project simulate the ISO-8583 transaction flow. Contain common domains: issuing bank, accquiring bank and card network scheme. The main purpose of this project is create a sample payment transaction environment.

## Overview

![image](iso8583.drawio.png)

## Modules

- Common Module Shared ISO 8583 message models and parsing utilities
- Client Module REST API gateway for web console interaction
- Server Module ISO 8583 message processing server
- Authorize Module Transaction authorization service
- Simulator Module Transaction simulation and testing
- Console (Web UI) Web-based management interface

## Prerequisites

- Java 17+
- Maven 3.6+
- NodeJS 20+
- Apache Kafka
- Docker/K8s (optional)
- Postgres DB (optional)
- Grafana (optional)
- Prometheus (optional)
- Tempo (optional)
- Open telemetry (optional)
- Grafana Faro (optional)

## Usage
**1. Localhost deployment:**
```bash
# Start Zookeeper
./zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka
./kafka-server-start.sh config/server.properties

# Start Server
cd source/server
mvn spring-boot:run

# Start Client
cd source/client
mvn spring-boot:run

# Start Authorize
cd source/authorize
mvn spring-boot:run

# Start Console
cd source/console
npm run start

# Establish server/client connection via console
# For example: localhost:8583

# Start Simulator
cd source/simulator
mvn spring-boot:run

```

**2. K8s Deployment**


**3. Monitoring**


## ⚠️ Disclamer

This project is for demonstration purposes only.