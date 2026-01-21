# REST Bridge (Java 11)

A Java Spring Boot microservice that provides a REST API bridge to Documentum REST Services endpoints.

> **Note:** This is the **Java 11** version of REST Bridge, compatible with environments that cannot run Java 17+. For environments with Java 17+ support, use [rest-bridge](https://github.com/tonychristie/rest-bridge) instead.

## Overview

REST Bridge enables non-browser applications (like the Documentum VS Code extension) to interact with Documentum repositories through Documentum REST Services. Unlike DFC Bridge which uses native DFC libraries, REST Bridge communicates with Documentum via the standard REST API.

### Architecture

```
Client Application (e.g., VS Code Extension)
           |
           | HTTP/REST
           v
    +--------------+
    | REST Bridge  |
    | (Spring Boot)|
    +--------------+
           |
           | HTTP/REST (WebClient)
           v
    +----------------------+
    | Documentum REST      |
    | Services             |
    +----------------------+
           |
           v
    +--------------+
    |  Repository  |
    +--------------+
```

### When to Use REST Bridge vs DFC Bridge

| Feature | REST Bridge | DFC Bridge |
|---------|-------------|------------|
| Connection type | HTTP to REST Services | Native DFC to Docbroker |
| Prerequisites | REST Services endpoint | DFC libraries installed |
| DQL support | Optional (can be disabled) | Always available |
| User/Group listing | Native REST endpoints | DQL queries |
| Deployment | Standalone JAR | Requires DFC installation |

### Java 11 vs Java 17 Version

| Feature | Java 11 Version | Java 17 Version |
|---------|-----------------|-----------------|
| Java requirement | Java 11+ | Java 17+ |
| Spring Boot | 2.7.x | 3.x |
| Jakarta namespace | javax.* | jakarta.* |
| Repository | rest-bridge-java11 | rest-bridge |

## Prerequisites

- **Java 11+** (tested with Java 11 LTS)
- **Maven 3.8+**
- **Access to Documentum REST Services endpoint**

No DFC installation required - REST Bridge is a pure Java application.

## Building

```bash
# Clone the repository
git clone https://github.com/tonychristie/rest-bridge-java11.git
cd rest-bridge-java11

# Build with Maven
mvn clean package

# The JAR will be in target/rest-bridge-1.0.0-SNAPSHOT.jar
```

## Running

### Using Maven (development)

```bash
mvn spring-boot:run
```

### Using the JAR

```bash
java -jar target/rest-bridge-1.0.0-SNAPSHOT.jar
```

### Specifying a custom port

```bash
java -jar target/rest-bridge-1.0.0-SNAPSHOT.jar --server.port=8080
```

Or via environment variable:
```bash
export SERVER_PORT=8080
java -jar target/rest-bridge-1.0.0-SNAPSHOT.jar
```

## Configuration

Application configuration is in `src/main/resources/application.yml`:

```yaml
server:
  port: 9877

rest-bridge:
  timeout-seconds: 30              # Request timeout
  session-timeout-minutes: 30      # Session expiration
  session-cleanup-interval-ms: 60000  # Cleanup check interval
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port | 9877 |
| `REST_BRIDGE_TIMEOUT_SECONDS` | Request timeout | 30 |
| `REST_BRIDGE_SESSION_TIMEOUT_MINUTES` | Session timeout | 30 |

## API Documentation

Once running, access the Swagger UI at:
- **Swagger UI:** http://localhost:9877/swagger-ui.html
- **OpenAPI JSON:** http://localhost:9877/api-docs

## API Endpoints

### Session Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/connect` | Connect to REST Services |
| POST | `/api/v1/disconnect` | Close session |
| GET | `/api/v1/session/{sessionId}` | Get session info |
| GET | `/api/v1/session/{sessionId}/valid` | Check session validity |

### DQL Queries

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/dql` | Execute DQL query |
| GET | `/api/v1/dql/available?sessionId=...` | Check if DQL is enabled |

> **Note:** DQL may be disabled on some REST Services deployments. The bridge detects this gracefully and returns HTTP 503 with `DQL_NOT_AVAILABLE`.

### Object Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/objects/{id}` | Get object by r_object_id |
| PUT | `/api/v1/objects/{id}` | Update object attributes |
| GET | `/api/v1/cabinets` | List cabinets |
| GET | `/api/v1/folders/{id}/objects` | List folder contents |

### Type Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/types` | List object types |
| GET | `/api/v1/types/{typeName}` | Get type info |

### User and Group Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/users` | List users |
| GET | `/api/v1/users/{userName}` | Get user info |
| GET | `/api/v1/groups` | List groups |
| GET | `/api/v1/groups/{groupName}` | Get group info |

> **Note:** User and group operations use native REST endpoints (`/users`, `/groups`) instead of DQL, making them available even when DQL is disabled.

## Usage Examples

### Connect to Repository

```bash
curl -X POST http://localhost:9877/api/v1/connect \
  -H "Content-Type: application/json" \
  -d '{
    "endpoint": "https://documentum.example.com/dctm-rest",
    "repository": "MyRepo",
    "username": "dmadmin",
    "password": "password123"
  }'
```

Response:
```json
{
  "sessionId": "rest-abc123-...",
  "repositoryInfo": {
    "name": "MyRepo",
    "endpoint": "https://documentum.example.com/dctm-rest",
    "serverVersion": "23.2...."
  }
}
```

### List Users (No DQL Required)

```bash
curl "http://localhost:9877/api/v1/users?sessionId=rest-abc123-..."
```

### Execute DQL Query (If Available)

```bash
curl -X POST http://localhost:9877/api/v1/dql \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "rest-abc123-...",
    "query": "SELECT r_object_id, object_name FROM dm_document WHERE FOLDER('\''/Temp'\'')",
    "maxRows": 100
  }'
```

### Check DQL Availability

```bash
curl "http://localhost:9877/api/v1/dql/available?sessionId=rest-abc123-..."
```

Response when DQL is disabled:
```json
{
  "available": false,
  "reason": "DQL is not available on this Documentum REST Services endpoint."
}
```

### Get Object

```bash
curl "http://localhost:9877/api/v1/objects/0901234567890123?sessionId=rest-abc123-..."
```

### Disconnect

```bash
curl -X POST http://localhost:9877/api/v1/disconnect \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "rest-abc123-..."}'
```

## Health Check

```bash
curl http://localhost:9877/health
```

Response:
```json
{
  "status": "UP"
}
```

## Service Status

```bash
curl http://localhost:9877/api/v1/status
```

Response:
```json
{
  "service": "rest-bridge",
  "version": "1.0.0-SNAPSHOT",
  "backend": "REST",
  "description": "Documentum REST Services bridge - uses native REST API endpoints"
}
```

## Development

### Project Structure

```
rest-bridge-java11/
├── src/main/java/com/spire/restbridge/
│   ├── RestBridgeApplication.java      # Main application
│   ├── config/                         # Configuration classes
│   │   └── RestBridgeProperties.java
│   ├── controller/                     # REST controllers
│   │   ├── SessionController.java
│   │   ├── DqlController.java
│   │   ├── ObjectController.java
│   │   ├── UserGroupController.java
│   │   └── StatusController.java
│   ├── dto/                            # Request/Response DTOs
│   ├── exception/                      # Exception handling
│   │   ├── GlobalExceptionHandler.java
│   │   ├── DqlNotAvailableException.java
│   │   └── ...
│   ├── model/                          # Domain models
│   └── service/                        # Business logic
│       ├── SessionService.java
│       ├── DqlService.java
│       ├── ObjectService.java
│       ├── TypeService.java
│       └── UserGroupService.java
├── src/main/resources/
│   └── application.yml                 # Configuration
├── src/test/                           # Unit tests
└── pom.xml                             # Maven build file
```

### Running Tests

```bash
mvn test
```

### DQL Graceful Degradation

The DQL service automatically detects if DQL is disabled on the REST Services endpoint:

1. On first DQL request, it tests availability with a simple query
2. If DQL is disabled, subsequent requests return `DQL_NOT_AVAILABLE` (HTTP 503)
3. User/Group operations continue to work via native REST endpoints

## Troubleshooting

### Authentication Failed

Error: `Authentication failed. Check your credentials.`

Solution: Verify username and password. REST Services uses Basic authentication.

### Repository Not Found

Error: `Repository "XYZ" not found.`

Solution: Check the repository name and ensure it's accessible via the REST Services endpoint.

### DQL Not Available

Error: `DQL_NOT_AVAILABLE - DQL is not available on this Documentum REST Services endpoint.`

This is expected if DQL is disabled in the REST Services configuration. Use native REST endpoints for user/group operations instead of DQL queries.

### Connection Timeout

Check that:
1. The REST Services endpoint URL is correct
2. Network connectivity to the endpoint is available
3. SSL/TLS certificates are valid (for HTTPS endpoints)

## License

MIT License - see LICENSE file for details.

## Related Projects

- [rest-bridge](https://github.com/tonychristie/rest-bridge) - Java 17 version of REST Bridge
- [dfc-bridge](https://github.com/tonychristie/dfc-bridge) - DFC-based bridge for native Documentum access
- [dctm-vscode](https://github.com/tonychristie/dctm-vscode) - VS Code extension for Documentum
