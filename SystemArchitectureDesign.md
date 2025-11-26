# SYSTEM ARCHITECTURE DESIGN

### 1. Observability

**Front-End**

*Grafana Faro*

Set ```FARO_ENABLED: "true"``` to enable Grafana Faro.
Faro will send data to Grafana Cloud. (or Alloy for local deployment)

**Spring Boot** *Actuator*
##### Server
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Transaction Metrics:
    - http://localhost:8080/actuator/metrics/iso8583.transactions.successful
    - http://localhost:8080/actuator/metrics/iso8583.transactions.failed

- When server receive the transaction from *simulator*, the simer start (using field 37 as key). The timer end when a 210 resonse is processed
- Transaction successed: response time < ```iso8583.transaction.timeout``` (usually 7 seconds)
- Transaction failed: Invalid message or Timeout

### 2. Deployment

**High Availability**

This project focus on the high availability in the Issuer Side (usually a Bank).

##### Scale Authorize service
- All services use ```group-id=authorize-service```
- Topic has 3 partitions (support up to 3 consumer instance)
- Each partition assigned to different consumer instance
- Each message goes to only one consumer in the group

**Kubernetes** K3s for testing purpose

**High Availability ISO 8583 connection**
#### Prerequisites
- Client and Server must maintain the persistent, bi-directional connection.
- Server and Client should running in multiple instances to increase high availability and able to upgrade without any downtime.
#### Solutions
1. **Client - Server connection**: stateful
- On Client Side:
    - Deploy on kubernetes: multi primary cluster setup with Istio.
    - Kafka Cluster
    - When Client receive messages, produce to Kafka. 
    - Authorize consume Kafka topics.
    - Client should connect with multiple server node (in different regions).
    - Client need to send echo message every minutes (or less) to server. If server did not receive message --> disconnect to this instance (send to another available client instance)
    - Authorize module: stateless
    - Client module: stateful
    - Console module can connect and control multiple client.
- On Server side:
    - Deploy on kubernetes: multi primary cluster setup with Istio.
    - Kafka Cluster
    - When server receive a connect message from client -> produce Kafka, set current status is available. Simulator consume this topic, send message to available server only.
    - When server receive a disconnect message from client or did not receive echo message after 1 minute -> produce Kafka, set current status is unavailable. Simulator consume this topic, stop send message to this server instance.

2. **Simulation to Server connection**: stateless
    - Simulator connect to server headless service (by dns name)
    - Simulator (grpc client side) must config loadbalancing policy

3. **Client to Kafka connection**: stateless
    - 

4. **Kafka to Authorize connection**: stateless
    - All authorize instances must use the same groud-id=authorize-service
    - Partition distribution: topic has 3 partitions, supporting up to 3 consumer instances
    - Each message goes to only one consumer in the group


#### Downtime Scenerio and Resolved


#### Maintainance

**Kubernetes**

### 3. CICD

**Jenkins**
