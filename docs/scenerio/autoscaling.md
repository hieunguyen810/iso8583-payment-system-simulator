# Auto Scaling

## Server Autoscaling: HPA

- More request (simulator) --> Server
- Based on metrics, server controller sidecar --> Central controller --> Scale Up request
- Central controller receive scale up request --> client controler sidecar 
- Scale up Client --> reponse (client scale up)
- Server scale up --> finish scale up
- Client/server establish connection
- Server set status to ready
- Simulator send request to server

**Metrics**
- messages_per_second
- active_connections
- socket_buffer_utilization
- response_latency
- message_queue_depth
- cpu_usage_percentage
- memory_usage_percentage