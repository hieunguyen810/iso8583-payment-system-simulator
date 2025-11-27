### Future Improvements
#### Code
- Monitor Client: count success/failed transaction
- Test cases: client, simulator, authorize
- Client -> Server: File configuration (auto re-connect)
- Simulator: support multiple request type
- Profiling
- Tracing: OTLP

#### K8s
- Helm chart
- Client on K8s

#### Deployment
- Autoscaling: authorize (cpu, ram, transaction/second: metric)
- Simulator: load test
- Server: scale up

#### CICD
- 1 job/ 1 module
- Build Front End
- Security scan
- Code coverage
- Quality Gate
- Push image to repo
- Deploy

#### Observability
- Loki: receive logs
- Alloy: receive trace, profile, get app logs
- Grafana
- Prometheus
- Tempo
- Spring Boot Actuator


#### Database
- Postgresql
- Authorize -> DB
- DB design
- DB monitor


#### Database CICD
