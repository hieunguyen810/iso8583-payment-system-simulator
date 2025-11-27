# Demo ISO 8583 Server and Client

## Project struture
```
src/main/java/com/example/demo/
├── DemoApplication.java
├── server/
│   └── Iso8583Server.java
├── client/  
│   └── Iso8583Client.java
└── model/
    └── Iso8583Message.java
```

## Build and start

1. Build the JAR
```
mvn clean package
```
2. Run server in one terminal
```
java -jar target/demo-0.0.1-SNAPSHOT.jar server
```
3. Run client in another terminal  
```
java -jar target/demo-0.0.1-SNAPSHOT.jar client
```
## How to run test cases
1. Run all test
```
mvn test
```
2. Run only the main integration test
```
mvn test -Dtest=DemoApplicationTests
```
3. Run only unit tests
```
mvn test -Dtest=Iso8583MessageTest
```
4. Run tests with detailed output
```
mvn test -Dtest=DemoApplicationTests#shouldSendAndReceiveIso8583Message
```
5. Run tests and generate report
```
mvn clean test surefire-report:report
```

## What These Tests Cover:
*Integration Tests (DemoApplicationTests):*

- Context Loading: Ensures Spring Boot starts correctly
- Server Connectivity: Tests that server accepts connections
- Message Exchange: Tests complete ISO 8583 request/response flow
- Concurrency: Tests multiple simultaneous connections
- Error Handling: Tests server behavior with invalid messages
- Connection Management: Tests timeout and connection handling
- Mode Switching: Tests different application modes

*Unit Tests (Iso8583MessageTest):*

- Message Creation: Tests MTI setting/getting
- Field Management: Tests adding/retrieving fields
- String Serialization: Tests message-to-string conversion
- Edge Cases: Tests empty messages, non-existent fields
- Field Updates: Tests updating existing fields