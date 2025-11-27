# Controller Service Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CONTROLLER CONTROL PLANE                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐              │
│  │ Central         │    │ Connection      │    │ Scaling         │              │
│  │ Controller      │◄──►│ Registry        │◄──►│ Coordinator     │              │
│  │                 │    │ (etcd/Redis)    │    │                 │              │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘              │
│           │                       │                       │                     │
│           ▼                       ▼                       ▼                     │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐              │
│  │ Health Monitor  │    │ Config Manager  │    │ Event Bus       │              │
│  │                 │    │                 │    │ (Kafka)         │              │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
                    ▼               ▼               ▼
┌─────────────────────────┐ ┌─────────────────────────┐ ┌─────────────────────────┐
│     CLIENT SIDE         │ │     SERVER SIDE         │ │     CONSOLE SIDE        │
├─────────────────────────┤ ├─────────────────────────┤ ├─────────────────────────┤
│ ┌─────────────────────┐ │ │ ┌─────────────────────┐ │ │ ┌─────────────────────┐ │
│ │ Client Controller   │ │ │ │ Server Controller   │ │ │ │ Console Controller  │ │
│ │ (Sidecar)           │ │ │ │ (Sidecar)           │ │ │ │ (Embedded)          │ │
│ │                     │ │ │ │                     │ │ │ │                     │ │
│ │ • Connection Mgmt   │ │ │ │ • Connection Pool   │ │ │ │ • UI Coordination   │ │
│ │ • Health Checks     │ │ │ │ • Load Balancing    │ │ │ │ • Status Dashboard  │ │
│ │ • Failover Logic    │ │ │ │ • Session Affinity  │ │ │ │ • Control Commands  │ │
│ └─────────────────────┘ │ │ └─────────────────────┘ │ │ └─────────────────────┘ │
│           │             │ │           │             │ │           │             │
│           ▼             │ │           ▼             │ │           ▼             │
│ ┌─────────────────────┐ │ │ ┌─────────────────────┐ │ │ ┌─────────────────────┐ │
│ │ Client Instance 1   │ │ │ │ Server Instance 1   │ │ │ │ Console Web UI      │ │
│ │ Client Instance 2   │ │ │ │ Server Instance 2   │ │ │ │                     │ │
│ │ Client Instance N   │ │ │ │ Server Instance N   │ │ │ │                     │ │
│ └─────────────────────┘ │ │ └─────────────────────┘ │ │ └─────────────────────┘ │
└─────────────────────────┘ └─────────────────────────┘ └─────────────────────────┘
            │                           │                           │
            └───────────────┬───────────────────────────────────────┘
                           │
                           ▼
            ┌─────────────────────────────────────────┐
            │         KAFKA MESSAGE BUS               │
            │                                         │
            │ Topics:                                 │
            │ • controller-events                     │
            │ • connection-status                     │
            │ • scaling-commands                      │
            │ • health-reports                        │
            └─────────────────────────────────────────┘
```

## Controller Components

### Central Controller
- **Global orchestration** of all client-server connections
- **Scaling decisions** based on load metrics
- **Configuration management** across all instances

### Client Controller (Sidecar)
- **Connection management** for local client instance
- **Health monitoring** via echo messages
- **Failover coordination** when connections fail

### Server Controller (Sidecar)
- **Connection pool management** for incoming connections
- **Load balancing** across server instances
- **Session affinity** maintenance

### Connection Registry
- **Centralized state store** (etcd/Redis)
- **Real-time connection topology**
- **Connection metadata and health status**

## Communication Flow

```
1. Client Controller registers connection intent
   Client Controller → Central Controller → Connection Registry

2. Central Controller assigns server instance
   Connection Registry → Central Controller → Server Controller

3. Connection established and monitored
   Client ←→ Server (ISO 8583 socket)
   Client Controller ←→ Server Controller (health sync)

4. Scaling event triggered
   HPA → Central Controller → Scaling Coordinator

5. Graceful connection migration
   Scaling Coordinator → Client/Server Controllers → Connection migration
```

## Benefits

- **Zero-downtime scaling**: Graceful connection migration
- **Automatic failover**: Health-based connection rerouting  
- **Centralized control**: Single point for connection management
- **Observability**: Complete connection topology visibility


### Key Solutions:
1. Multi-Layer Detection (5-10 seconds)
ISO Socket Health: Echo messages every 30s

Network Path Health: TCP keepalive every 10s

Controller Heartbeat: Management network every 5s

2. Separate Control Network
Management VLAN: Controller communication isolated from data network

Out-of-band monitoring: Survives primary network failures

Cross-site connectivity: Direct DR communication

3. Automatic Recovery
Proactive signoff: Controller sends proper disconnect to server

DR site activation: Automatic failover to backup network/site

State preservation: Transaction context maintained during switch

4. Geographic Resilience
Multi-site deployment: Primary + DR sites

Network diversity: Different ISPs/carriers

Automatic switchover: No manual intervention

Real-World Banking Benefits:
Sub-10 Second Detection: Faster than manual monitoring
Zero Transaction Loss: State preserved during failover
24/7 Automatic Recovery: No weekend/night manual intervention
Regulatory Compliance: Proper connection cleanup and audit trails