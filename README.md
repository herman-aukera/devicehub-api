# DeviceHub API

DeviceHub is a REST API for managing devices and combining their stored details
with telemetry and warranty information. Existing Java domain and service code
coexists with an incremental Kotlin assessment feature in one Spring Boot application.

## Stack

- Java 26
- Kotlin 2.4.20 and Spring Boot 4.1.1
- Apache Maven Wrapper, Maven 3.9.16
- Spring MVC, Spring Data JPA, H2, and Jackson 3
- Springdoc OpenAPI 3.1.0
- Spring Boot-managed kotlinx-coroutines
- JUnit, AssertJ, Mockito, and kotlinx-coroutines-test

## Development setup

Install a stable JDK 26 and select it with JAVA_HOME for your shell or IDE.
The Maven Wrapper downloads Maven 3.9.16; system Maven is not required.

Windows:

```bat
mvnw.cmd clean test
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

Linux, macOS, Git Bash, or Codespaces:

```bash
./mvnw clean test
./mvnw clean package
./mvnw spring-boot:run
```

Alternatively, run the packaged application:

```bash
java -jar target/devicehub-api-1.0.0.jar
```

The service listens on port 8080. The default H2 database is stored under
`data/`; the test profile uses an in-memory database.

In IntelliJ, open the root `pom.xml`, select JDK 26 as the project SDK and Maven
importer JDK, and use Maven Wrapper as Maven home. For Windows Git Bash, use
`"C:\Program Files\Git\bin\bash.exe" --login -i` as the terminal shell.

## Codespaces

Create or rebuild a Codespace from this branch. The devcontainer uses the
official Java feature with Temurin 26, forwards port 8080 as **DeviceHub API**,
and provides Java, Kotlin, and Maven editor extensions. It uses the project
wrapper rather than installing Maven through the feature.

The post-create command downloads dependencies and runs the test suite.
Then start the service with `./mvnw spring-boot:run`. Use the forwarded port
to open Swagger UI. Remote execution must be validated in your Codespace;
local build success alone does not prove the remote environment works.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/devices | Create a device |
| GET | /api/devices | List devices; optional brand and state filters |
| GET | /api/devices/{id} | Read a device |
| PUT | /api/devices/{id} | Update a device |
| PATCH | /api/devices/{id} | Partially update a device |
| DELETE | /api/devices/{id} | Delete a device |
| GET | /api/devices/{id}/assessment | Combine device and partner data |
| GET | /actuator/health | Health status |
| GET | /v3/api-docs | OpenAPI JSON |
| GET | /swagger-ui.html | Swagger UI, with redirect |

Devices have `name`, `brand`, `state`, and an immutable `creationTime`.
States are AVAILABLE, IN_USE, and INACTIVE. An IN_USE device cannot be deleted
or have its name or brand changed.

```bash
curl --fail -H 'Content-Type: application/json' \
  -d '{"name":"MacBook Pro","brand":"Apple","state":"AVAILABLE"}' \
  http://localhost:8080/api/devices

# Use the ID returned by the create response:
curl --fail http://localhost:8080/api/devices/1/assessment
```

## Kotlin Coroutines Assessment

The Kotlin controller and service reuse Java `DeviceService` and
`DeviceResponse`. The JPA-backed device lookup remains blocking and runs
before the partner deadline. Java virtual threads remain enabled.

Telemetry and warranty are independent, mandatory results. Both start with
`async` before either is awaited inside `coroutineScope`. Structured
concurrency ties child lifecycles to the request: partner failure cancels the
sibling, and the 1000 ms partner timeout cancels both operations. No explicit
dispatcher or custom virtual-thread-backed CoroutineDispatcher is used.

Flow is intentionally absent because the endpoint returns one aggregate value.
The local demo adapters use suspending delays of 200 and 350 ms and fixed
responses. They are simulations; no external API credentials are required.

```text
Assessment controller
  -> Java DeviceService -> JPA (blocking)
  -> 1000 ms timeout / coroutineScope
     +-> async TelemetryGateway
     +-> async WarrantyGateway
     -> await both -> DeviceAssessment
```

Example HTTP 200 response:

```json
{
  "device": {
    "id": 1,
    "name": "MacBook Pro",
    "brand": "Apple",
    "state": "AVAILABLE",
    "creationTime": "2026-09-29T12:00:00"
  },
  "telemetry": {
    "status": "ONLINE",
    "batteryPercent": 87,
    "lastSeenAt": "2026-09-29T12:00:00Z"
  },
  "warranty": {
    "covered": true,
    "provider": "DeviceProtect",
    "expiresOn": "2027-12-31"
  }
}
```

Warranty expiration can be null when unknown. Errors use RFC 7807 Problem
Details: 400 for validation, 404 for a missing device, 409 for a business-rule
violation, 502 for partner integration failure, and 504 for assessment timeout.
Partner causes are not exposed in the response.

## Tests

The suite contains 49 tests. Coroutine tests use virtual time to assert
350 ms concurrent completion, sibling cancellation, and the 1000 ms timeout.
The assessment MVC integration test uses async dispatch and the real demo
adapters. Health is checked through full-context MockMvc.

```bash
./mvnw -B clean test
./mvnw -B clean package
```

## Docker

The multi-stage image builds with Maven 3.9.16 and Java 26, then runs on a
Temurin 26 JRE as a non-root user. Compose binds `./data` for persistence and
publishes port 8080. The image includes a health check.

```bash
docker build -t devicehub-api:local .
docker compose up -d --build
curl --fail http://localhost:8080/actuator/health
docker compose down
```

Compose accepts SERVER_PORT, SPRING_PROFILES_ACTIVE, and LOG_LEVEL environment
overrides. Do not commit database files, secrets, or local IDE configuration.
