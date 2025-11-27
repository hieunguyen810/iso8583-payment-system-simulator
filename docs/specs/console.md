# Console Module Specification

## Overview

The Console module provides a web-based user interface for managing ISO 8583 connections and sending messages. Built with React, it serves as the primary interface for operators to interact with the ISO 8583 system.

## Architecture

### Technology Stack
- **Frontend**: React 18.2.0
- **Build Tool**: Create React App
- **Web Server**: Nginx (in production)
- **Observability**: Grafana Faro SDK
- **Container**: Docker with multi-stage build

### Port Configuration
- **Development**: 3000 (React dev server)
- **Production**: 80 (Nginx)

## Features

### 1. Connection Management
- **Add Connections**: Create new ISO 8583 server connections
- **View Connections**: Display all configured connections with status
- **Connect/Disconnect**: Manage connection states
- **Remove Connections**: Delete unused connections

### 2. Message Operations
- **Send Messages**: Transmit ISO 8583 messages to connected servers
- **Echo Testing**: Send echo messages for connectivity testing
- **Sample Messages**: Pre-configured message templates
- **Real-time Console**: Live output of all operations

### 3. Observability Integration
- **Grafana Faro**: Web application monitoring and tracing
- **Event Tracking**: User actions and system events
- **Error Logging**: Automatic error capture and reporting

## API Integration

### Base URL Configuration
```javascript
const API_URL = window.APP_CONFIG?.API_BASE_URL || 'http://localhost:8081/api/iso8583';
```

### Endpoints Used
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/connections` | List all connections |
| POST | `/connections` | Create new connection |
| DELETE | `/connections/{id}` | Remove connection |
| POST | `/connections/{id}/connect` | Establish connection |
| POST | `/connections/{id}/disconnect` | Close connection |
| POST | `/connections/{id}/send` | Send ISO message |
| POST | `/connections/{id}/echo` | Send echo test |

## Configuration

### Environment Variables
```bash
API_BASE_URL=http://localhost:8081/api/iso8583
FARO_URL=https://faro-collector-prod-ap-southeast-1.grafana.net/collect/<app_key>
FARO_ENABLED=false
ENVIRONMENT=development
```

### Runtime Configuration
Configuration is loaded via `/config.js` with fallback to defaults:
```javascript
window.APP_CONFIG = {
    API_BASE_URL: 'http://localhost:8081/api/iso8583',
    FARO_URL: 'https://faro-collector-prod-ap-southeast-1.grafana.net/collect/<app_key>',
    FARO_ENABLED: false,
    ENVIRONMENT: 'development'
};
```

## Sample Messages

The console includes pre-configured ISO 8583 message templates:

### Authorization Request (0100)
```
MTI=0100|2=4000123456789012|3=000000|4=000000001000|7=0101120000|11=000001|12=120000|13=0101|18=5999|22=012|25=00|37=000000000001|41=TERM001 |42=MERCHANT001    |49=840
```

### Financial Transaction (0200)
```
MTI=0200|2=4000123456789012|3=000000|4=000000001000|7=0101120000|11=000001|12=120000|13=0101|18=5999|22=012|25=00|37=000000000001|41=TERM001 |42=MERCHANT001    |49=840
```

### Network Management (0800)
```
MTI=0800|7=0101120000|11=000001|12=120000|13=0101|70=301
```

### Network Management Response (0900)
```
MTI=0900|7=0101120000|11=000001|12=120000|13=0101|70=301
```

## Development

### Local Development
```bash
cd source/console
npm install
npm start
```

### Build for Production
```bash
npm run build
```

### Testing
```bash
npm test
```

## Deployment

### Docker Build
```bash
cd source/console
docker build -t iso8583-console:latest .
```

### Kubernetes Deployment
```bash
kubectl apply -f k8s/console-configmap.yaml
kubectl apply -f k8s/console-deployment.yaml
kubectl apply -f k8s/console-service.yaml
kubectl apply -f k8s/console-ingress.yaml
```

### Resource Requirements
- **Memory**: 64Mi (request) / 128Mi (limit)
- **CPU**: 50m (request) / 100m (limit)

## User Interface

### Main Sections

#### 1. Connection Management Panel
- **Add New Connection**: Form for creating connections
- **Active Connections**: Grid view of all connections
- **Connection Actions**: Connect, disconnect, remove buttons

#### 2. Message Operations Panel
- **Target Selection**: Dropdown for connection selection
- **Message Input**: Textarea for ISO 8583 message content
- **Action Buttons**: Send message, echo test, samples
- **Console Output**: Real-time operation logs

### Console Output Types
- **Info** (🔄): General information messages
- **Success** (✅): Successful operations
- **Error** (❌): Error messages and failures
- **Request** (📨): Outgoing message content
- **Response** (📬): Incoming response content

## Monitoring and Observability

### Grafana Faro Integration
```javascript
// Event tracking examples
faro.api.pushEvent('connection_add_attempt', { connectionId, host, port });
faro.api.pushEvent('message_sent', { connectionId, mti });
faro.api.pushError(new Error(message));
faro.api.pushLog([message], { level: type });
```

### Tracked Events
- Connection lifecycle (add, connect, disconnect, remove)
- Message operations (send attempts, success, failures)
- User interactions and errors

## Security Considerations

### Input Validation
- Connection parameters validation
- Message content sanitization
- XSS prevention through React's built-in protection

### Network Security
- CORS configuration required for API access
- HTTPS recommended for production deployment
- Environment-specific configuration isolation

## Troubleshooting

### Common Issues

#### Connection Failures
- Verify client API service is running on port 8081
- Check network connectivity between console and client
- Validate connection parameters (host, port)

#### Message Send Failures
- Ensure connection is established before sending
- Verify message format follows ISO 8583 standard
- Check server logs for processing errors

#### Configuration Issues
- Verify `/config.js` is properly loaded
- Check environment variables in Kubernetes ConfigMap
- Validate API base URL accessibility

### Debug Mode
Enable browser developer tools to view:
- Network requests to client API
- Console logs and errors
- Faro telemetry data (if enabled)

## Future Enhancements

### Planned Features
- Message history and replay functionality
- Advanced message builder with field validation
- Real-time connection status monitoring
- Bulk message operations
- Export/import connection configurations
- Enhanced error reporting and diagnostics