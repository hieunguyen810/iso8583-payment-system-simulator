# Simulator Module

Flexible ISO 8583 transaction simulator with multiple testing modes.

## Modes

### 1. SCHEDULED (Default)
Regular interval transactions
```properties
simulator.mode=SCHEDULED
simulator.scheduled.interval-ms=15000
```

### 2. LOAD_TEST
Continuous load testing
```properties
simulator.mode=LOAD_TEST
simulator.load-test.threads-per-second=10
simulator.load-test.duration-seconds=60
```

### 3. SPIKE
Spike testing with normal and peak loads
```properties
simulator.mode=SPIKE
simulator.spike.normal-tps=5
simulator.spike.spike-tps=100
simulator.spike.spike-duration-seconds=30
```

### 4. MANUAL
Manual control via REST API
```properties
simulator.mode=MANUAL
```

## REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/simulator/send` | POST | Send single transaction |
| `/api/simulator/load-test/start` | POST | Start load test (manual mode) |
| `/api/simulator/spike-test/start` | POST | Start spike test (manual mode) |
| `/api/simulator/config` | GET | Get current configuration |
| `/api/simulator/status` | GET | Get simulator status |

## Examples

### Load Test Configuration
```properties
simulator.mode=LOAD_TEST
simulator.load-test.threads-per-second=50
simulator.load-test.duration-seconds=120
simulator.load-test.max-concurrent-threads=200
```

### Spike Test Configuration
```properties
simulator.mode=SPIKE
simulator.spike.normal-tps=10
simulator.spike.spike-tps=200
simulator.spike.spike-duration-seconds=60
simulator.spike.interval-between-spikes-seconds=600
```

### Manual Control
```bash
# Send single transaction
curl -X POST http://localhost:8084/api/simulator/send

# Start load test
curl -X POST http://localhost:8084/api/simulator/load-test/start

# Check status
curl http://localhost:8084/api/simulator/status
```