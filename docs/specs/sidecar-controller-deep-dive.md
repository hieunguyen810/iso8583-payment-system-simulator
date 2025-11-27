# Sidecar Controller Deep Dive

## Sidecar Controller Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              POD ARCHITECTURE                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────────┐ │
│  │                            CLIENT POD                                       │ │
│  ├─────────────────────────────────────────────────────────────────────────────┤ │
│  │ ┌─────────────────────────────┐  ┌─────────────────────────────────────────┐ │ │
│  │ │     CLIENT APPLICATION      │  │        CLIENT SIDECAR CONTROLLER       │ │ │
│  │ │                             │  │                                         │ │ │
│  │ │ • ISO 8583 Client           │◄─┤ • Connection Lifecycle Manager         │ │ │
│  │ │ • Socket Management         │  │ • Health Monitor & Reporter             │ │ │
│  │ │ • Message Processing        │  │ • Failover Executor                     │ │ │
│  │ │ • Connection State          │  │ • Configuration Manager                 │ │ │
│  │ │                             │  │ • Metrics Collector                     │ │ │
│  │ │ Port: 8583 (ISO)           │  │ • Command Processor                     │ │ │
│  │ │ Port: 8081 (REST API)      │  │                                         │ │ │
│  │ └─────────────────────────────┘  │ Port: 8080 (Control API)               │ │ │
│  │              │                   │ Port: 8082 (Metrics)                   │ │ │
│  │              │ Localhost         │ Port: 8083 (Health)                    │ │ │
│  │              ▼                   └─────────────────────────────────────────┘ │ │
│  │ ┌─────────────────────────────────────────────────────────────────────────┐ │ │
│  │ │                        SHARED VOLUME                                    │ │ │
│  │ │ • Connection State Files    • Configuration Files                       │ │ │
│  │ │ • Health Check Results      • Failover Scripts                          │ │ │
│  │ │ • Metrics Data             • Log Files                                  │ │ │
│  │ └─────────────────────────────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
│                                        │                                         │
│                                        ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────┐ │
│  │                      EXTERNAL COMMUNICATION                                 │ │
│  ├─────────────────────────────────────────────────────────────────────────────┤ │
│  │ ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │ │
│  │ │ Central         │  │ Redis Cluster   │  │ Kafka Event     │              │ │
│  │ │ Controller      │  │ (State Store)   │  │ Bus             │              │ │
│  │ │ (Control EKS)   │  │                 │  │                 │              │ │
│  │ └─────────────────┘  └─────────────────┘  └─────────────────┘              │ │
│  └─────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Sidecar Controller Core Functions

### 1. Connection Lifecycle Manager

```go
type ConnectionLifecycleManager struct {
    connections map[string]*ConnectionState
    mutex       sync.RWMutex
    eventChan   chan ConnectionEvent
}

// Core responsibilities:
func (clm *ConnectionLifecycleManager) ManageConnection(connID string) {
    // 1. Connection Registration
    clm.registerConnection(connID)
    
    // 2. Connection Establishment
    clm.establishConnection(connID)
    
    // 3. Connection Monitoring
    go clm.monitorConnection(connID)
    
    // 4. Connection Cleanup
    defer clm.cleanupConnection(connID)
}

// Connection state management
type ConnectionState struct {
    ID              string
    Status          ConnectionStatus  // CONNECTING, ACTIVE, DEGRADED, FAILED
    LastHeartbeat   time.Time
    HealthScore     int              // 0-100
    FailoverTarget  string
    Metadata        map[string]string
}
```

### 2. Health Monitor & Reporter

```go
type HealthMonitor struct {
    checkInterval   time.Duration
    reportInterval  time.Duration
    healthChecks    []HealthCheck
    reporter        HealthReporter
}

// Multi-layer health monitoring
func (hm *HealthMonitor) StartMonitoring() {
    // Layer 1: Application Health (every 10s)
    go hm.monitorApplicationHealth()
    
    // Layer 2: Network Health (every 5s)
    go hm.monitorNetworkHealth()
    
    // Layer 3: ISO 8583 Protocol Health (every 30s)
    go hm.monitorProtocolHealth()
    
    // Layer 4: Resource Health (every 15s)
    go hm.monitorResourceHealth()
}

// Health check implementations
func (hm *HealthMonitor) monitorApplicationHealth() {
    for {
        // Check if client application is responsive
        resp := hm.callLocalAPI("http://localhost:8081/actuator/health")
        
        // Check socket connection status
        connStatus := hm.checkSocketConnection()
        
        // Update health score
        healthScore := hm.calculateHealthScore(resp, connStatus)
        
        // Report to central controller
        hm.reportHealth(healthScore)
        
        time.Sleep(hm.checkInterval)
    }
}

func (hm *HealthMonitor) monitorProtocolHealth() {
    for {
        // Send ISO 8583 echo message (0800)
        echoResult := hm.sendEchoMessage()
        
        // Measure response time
        responseTime := echoResult.ResponseTime
        
        // Check message format compliance
        compliance := hm.validateMessageFormat(echoResult.Response)
        
        // Update protocol health metrics
        hm.updateProtocolMetrics(responseTime, compliance)
        
        time.Sleep(30 * time.Second)
    }
}
```

### 3. Failover Executor

```go
type FailoverExecutor struct {
    strategies      map[string]FailoverStrategy
    currentStrategy string
    executor        CommandExecutor
}

// Failover execution logic
func (fe *FailoverExecutor) ExecuteFailover(trigger FailoverTrigger) error {
    // 1. Assess failover scenario
    scenario := fe.assessFailoverScenario(trigger)
    
    // 2. Select appropriate strategy
    strategy := fe.selectStrategy(scenario)
    
    // 3. Execute pre-failover actions
    if err := fe.preFailoverActions(strategy); err != nil {
        return err
    }
    
    // 4. Execute main failover
    if err := fe.executeMainFailover(strategy); err != nil {
        return err
    }
    
    // 5. Execute post-failover actions
    return fe.postFailoverActions(strategy)
}

// Failover strategies
type FailoverStrategy interface {
    Execute(context FailoverContext) error
}

// Network failure strategy
type NetworkFailureStrategy struct{}
func (nfs *NetworkFailureStrategy) Execute(ctx FailoverContext) error {
    // 1. Graceful connection shutdown
    nfs.gracefulShutdown(ctx.ConnectionID)
    
    // 2. Activate DR connection
    nfs.activateDRConnection(ctx.DRTarget)
    
    // 3. Migrate connection state
    nfs.migrateConnectionState(ctx.ConnectionID, ctx.DRTarget)
    
    // 4. Update routing tables
    nfs.updateRoutingTables(ctx.ConnectionID, ctx.DRTarget)
    
    return nil
}

// Application failure strategy
type ApplicationFailureStrategy struct{}
func (afs *ApplicationFailureStrategy) Execute(ctx FailoverContext) error {
    // 1. Restart application container
    afs.restartApplication(ctx.PodName)
    
    // 2. Restore connection state
    afs.restoreConnectionState(ctx.ConnectionID)
    
    // 3. Re-establish connections
    afs.reestablishConnections(ctx.ConnectionID)
    
    return nil
}
```

### 4. Configuration Manager

```go
type ConfigurationManager struct {
    configStore     ConfigStore
    watchers        []ConfigWatcher
    currentConfig   *Configuration
    mutex          sync.RWMutex
}

// Dynamic configuration management
func (cm *ConfigurationManager) StartConfigWatch() {
    // Watch for configuration changes from central controller
    go cm.watchCentralConfig()
    
    // Watch for local configuration file changes
    go cm.watchLocalConfig()
    
    // Watch for environment variable changes
    go cm.watchEnvironmentConfig()
}

func (cm *ConfigurationManager) watchCentralConfig() {
    for {
        // Poll central controller for config updates
        newConfig := cm.fetchConfigFromCentral()
        
        if cm.configChanged(newConfig) {
            // Validate new configuration
            if err := cm.validateConfig(newConfig); err != nil {
                log.Error("Invalid config received", err)
                continue
            }
            
            // Apply configuration changes
            cm.applyConfigChanges(newConfig)
            
            // Notify application of config changes
            cm.notifyApplication(newConfig)
        }
        
        time.Sleep(30 * time.Second)
    }
}

// Configuration hot-reload
func (cm *ConfigurationManager) applyConfigChanges(newConfig *Configuration) {
    cm.mutex.Lock()
    defer cm.mutex.Unlock()
    
    // Update connection parameters
    if newConfig.Connection != cm.currentConfig.Connection {
        cm.updateConnectionConfig(newConfig.Connection)
    }
    
    // Update health check intervals
    if newConfig.HealthCheck != cm.currentConfig.HealthCheck {
        cm.updateHealthCheckConfig(newConfig.HealthCheck)
    }
    
    // Update failover settings
    if newConfig.Failover != cm.currentConfig.Failover {
        cm.updateFailoverConfig(newConfig.Failover)
    }
    
    cm.currentConfig = newConfig
}
```

### 5. Command Processor

```go
type CommandProcessor struct {
    commandQueue    chan Command
    handlers        map[CommandType]CommandHandler
    executor        CommandExecutor
}

// Command processing loop
func (cp *CommandProcessor) StartProcessing() {
    for command := range cp.commandQueue {
        go cp.processCommand(command)
    }
}

func (cp *CommandProcessor) processCommand(cmd Command) {
    // Validate command
    if err := cp.validateCommand(cmd); err != nil {
        cp.sendCommandResponse(cmd.ID, CommandResponse{
            Status: "FAILED",
            Error:  err.Error(),
        })
        return
    }
    
    // Execute command
    handler := cp.handlers[cmd.Type]
    result := handler.Execute(cmd)
    
    // Send response
    cp.sendCommandResponse(cmd.ID, result)
}

// Command types and handlers
type CommandType string
const (
    CMD_ESTABLISH_CONNECTION CommandType = "ESTABLISH_CONNECTION"
    CMD_CLOSE_CONNECTION    CommandType = "CLOSE_CONNECTION"
    CMD_SEND_MESSAGE        CommandType = "SEND_MESSAGE"
    CMD_UPDATE_CONFIG       CommandType = "UPDATE_CONFIG"
    CMD_TRIGGER_FAILOVER    CommandType = "TRIGGER_FAILOVER"
    CMD_HEALTH_CHECK        CommandType = "HEALTH_CHECK"
)

// Connection establishment handler
type EstablishConnectionHandler struct{}
func (ech *EstablishConnectionHandler) Execute(cmd Command) CommandResponse {
    params := cmd.Parameters.(EstablishConnectionParams)
    
    // 1. Validate connection parameters
    if err := ech.validateParams(params); err != nil {
        return CommandResponse{Status: "FAILED", Error: err.Error()}
    }
    
    // 2. Establish connection via application
    connResult := ech.callApplication("POST", "/api/connections", params)
    
    // 3. Register connection with central controller
    ech.registerWithCentral(params.ConnectionID, connResult)
    
    // 4. Start monitoring the connection
    ech.startMonitoring(params.ConnectionID)
    
    return CommandResponse{
        Status: "SUCCESS",
        Data:   connResult,
    }
}
```

### 6. Metrics Collector

```go
type MetricsCollector struct {
    metrics         map[string]Metric
    collectors      []MetricCollector
    exporters       []MetricExporter
    collectionInterval time.Duration
}

// Metrics collection and export
func (mc *MetricsCollector) StartCollection() {
    ticker := time.NewTicker(mc.collectionInterval)
    
    for range ticker.C {
        // Collect application metrics
        appMetrics := mc.collectApplicationMetrics()
        
        // Collect connection metrics
        connMetrics := mc.collectConnectionMetrics()
        
        // Collect health metrics
        healthMetrics := mc.collectHealthMetrics()
        
        // Collect resource metrics
        resourceMetrics := mc.collectResourceMetrics()
        
        // Aggregate and export
        allMetrics := mc.aggregateMetrics(
            appMetrics, connMetrics, healthMetrics, resourceMetrics,
        )
        
        mc.exportMetrics(allMetrics)
    }
}

// Key metrics collected
func (mc *MetricsCollector) collectConnectionMetrics() map[string]Metric {
    return map[string]Metric{
        "connection_count":           mc.getActiveConnectionCount(),
        "connection_establishment_time": mc.getConnectionEstablishmentTime(),
        "connection_failure_rate":    mc.getConnectionFailureRate(),
        "message_throughput":         mc.getMessageThroughput(),
        "message_latency":           mc.getMessageLatency(),
        "echo_response_time":        mc.getEchoResponseTime(),
        "failover_count":            mc.getFailoverCount(),
        "failover_duration":         mc.getFailoverDuration(),
    }
}
```

## Sidecar Controller Communication Patterns

### 1. Local Communication (Same Pod)
```yaml
# Sidecar ↔ Application communication
Method: HTTP REST API over localhost
Endpoints:
  - GET  /health                    # Health check
  - POST /connections               # Create connection
  - GET  /connections/{id}          # Get connection status
  - POST /connections/{id}/send     # Send message
  - DELETE /connections/{id}        # Close connection
  - PUT  /config                    # Update configuration

# Shared volume communication
Files:
  - /shared/connection-state.json   # Connection state
  - /shared/health-status.json      # Health status
  - /shared/config.yaml            # Configuration
  - /shared/metrics.json           # Metrics data
```

### 2. External Communication
```yaml
# Sidecar → Central Controller
Method: HTTP REST API
Endpoint: http://central-controller.bank-control-eks:8080/api/v1/
Actions:
  - POST /connections/register      # Register connection
  - PUT  /connections/{id}/health   # Report health
  - POST /connections/{id}/failover # Request failover
  - GET  /config                   # Fetch configuration

# Sidecar → Redis (State Store)
Method: Redis Protocol
Actions:
  - SET connection:{id}:state       # Update connection state
  - GET connection:{id}:config      # Get configuration
  - PUBLISH health-events           # Publish health events
  - SUBSCRIBE failover-commands     # Subscribe to commands

# Sidecar → Kafka (Event Bus)
Method: Kafka Protocol
Topics:
  - Produce: health-reports         # Health status reports
  - Produce: connection-events      # Connection lifecycle events
  - Consume: failover-commands      # Failover instructions
  - Consume: config-updates         # Configuration updates
```

## Deployment Configuration

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: iso8583-client-with-sidecar
spec:
  template:
    spec:
      containers:
      # Main application container
      - name: client-app
        image: iso8583-client:latest
        ports:
        - containerPort: 8583
          name: iso8583
        - containerPort: 8081
          name: rest-api
        volumeMounts:
        - name: shared-data
          mountPath: /shared
        
      # Sidecar controller container
      - name: client-controller
        image: iso8583-controller:latest
        ports:
        - containerPort: 8080
          name: control-api
        - containerPort: 8082
          name: metrics
        - containerPort: 8083
          name: health
        env:
        - name: CONTROLLER_MODE
          value: "sidecar"
        - name: APPLICATION_ENDPOINT
          value: "http://localhost:8081"
        - name: CENTRAL_CONTROLLER_ENDPOINT
          value: "http://central-controller.bank-control-eks:8080"
        - name: REDIS_ENDPOINT
          value: "redis-cluster.bank-control-eks:6379"
        - name: KAFKA_BROKERS
          value: "kafka-cluster.bank-control-eks:9092"
        volumeMounts:
        - name: shared-data
          mountPath: /shared
        
      volumes:
      - name: shared-data
        emptyDir: {}
```

## Key Benefits of Sidecar Pattern

**Separation of Concerns**: Application focuses on business logic, sidecar handles infrastructure
**Independent Scaling**: Sidecar can be updated without touching application
**Consistent Management**: Same sidecar pattern across all services
**Local Communication**: Fast localhost communication between containers
**Shared State**: Shared volume for state synchronization
**Observability**: Centralized metrics and health monitoring
**Resilience**: Automatic failover and recovery capabilities