# Cross-Cluster Controller Communication

## Controller Communication Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    CONTROLLER COMMUNICATION LAYERS                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────┐     │
│  │      BANK CONTROL EKS           │    │     SCHEME CONTROL EKS          │     │
│  │    (bank-control-eks)           │    │   (scheme-control-eks)          │     │
│  ├─────────────────────────────────┤    ├─────────────────────────────────┤     │
│  │ ┌─────────────────────────────┐ │    │ ┌─────────────────────────────┐ │     │
│  │ │ Central Controller          │ │    │ │ Central Controller          │ │     │
│  │ │                             │ │◄──►│ │                             │ │     │
│  │ │ • Connection Registry       │ │    │ │ • Connection Registry       │ │     │
│  │ │ • Health Monitor            │ │    │ │ • Health Monitor            │ │     │
│  │ │ • Failover Coordinator      │ │    │ │ • Failover Coordinator      │ │     │
│  │ └─────────────────────────────┘ │    │ └─────────────────────────────┘ │     │
│  └─────────────────────────────────┘    └─────────────────────────────────┘     │
│              │                                          │                       │
│              │ ┌─────────────────────────────────────┐  │                       │
│              └►│     COMMUNICATION CHANNELS          │◄─┘                       │
│                ├─────────────────────────────────────┤                         │
│                │ 1. gRPC API (Direct)                │                         │
│                │ 2. Kafka Event Bus (Async)          │                         │
│                │ 3. Shared Redis (State Sync)        │                         │
│                │ 4. Kubernetes API (Cross-cluster)   │                         │
│                └─────────────────────────────────────┘                         │
│              │                                          │                       │
│              ▼                                          ▼                       │
│  ┌─────────────────────────────────┐    ┌─────────────────────────────────┐     │
│  │      BANK DATA EKS              │    │     SCHEME DATA EKS             │     │
│  │    (bank-iso8583-eks)           │    │   (scheme-iso8583-eks)          │     │
│  ├─────────────────────────────────┤    ├─────────────────────────────────┤     │
│  │ ┌─────────────────────────────┐ │    │ ┌─────────────────────────────┐ │     │
│  │ │ Client Controller (Sidecar) │ │    │ │ Server Controller (Sidecar) │ │     │
│  │ │                             │ │    │ │                             │ │     │
│  │ │ • Local Connection Mgmt     │ │    │ │ • Local Connection Pool     │ │     │
│  │ │ • Health Reporting          │ │    │ │ • Load Balancing            │ │     │
│  │ │ • Command Execution         │ │    │ │ • Session Management        │ │     │
│  │ └─────────────────────────────┘ │    │ └─────────────────────────────┘ │     │
│  │              │                  │    │              │                  │     │
│  │              ▼                  │    │              ▼                  │     │
│  │ ┌─────────────────────────────┐ │    │ ┌─────────────────────────────┐ │     │
│  │ │ Client Application Pods     │ │    │ │ Server Application Pods     │ │     │
│  │ │                             │ │    │ │                             │ │     │
│  │ │ • ISO 8583 Client           │ │◄──►│ │ • ISO 8583 Server           │ │     │
│  │ │ • Connection State          │ │    │ │ • Connection Handling       │ │     │
│  │ └─────────────────────────────┘ │    │ └─────────────────────────────┘ │     │
│  └─────────────────────────────────┘    └─────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Communication Mechanisms

### 1. gRPC API (Real-time Control)
```yaml
# Central Controller exposes gRPC API
apiVersion: v1
kind: Service
metadata:
  name: central-controller-grpc
  namespace: iso8583-control
spec:
  type: LoadBalancer
  ports:
  - port: 9090
    targetPort: 9090
    name: grpc
  selector:
    app: central-controller

# Cross-cluster gRPC communication
Bank Controller → Scheme Controller:
  Endpoint: scheme-controller.us-west-2.elb.amazonaws.com:9090
  Methods:
    - RegisterConnection(client_id, server_id)
    - ReportHealth(connection_id, status)
    - RequestFailover(connection_id, reason)
    - UpdateConnectionState(connection_id, state)
```

### 2. Kafka Event Bus (Asynchronous Events)
```yaml
# MSK Kafka Cluster (Cross-region replication)
Topics:
  - controller-events: Global controller coordination
  - connection-status: Real-time connection health
  - failover-commands: Failover orchestration
  - health-reports: Health monitoring data

# Event Flow Example:
1. Client Controller publishes: connection-lost event
2. Central Controllers consume: coordinate failover
3. Server Controller receives: stop-sending-to-client command
4. DR Controller activates: new-connection-established event
```

### 3. Shared Redis Cluster (State Synchronization)
```yaml
# ElastiCache Redis Global Datastore
Primary: us-east-1 (Bank region)
Replica: us-west-2 (Scheme region)

# Connection Registry Schema:
connections:{connection_id} = {
  client_id: "bank-client-1",
  server_id: "scheme-server-2", 
  status: "active|failed|migrating",
  last_heartbeat: timestamp,
  health_score: 0-100,
  failover_target: "backup-server-id"
}

# Real-time state sync across regions
Bank Controller writes → Redis Primary → Auto-replication → Scheme Controller reads
```

### 4. Kubernetes API (Cross-cluster Operations)
```yaml
# Cross-cluster service account and RBAC
apiVersion: v1
kind: ServiceAccount
metadata:
  name: cross-cluster-controller
  namespace: iso8583-control

# ClusterRole for cross-cluster access
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: cross-cluster-controller
rules:
- apiGroups: [""]
  resources: ["pods", "services", "endpoints"]
  verbs: ["get", "list", "watch", "create", "update", "patch"]
- apiGroups: ["apps"]
  resources: ["deployments", "replicasets"]
  verbs: ["get", "list", "watch", "update", "patch"]

# Cross-cluster kubeconfig
Bank Controller → Scheme EKS API:
  Endpoint: https://scheme-eks.us-west-2.eks.amazonaws.com
  Actions:
    - Scale server pods
    - Update service endpoints
    - Trigger rolling updates
```

## Connection Management Flow

### Normal Operation
```
1. Client Controller registers connection intent
   Client Controller → Redis → "connection-request" event

2. Central Controllers coordinate assignment
   Bank Central Controller ↔ Scheme Central Controller (gRPC)
   Decision: Assign client-1 to server-3

3. Connection establishment
   Client Controller → Client App → Server App ← Server Controller
   
4. Continuous health monitoring
   Client Controller → Redis (heartbeat every 5s)
   Server Controller → Redis (connection status)
```

### Failure Detection & Recovery
```
1. Network failure detected
   Client Controller: No response from server
   Redis: Heartbeat timeout detected
   
2. Cross-cluster coordination
   Bank Central Controller → gRPC → Scheme Central Controller
   Message: "connection-failed, client-1, server-3"
   
3. Failover orchestration
   Scheme Central Controller → Kubernetes API → Scale new server
   Bank Central Controller → Kafka → "activate-dr-client" event
   
4. New connection establishment
   DR Client Controller → New Server Controller
   Redis: Update connection registry with new mapping
```

## Implementation Details

### Central Controller gRPC Service
```go
// Cross-cluster controller communication
service ControllerService {
  rpc RegisterConnection(ConnectionRequest) returns (ConnectionResponse);
  rpc ReportHealth(HealthReport) returns (HealthAck);
  rpc RequestFailover(FailoverRequest) returns (FailoverResponse);
  rpc SyncState(StateSync) returns (StateSyncAck);
}

// Connection coordination
message ConnectionRequest {
  string client_id = 1;
  string preferred_server = 2;
  ConnectionMetadata metadata = 3;
}
```

### Sidecar Controller Communication
```yaml
# Sidecar controllers communicate with central controllers
Client Sidecar → Central Controller:
  Method: HTTP REST API
  Endpoint: http://central-controller.bank-control-eks:8080/api/v1/
  
Server Sidecar → Central Controller:
  Method: HTTP REST API  
  Endpoint: http://central-controller.scheme-control-eks:8080/api/v1/

# Local pod communication (same cluster)
Sidecar Controller → Application Pod:
  Method: Localhost HTTP/gRPC
  Endpoint: http://localhost:8081/control
```

## Benefits

**Real-time Coordination**: gRPC for immediate failover decisions
**Event-driven Architecture**: Kafka for loose coupling and scalability
**Consistent State**: Redis for synchronized connection registry
**Infrastructure Control**: Kubernetes API for pod/service management
**Network Independence**: Control plane survives data network failures
**Cross-region Resilience**: Multi-region controller deployment

This architecture enables controllers to manage connections across different EKS clusters through multiple redundant communication channels.