#!/bin/bash

# ISO 8583 Demo - ActiveMQ Startup Script
echo "Starting ISO 8583 Demo with ActiveMQ..."

# Set JVM options for all Java processes
export JAVA_OPTS="-Xms64m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Start infrastructure services
echo "Starting infrastructure services..."
docker-compose -f docker-compose-activemq.yml up -d

# Wait for services to be ready
echo "Waiting for services to start..."
sleep 30

# Start applications with ActiveMQ profile
echo "Starting Server..."
cd source/server
mvn spring-boot:run -Dspring-boot.run.profiles=lightweight -Dspring-boot.run.jvmArguments="$JAVA_OPTS" &
SERVER_PID=$!

echo "Starting Client with ActiveMQ..."
cd ../client
mvn spring-boot:run -Dspring-boot.run.profiles=activemq -Dspring-boot.run.jvmArguments="$JAVA_OPTS" &
CLIENT_PID=$!

echo "Starting Authorize with ActiveMQ..."
cd ../authorize
mvn spring-boot:run -Dspring-boot.run.profiles=activemq -Dspring-boot.run.jvmArguments="-Xms32m -Xmx128m -XX:+UseG1GC" &
AUTHORIZE_PID=$!

echo "Starting Console..."
cd ../console
npm start &
CONSOLE_PID=$!

echo "Starting Simulator..."
cd ../simulator
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms32m -Xmx128m -XX:+UseG1GC" &
SIMULATOR_PID=$!

echo ""
echo "All services started with ActiveMQ!"
echo "Server: http://localhost:8080"
echo "Client: http://localhost:8081"
echo "Authorize: http://localhost:8082"
echo "Console: http://localhost:3001"
echo "Simulator: http://localhost:8084"
echo "ActiveMQ Console: http://localhost:8161 (admin/admin)"
echo "Grafana: http://localhost:3000"
echo ""

# Create stop script
cat > ../stop-activemq.sh << 'EOF'
#!/bin/bash
echo "Stopping ISO 8583 Demo services..."
pkill -f "spring-boot:run"
pkill -f "npm start"
docker-compose -f docker-compose-activemq.yml down
echo "All services stopped."
EOF

chmod +x ../stop-activemq.sh

echo "Press Ctrl+C to stop all services..."
trap 'kill $SERVER_PID $CLIENT_PID $AUTHORIZE_PID $CONSOLE_PID $SIMULATOR_PID; docker-compose -f docker-compose-activemq.yml down; exit' INT
wait