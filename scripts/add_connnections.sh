# Add connection
curl -X POST http://localhost:8080/api/iso8583/connections \
  -H "Content-Type: application/json" \
  -d '{
    "connectionId": "server1",
    "host": "localhost",
    "port": 8583
  }'

# Connect
curl -X POST http://localhost:8080/api/iso8583/connections/server1/connect

# Disconnect
curl -X POST http://localhost:8080/api/iso8583/connections/server1/disconnect

# Check status
curl -X GET http://localhost:8080/api/iso8583/connections
