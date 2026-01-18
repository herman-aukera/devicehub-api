# DeviceHub API

A production-ready REST API for managing device resources, built with Java 21 and Spring Boot 3.2.

## Features

- **RESTful API** with full CRUD operations for device management
- **Business Rule Enforcement**: Prevents modifications and deletions of in-use devices
- **RFC 7807 Problem Details** for consistent error responses
- **OpenAPI Documentation** with Swagger UI
- **H2 File-Based Database** for data persistence
- **Virtual Threads** (Java 21) for improved scalability
- **Production-Ready Logging** with MDC support
- **Docker Support** for containerized deployment
- **Comprehensive Test Coverage** with unit and integration tests

## Technology Stack

- **Java 21** (Amazon Corretto)
- **Spring Boot 3.2.2**
  - Spring Web
  - Spring Data JPA
  - Spring Boot Actuator
- **H2 Database 2.2.224** (file-based persistence)
- **SpringDoc OpenAPI 2.3.0** (Swagger)
- **Lombok 1.18.30**
- **Logback** for logging
- **JUnit 5** + **Mockito** for testing

## Quick Start

### Prerequisites

- Java 21 or later
- Maven 3.9+ (or use the included wrapper)

### Build and Run

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Or run the JAR
java -jar target/devicehub-api-1.0.0.jar
```

The API will be available at `http://localhost:8080`.

### Docker

```bash
# Build Docker image
docker-compose build

# Start services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop services
docker-compose down
```

## API Endpoints

### Device Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/devices` | Create a new device |
| `GET` | `/api/devices/{id}` | Get device by ID |
| `GET` | `/api/devices` | List all devices (supports filtering) |
| `PUT` | `/api/devices/{id}` | Update device (full) |
| `PATCH` | `/api/devices/{id}` | Update device (partial) |
| `DELETE` | `/api/devices/{id}` | Delete device |

### Query Parameters

- `brand`: Filter devices by brand (case-insensitive)
- `state`: Filter devices by state (`AVAILABLE`, `IN_USE`, `INACTIVE`)

### Health Check

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/health` | Application health status |

### API Documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Device Model

```json
{
  "id": 1,
  "name": "MacBook Pro",
  "brand": "Apple",
  "state": "AVAILABLE",
  "creationTime": "2026-01-18T10:30:00"
}
```

### States

- **AVAILABLE**: Device is available for use
- **IN_USE**: Device is currently in use
- **INACTIVE**: Device is inactive or out of service

## Business Rules

1. **Name/Brand Immutability**: Cannot update `name` or `brand` when device `state` is `IN_USE`
2. **Deletion Restriction**: Cannot delete devices with `state` set to `IN_USE`
3. **Creation Time Immutability**: `creationTime` is set automatically and cannot be updated

## Example Requests

### Create Device

```bash
curl -X POST http://localhost:8080/api/devices \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MacBook Pro 16",
    "brand": "Apple",
    "state": "AVAILABLE"
  }'
```

**Response** (201 Created):
```json
{
  "id": 1,
  "name": "MacBook Pro 16",
  "brand": "Apple",
  "state": "AVAILABLE",
  "creationTime": "2026-01-18T10:30:00.123"
}
```

### Get Device

```bash
curl http://localhost:8080/api/devices/1
```

**Response** (200 OK):
```json
{
  "id": 1,
  "name": "MacBook Pro 16",
  "brand": "Apple",
  "state": "AVAILABLE",
  "creationTime": "2026-01-18T10:30:00.123"
}
```

### List Devices (Filtered)

```bash
# Filter by brand
curl "http://localhost:8080/api/devices?brand=Apple"

# Filter by state
curl "http://localhost:8080/api/devices?state=IN_USE"
```

### Update Device

```bash
curl -X PUT http://localhost:8080/api/devices/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MacBook Pro 16",
    "brand": "Apple",
    "state": "IN_USE"
  }'
```

### Partial Update

```bash
curl -X PATCH http://localhost:8080/api/devices/1 \
  -H "Content-Type: application/json" \
  -d '{
    "state": "INACTIVE"
  }'
```

### Delete Device

```bash
curl -X DELETE http://localhost:8080/api/devices/1
```

**Response** (204 No Content)

## Error Handling

The API uses RFC 7807 Problem Details for HTTP APIs. All errors return a consistent structure:

```json
{
  "type": "https://devicehub.api/errors/device-not-found",
  "title": "Device Not Found",
  "status": 404,
  "detail": "Device not found with id: 999",
  "instance": "/api/devices/999",
  "timestamp": "2026-01-18T10:30:00.123Z"
}
```

### HTTP Status Codes

- `200 OK`: Successful GET/PUT/PATCH
- `201 Created`: Successful POST
- `204 No Content`: Successful DELETE
- `400 Bad Request`: Validation error
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation
- `500 Internal Server Error`: Unexpected error

## Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=DeviceServiceTest

# Run with coverage
./mvnw clean test jacoco:report
```

### Test Coverage

- **Unit Tests**: Service layer, Repository layer, Domain model
- **Integration Tests**: Controller layer with @WebMvcTest
- **End-to-End Tests**: Full stack tests with @SpringBootTest

Total: 44 tests

## Configuration

### Application Properties

Key configurations in `application.properties`:

```properties
# Server
server.port=8080

# Virtual Threads (Java 21)
spring.threads.virtual.enabled=true

# H2 Database (File-based)
spring.datasource.url=jdbc:h2:file:./data/devicehub
spring.datasource.driverClassName=org.h2.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update

# OpenAPI
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

### Profiles

- **default**: Production configuration
- **dev**: Development configuration (verbose logging)
- **test**: Test configuration (in-memory H2)

Activate profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Database

The application uses H2 database in file-based mode. Data is persisted in `./data/devicehub.mv.db`.

### H2 Console (Development)

The H2 console is disabled by default. To enable it for development:

```properties
# application-dev.properties
spring.h2.console.enabled=true
```

Access at: `http://localhost:8080/h2-console`

- **JDBC URL**: `jdbc:h2:file:./data/devicehub`
- **Username**: `sa`
- **Password**: (empty)

## Logging

Structured logging with MDC (Mapped Diagnostic Context) support:

```properties
# Logging levels
logging.level.root=INFO
logging.level.com.devicehub.api=DEBUG
```

Log pattern includes:
- Timestamp
- Thread name
- Log level
- Logger name
- Request ID (MDC)
- Message

## Security Considerations

- Sensitive configuration in `.env` file (excluded from git)
- Database files excluded from version control
- No hardcoded credentials
- CORS configured for production

## Development

### Project Structure

```
devicehub-api/
├── src/
│   ├── main/
│   │   ├── java/com/devicehub/api/
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── service/          # Business logic
│   │   │   ├── repository/       # Data access
│   │   │   ├── domain/           # Entities and enums
│   │   │   ├── dto/              # DTOs (Records)
│   │   │   └── exception/        # Custom exceptions
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       ├── application-test.properties
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/devicehub/api/
│           ├── controller/       # Controller tests
│           ├── service/          # Service tests
│           ├── repository/       # Repository tests
│           ├── domain/           # Entity tests
│           └── integration/      # E2E tests
├── .gitignore
├── .gitattributes
├── .env.example
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

### Code Style

- **TDD Methodology**: Tests written before implementation
- **Atomic Commits**: Each feature/fix in separate commit
- **Conventional Commits**: Commit messages follow convention
- **Java 21 Features**: Records for DTOs, pattern matching, virtual threads
- **Clean Code**: Single Responsibility Principle, meaningful names

### Contributing

1. Write tests first (TDD)
2. Follow existing code style
3. Keep commits atomic
4. Add API documentation
5. Update README if needed

## License

This project is a coding challenge/interview project.

## Author

Built with Java 21 and Spring Boot 3.2.
