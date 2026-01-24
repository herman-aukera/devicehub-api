# DeviceHub API - Copilot Instructions

## Architecture Overview

Spring Boot 3.2 REST API with layered architecture: **Controller → Service → Repository → H2 Database**

- **Controllers** ([DeviceController.java](../src/main/java/com/devicehub/api/controller/DeviceController.java)): Handle HTTP, OpenAPI annotations, delegate to services
- **Services** ([DeviceService.java](../src/main/java/com/devicehub/api/service/DeviceService.java)): Business logic, DTO ↔ Entity mapping, `@Transactional` boundaries
- **Repository**: Spring Data JPA interfaces with custom queries
- **DTOs**: Java records (`DeviceCreateRequest`, `DeviceUpdateRequest`, `DeviceResponse`)
- **Exceptions**: RFC 7807 Problem Details via `GlobalExceptionHandler`

## Critical Business Rules

These rules are enforced in `DeviceService` and tested extensively:

1. **IN_USE devices are immutable**: Cannot update `name` or `brand` when `state == IN_USE` → throws `BusinessRuleViolationException` (409 Conflict)
2. **IN_USE devices cannot be deleted** → throws `BusinessRuleViolationException` (409 Conflict)
3. **`creationTime` is immutable**: Set via `@PrePersist` in entity, marked `updatable = false`

## Code Patterns

### DTOs as Records

```java
public record DeviceCreateRequest(
    @NotBlank(message = "Name is required") String name,
    @NotBlank(message = "Brand is required") String brand,
    @NotNull(message = "State is required") DeviceState state
) {}
```

### Service DTO Mapping (manual, not MapStruct)

```java
private DeviceResponse toResponse(Device device) {
    return new DeviceResponse(device.getId(), device.getName(), ...);
}
```

### Exception Handling

Throw domain exceptions (`DeviceNotFoundException`, `BusinessRuleViolationException`) → `GlobalExceptionHandler` converts to RFC 7807 `ProblemDetail`.

### State Validation Pattern

Uses Java 21 switch expressions with pattern matching in `validateUpdateAllowed()`.

## Testing Conventions

- **Unit tests**: `@ExtendWith(MockitoExtension.class)`, mock repository, use AssertJ
- **Integration tests**: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- **Test naming**: `should<Expected>_when<Condition>` (e.g., `shouldThrowException_whenDeletingInUseDevice`)
- Run: `./mvnw test` or `./mvnw test -Dtest=DeviceServiceTest`

## Developer Commands

```bash
./mvnw clean package          # Build JAR
./mvnw spring-boot:run        # Run locally (port 8080)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # With dev profile
docker-compose up -d          # Run in Docker
```

## Key Configuration

- **Profiles**: `default` (prod), `dev` (verbose logging), `test` (in-memory H2)
- **Database**: File-based H2 at `./data/devicehub.mv.db` (not in version control)
- **Virtual threads enabled**: `spring.threads.virtual.enabled=true`
- **API docs**: `/swagger-ui.html`, `/v3/api-docs`

## When Adding Features

1. Start with test in appropriate `src/test/.../` package
2. DTOs go in `dto/` as records with validation annotations + OpenAPI schemas
3. Add new exceptions extending `RuntimeException`, handle in `GlobalExceptionHandler`
4. Service methods need `@Transactional` (or `readOnly = true` for reads)
5. Controller methods need OpenAPI `@Operation` and `@ApiResponse` annotations
