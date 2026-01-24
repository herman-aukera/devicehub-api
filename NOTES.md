# Engineering Notes & Technical Decision Log: DeviceHub API

## 📚 Interview Preparation Hub
> **Start Here**: This codebase is designed as a study aide for Senior Java Backend interviews.

### 🧠 Core Resources
1.  [**Top 50 Java & Spring QA**](NOTES_JAVA_QA.md) - Rapid-fire questions.
    *   *Topics*: Core Java 21, Collections, Multithreading, Spring Boot, JPA, System Design.
2.  [**Core Logic FAQ**](NOTES_FAQ_CORE.md) - Deep dive into this specific project's code.
    *   *Topics*: Race Conditions, REST maturity, Error Handling, Testing Strategy.
3.  [**Infrastructure FAQ**](NOTES_FAQ_INFRASTRUCTURE.md) - Ops & Architecture.
    *   *Topics*: Virtual Threads, DB Migrations, Docker, Thread Pools, Performance (O(1)).
4.  [**Advanced Scenarios**](NOTES_FAQ_ADVANCED.md) - System Design & "What Ifs".
    *   *Topics*: Scaling to 43M devices, Security, Refactoring, OOM debugging.

### 🛠 Code References
- [**Code Solutions**](NOTES_CODE.md) - Code snippets and pattern implementations.
- [**Copilot Instructions**](.github/copilot-instructions.md) - Architecture standards overview.

---

# 🎯 SENIOR-LEVEL TECHNICAL AUDIT: DeviceHub API

**Audit Date**: January 23, 2026
**Auditor Role**: Senior Staff Engineer / Technical Architect
**Context**: Senior Backend Developer interview for IoT/eSIM company (43M+ devices, 100K req/sec, 12 countries)
**Candidate Built This In**: ~4 hours (take-home assignment)

---

## 📊 PRIORITY MATRIX: Findings Summary

| Priority          | Category      | Finding                                    | Production Risk                        |
| ----------------- | ------------- | ------------------------------------------ | -------------------------------------- |
| 🔴 **P0-CRITICAL** | API Design    | No API versioning                          | Breaking changes = partner churn       |
| 🔴 **P0-CRITICAL** | Database      | No schema migrations (Flyway/Liquibase)    | Untracked schema = deployment failures |
| 🔴 **P0-CRITICAL** | Database      | No database indexes                        | 43M rows = 400ms+ query latency        |
| 🟠 **P1-HIGH**     | Security      | No authentication/authorization            | Public API = data breach               |
| 🟠 **P1-HIGH**     | Scalability   | No pagination on list endpoints            | OOM on large datasets                  |
| 🟠 **P1-HIGH**     | Resilience    | No rate limiting                           | DDoS vulnerability                     |
| 🟠 **P1-HIGH**     | Concurrency   | Race condition in update/delete            | Lost updates, business rule bypass     |
| 🟠 **P1-HIGH**     | Validation    | No size limits on string fields            | DoS via payload size                   |
| 🟠 **P1-HIGH**     | API Design    | PUT allows nulling required fields         | Data corruption                        |
| 🟡 **P2-MEDIUM**   | Database      | H2 in production                           | Not horizontally scalable              |
| 🟡 **P2-MEDIUM**   | Observability | No metrics (Prometheus/Micrometer)         | Cannot measure SLIs/SLOs               |
| 🟡 **P2-MEDIUM**   | Performance   | No caching strategy                        | Redundant DB hits                      |
| 🟡 **P2-MEDIUM**   | Observability | MDC requestId configured but not populated | Logs lack correlation                  |
| 🟡 **P2-MEDIUM**   | API Design    | No idempotency keys for POST               | Duplicate resources on retry           |
| 🟢 **P3-LOW**      | Testing       | No contract tests (Pact)                   | API compatibility unknown              |
| 🟢 **P3-LOW**      | Operations    | No distributed tracing                     | Cross-service debugging impossible     |
| 🟢 **P3-LOW**      | Operations    | No CORS configuration                      | Frontend apps blocked                  |
| 🟢 **P3-LOW**      | Operations    | No graceful shutdown configured            | Requests killed on redeploy            |
| 🟢 **P3-LOW**      | Code Quality  | Lombok @Data on entity                     | hashCode/equals issues with JPA        |

### ✅ STRENGTHS (What's Done Well)

| Strength                          | Evidence                                                    |
| --------------------------------- | ----------------------------------------------------------- |
| **Java 21 Virtual Threads**       | `spring.threads.virtual.enabled=true` for scalability       |
| **Spring Boot 3.2 (Latest)**      | Using 3.2.2 - modern stack with best practices              |
| **Comprehensive Test Suite**      | 44 tests covering unit, integration, and E2E scenarios      |
| **RFC 7807 Problem Details**      | Consistent error responses via `GlobalExceptionHandler`     |
| **Field-Level Validation Errors** | Returns structured error map with field names               |
| **Clean Architecture**            | Proper layering: Controller → Service → Repository          |
| **Business Rule Enforcement**     | IN_USE immutability tested and enforced                     |
| **OpenAPI Documentation**         | Full Swagger UI with examples (SpringDoc 2.3.0)             |
| **Docker Best Practices**         | Multi-stage build, non-root user, health checks             |
| **Immutable DTOs**                | Java records prevent accidental mutation                    |
| **Async Logging**                 | `AsyncAppender` in logback prevents I/O blocking            |
| **Profile-Based Logging**         | `dev`, `test`, `prod` profiles with appropriate verbosity   |
| **OSIV Disabled**                 | `spring.jpa.open-in-view=false` prevents lazy loading leaks |

---

## 🔴 CATEGORY 1: API DESIGN & VERSIONING

### Finding 1.1: No API Versioning [P0-CRITICAL]

**Current State**:

```java
@RequestMapping("/api/devices")  // No version in URL
```

**Production Risk**:

- Breaking changes (field rename, removed endpoint) break all clients simultaneously
- Partners integrating with your API cannot upgrade gracefully
- At 43M devices with external integrations, one breaking change = SLA violation + legal exposure

**Interview Question**:
> "I see `/api/devices` with no version. If you need to rename `creationTime` to `createdAt` next month, how do you deploy without breaking existing clients?"

**Expected Senior Response**:
> "You're absolutely right—this is a gap I'd address for production. I chose to skip versioning given the 4-hour constraint, but here's my production approach:
>
> **URI Versioning** for external partners: `/api/v1/devices` and `/api/v2/devices`
>
> - Clear, cacheable, easy to document
> - Partners can migrate on their timeline
>
> **Header Versioning** for internal services: `Accept: application/vnd.devicehub.v2+json`
>
> - Cleaner URLs for internal consumption
> - Single endpoint, multiple representations
>
> **Deprecation Strategy**:
>
> 1. Add `Deprecation` header with sunset date
> 2. Log v1 usage to track migration
> 3. Maintain v1 for 6-12 months minimum
> 4. Communicate via API changelog + partner emails"

**Implementation Example**:

```java
// Option A: URI Versioning (Recommended for partners)
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceControllerV1 {
    // Original implementation
}

@RestController
@RequestMapping("/api/v2/devices")
public class DeviceControllerV2 {
    // New implementation with breaking changes
    // Returns DeviceResponseV2 with 'createdAt' instead of 'creationTime'
}

// Option B: Header-based versioning with single controller
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @GetMapping(produces = "application/vnd.devicehub.v1+json")
    public ResponseEntity<DeviceResponseV1> getDeviceV1(@PathVariable Long id) {
        return ResponseEntity.ok(deviceService.findByIdV1(id));
    }

    @GetMapping(produces = "application/vnd.devicehub.v2+json")
    public ResponseEntity<DeviceResponseV2> getDeviceV2(@PathVariable Long id) {
        return ResponseEntity.ok(deviceService.findByIdV2(id));
    }
}

// Deprecation headers
@GetMapping("/{id}")
public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long id) {
    return ResponseEntity.ok(deviceService.findById(id))
            .header("Deprecation", "Sun, 01 Jul 2026 00:00:00 GMT")
            .header("Sunset", "Sun, 01 Jan 2027 00:00:00 GMT")
            .header("Link", "</api/v2/devices>; rel=\"successor-version\"");
}
```

**Deeper Probe**:
> "How would you handle a client that's still on v1 after the sunset date? What's your rollback strategy if v2 has a critical bug?"

---

### Finding 1.2: No HATEOAS/Hypermedia Links [P3-LOW]

**Current State**: Responses are plain JSON with no navigation links.

**Production Risk**: Clients hardcode URLs; endpoint changes require client updates.

**Interview Question**:
> "Should device responses include links to related resources like `/api/devices/1/history`?"

**Expected Senior Response**:
> "HATEOAS is valuable for discoverability but adds complexity. For this scope, I'd defer it. In production, I'd use Spring HATEOAS for public APIs where clients need to navigate dynamically, but keep internal service-to-service calls simple."

---

## 🔴 CATEGORY 2: DATABASE & PERSISTENCE

### Finding 2.1: No Schema Migrations [P0-CRITICAL]

**Current State**:

```properties
spring.jpa.hibernate.ddl-auto=update  # Hibernate manages schema
```

**Production Risk**:

- No audit trail of schema changes
- `ddl-auto=update` can cause data loss (column type changes)
- Cannot reproduce exact schema in new environments
- Rollback is impossible without backup
- In CI/CD, schema drift between environments causes deployment failures

**Interview Question**:
> "You're using `ddl-auto=update`. What happens when you need to add a NOT NULL column to a table with 43 million rows?"

**Expected Senior Response**:
> "You've identified a critical gap. `ddl-auto=update` is only appropriate for local development. For production, I'd implement Flyway or Liquibase:
>
> **Why Flyway/Liquibase**:
>
> - Version-controlled migrations (auditable)
> - Repeatable deployments across environments
> - Rollback scripts for failed migrations
> - Lock mechanism prevents concurrent migrations
>
> **For the NOT NULL column scenario**, I'd use a 4-phase zero-downtime migration:
>
> 1. **V1**: Add column as NULLABLE
> 2. **Deploy code** that writes to new column
> 3. **V2**: Backfill existing rows (batched, off-peak)
> 4. **V3**: Add NOT NULL constraint
>
> This prevents table locks on 43M rows."

**Implementation Example**:

```xml
<!-- pom.xml addition -->
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

```yaml
# src/main/resources/db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-devices-table.yaml
  - include:
      file: db/changelog/changes/002-add-indexes.yaml
```

```yaml
# src/main/resources/db/changelog/changes/001-create-devices-table.yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: herman
      changes:
        - createTable:
            tableName: devices
            columns:
              - column:
                  name: id
                  type: bigint
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: brand
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: state
                  type: varchar(20)
                  constraints:
                    nullable: false
              - column:
                  name: creation_time
                  type: timestamp
                  constraints:
                    nullable: false
      rollback:
        - dropTable:
            tableName: devices
```

```properties
# application.properties changes
spring.jpa.hibernate.ddl-auto=validate  # Only validate, never modify
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
spring.liquibase.enabled=true
```

**Deeper Probe**:
> "How would you handle a migration that fails halfway through on 43M rows? What's your strategy for testing migrations before production?"

---

### Finding 2.2: No Database Indexes [P0-CRITICAL]

**Current State**:

```java
@Entity
@Table(name = "devices")
public class Device {
    // No @Index annotations
    // Queries on 'brand' and 'state' will full-table scan
}
```

Repository queries without indexes:

```java
List<Device> findByBrandIgnoreCase(String brand);  // Full scan
List<Device> findByState(DeviceState state);        // Full scan
```

**Production Risk**:

- `findByState(IN_USE)` on 43M rows = **15-30 seconds** without index
- With proper index = **10-50ms**
- Under load (100K req/sec), unindexed queries cause connection pool exhaustion → cascading failure

**Interview Question**:
> "Your `findByState()` query will scan 43 million rows. Walk me through how you'd optimize this for 100K requests per second."

**Expected Senior Response**:
> "Absolutely—this needs indexes. Without them, we're looking at O(n) table scans.
>
> **Immediate fix**: Add B-Tree indexes on frequently queried columns:
>
> - `state` (enum with 3 values → high selectivity per value)
> - `brand` (for case-insensitive, use a functional index or lowercase column)
> - Composite index `(state, brand)` for combined filters
>
> **Performance impact**:
>
> - Without index: ~400ms per query (full scan)
> - With index: ~10ms per query
> - At 100K req/sec: difference between 40 servers and 4 servers
>
> **I'd validate with EXPLAIN ANALYZE** before and after to prove the improvement."

**Implementation Example**:

```yaml
# src/main/resources/db/changelog/changes/002-add-indexes.yaml
databaseChangeLog:
  - changeSet:
      id: 2
      author: herman
      comment: Add indexes for common query patterns
      changes:
        - createIndex:
            indexName: idx_devices_state
            tableName: devices
            columns:
              - column:
                  name: state
        - createIndex:
            indexName: idx_devices_brand
            tableName: devices
            columns:
              - column:
                  name: brand
        - createIndex:
            indexName: idx_devices_state_brand
            tableName: devices
            columns:
              - column:
                  name: state
              - column:
                  name: brand
        - createIndex:
            indexName: idx_devices_creation_time
            tableName: devices
            columns:
              - column:
                  name: creation_time
                  descending: true
      rollback:
        - dropIndex:
            indexName: idx_devices_state
            tableName: devices
        - dropIndex:
            indexName: idx_devices_brand
            tableName: devices
        - dropIndex:
            indexName: idx_devices_state_brand
            tableName: devices
        - dropIndex:
            indexName: idx_devices_creation_time
            tableName: devices
```

Alternative using JPA annotations (if not using Liquibase):

```java
@Entity
@Table(name = "devices", indexes = {
    @Index(name = "idx_devices_state", columnList = "state"),
    @Index(name = "idx_devices_brand", columnList = "brand"),
    @Index(name = "idx_devices_state_brand", columnList = "state, brand"),
    @Index(name = "idx_devices_creation_time", columnList = "creation_time DESC")
})
public class Device {
    // ...
}
```

**Deeper Probe**:
> "When would you choose a composite index over separate indexes? How do you decide the column order in a composite index?"

---

### Finding 2.3: H2 Database in Production [P2-MEDIUM]

**Current State**:

```properties
spring.datasource.url=jdbc:h2:file:${DB_PATH:./data/devicehub}
```

**Production Risk**:

- H2 file-based is single-instance only (no horizontal scaling)
- Limited concurrent write performance
- No replication, no failover
- Not battle-tested for 43M rows under load

**Interview Question**:
> "H2 is fine for development, but how would you evolve this for 43 million devices across 12 countries?"

**Expected Senior Response**:
> "H2 was a pragmatic choice for the take-home—zero setup, file-based persistence, perfect for demonstrating the API.
>
> For production at this scale, I'd migrate to:
>
> - **PostgreSQL** (ACID, mature, excellent index support, read replicas)
> - **Or Aurora PostgreSQL** if on AWS (auto-scaling, multi-AZ failover)
>
> **Migration path**:
>
> 1. Add PostgreSQL profile alongside H2
> 2. Use Liquibase (already discussed) for schema management
> 3. Blue-green deployment with data sync
>
> For 12 countries, consider read replicas per region with global write primary, or CockroachDB/Spanner for true multi-region writes."

---

### Finding 2.4: No Connection Pool Tuning [P2-MEDIUM]

**Current State**: Default HikariCP settings (10 connections max).

**Production Risk**: Under load, threads wait for connections → increased latency → timeouts.

**Interview Question**:
> "What's your connection pool sizing strategy for 100K requests per second?"

**Expected Senior Response**:
> "HikariCP defaults are conservative. For high throughput:
>
> ```properties
> spring.datasource.hikari.maximum-pool-size=30
> spring.datasource.hikari.minimum-idle=10
> spring.datasource.hikari.connection-timeout=30000
> spring.datasource.hikari.idle-timeout=600000
> spring.datasource.hikari.max-lifetime=1800000
> ```
>
> **Formula**: `connections = (core_count * 2) + effective_spindle_count`
> For a 16-core server with SSD: ~30-50 connections per instance.
>
> With Virtual Threads (Java 21), I'd be more aggressive since threads don't block, but I'd monitor connection wait times via Micrometer."

---

### Finding 2.5: Race Condition in Update/Delete [P1-HIGH]

**Current State**:

```java
@Transactional
public DeviceResponse update(Long id, DeviceUpdateRequest request) {
    Device existingDevice = deviceRepository.findById(id)  // Step 1: Read
            .orElseThrow(() -> new DeviceNotFoundException(id));

    validateUpdateAllowed(existingDevice, request);         // Step 2: Validate

    existingDevice.setName(request.name());                 // Step 3: Modify
    // ... time gap where another request can modify ...
    Device savedDevice = deviceRepository.save(existingDevice);  // Step 4: Save
}
```

**Production Risk**:

- **Lost update problem**: Two concurrent requests read same device, both pass validation, both save → last write wins
- **Business rule bypass**: Request A changes state from `AVAILABLE` to `IN_USE`. Request B (started before A completed) still sees `AVAILABLE` and modifies name → violates immutability rule
- At 100K req/sec, race conditions become frequent, not edge cases

**Interview Question**:
> "Two users try to update the same device simultaneously. What happens?"

**Expected Senior Response**:
> "Classic lost update problem. The current implementation has a time-of-check to time-of-use (TOCTOU) vulnerability. Solutions:
>
> **Option 1: Optimistic Locking (preferred)**
>
> ```java
> @Entity
> public class Device {
>     @Version
>     private Long version;
> }
> ```
>
> JPA throws `OptimisticLockException` if version mismatch → return `409 Conflict`.
>
> **Option 2: Pessimistic Locking**
>
> ```java
> @Lock(LockModeType.PESSIMISTIC_WRITE)
> Optional<Device> findById(Long id);
> ```
>
> Blocks concurrent access but reduces throughput.
>
> **Option 3: ETag Headers**
>
> ```java
> @GetMapping("/{id}")
> public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long id) {
>     DeviceResponse device = deviceService.findById(id);
>     return ResponseEntity.ok()
>         .eTag(String.valueOf(device.version()))
>         .body(device);
> }
>
> @PutMapping("/{id}")
> public ResponseEntity<DeviceResponse> updateDevice(
>         @PathVariable Long id,
>         @RequestHeader("If-Match") String etag,
>         @RequestBody DeviceUpdateRequest request) {
>     // Compare etag with current version
> }
> ```
>
> For this API, I'd use **optimistic locking with ETag** since reads vastly outnumber writes."

**Deeper Probe**:
> "How would you handle the `OptimisticLockException` in the GlobalExceptionHandler? What HTTP status code?"

---

### Finding 2.6: No Validation Limits on String Fields [P1-HIGH]

**Current State**:

```java
public record DeviceCreateRequest(
    @NotBlank(message = "Name is required") String name,   // No @Size limit
    @NotBlank(message = "Brand is required") String brand, // No @Size limit
    @NotNull(message = "State is required") DeviceState state
) {}
```

```java
@Column(nullable = false)  // No length specified → defaults to VARCHAR(255) but JPA doesn't enforce
private String name;
```

**Production Risk**:

- Client can send 10MB string as device name → memory exhaustion
- Database may truncate or reject silently
- DoS attack vector via payload size

**Interview Question**:
> "What happens if I send a POST with a 10-megabyte device name?"

**Expected Senior Response**:
> "It would attempt to store it, potentially causing memory issues or database errors. I'd add:
>
> ```java
> public record DeviceCreateRequest(
>     @NotBlank(message = "Name is required")
>     @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
>     String name,
>
>     @NotBlank(message = "Brand is required")
>     @Size(min = 1, max = 100, message = "Brand must be between 1 and 100 characters")
>     String brand,
>
>     @NotNull(message = "State is required")
>     DeviceState state
> ) {}
> ```
>
> Also add `@Column(length = 255)` to entity and configure:
>
> ```properties
> server.tomcat.max-http-form-post-size=1MB
> spring.servlet.multipart.max-request-size=1MB
> ```"

---

### Finding 2.7: PUT Endpoint Allows Nulling Required Fields [P1-HIGH]

**Current State**:

```java
// DeviceUpdateRequest has NO validation annotations
public record DeviceUpdateRequest(
    String name,   // Can be null
    String brand,  // Can be null
    DeviceState state
) {}

// DeviceService.update() sets null values directly
existingDevice.setName(request.name());   // Sets to null if null passed
existingDevice.setBrand(request.brand()); // Sets to null if null passed
```

**Production Risk**:

- `PUT /api/devices/1` with `{"state": "AVAILABLE"}` sets name and brand to `null`
- Violates entity constraints (`@NotBlank`) at persistence layer
- Inconsistent with REST semantics (PUT should replace entire resource)

**Interview Question**:
> "If I send `PUT /api/devices/1` with only `{\"state\": \"AVAILABLE\"}`, what happens to name and brand?"

**Expected Senior Response**:
> "They'd be set to null, causing a database constraint violation. This is a bug. For PUT (full replacement):
>
> ```java
> public record DeviceUpdateRequest(
>     @NotBlank(message = "Name is required")
>     String name,
>
>     @NotBlank(message = "Brand is required")
>     String brand,
>
>     @NotNull(message = "State is required")
>     DeviceState state
> ) {}
> ```
>
> Then in controller, add `@Valid` to PUT but not PATCH:
>
> ```java
> @PutMapping("/{id}")
> public ResponseEntity<DeviceResponse> updateDevice(
>     @PathVariable Long id,
>     @Valid @RequestBody DeviceUpdateRequest request) { ... }  // @Valid enforces full payload
>
> @PatchMapping("/{id}")
> public ResponseEntity<DeviceResponse> partialUpdateDevice(
>     @PathVariable Long id,
>     @RequestBody DeviceUpdateRequest request) { ... }  // No @Valid, nulls allowed
> ```"

---

## 🟠 CATEGORY 3: SECURITY & RESILIENCE

### Finding 3.1: No Authentication/Authorization [P1-HIGH]

**Current State**: All endpoints are publicly accessible.

**Production Risk**:

- Anyone can delete devices
- No audit trail of who did what
- Data breach exposure
- Regulatory non-compliance (GDPR, SOC2)

**Interview Question**:
> "Who can call `DELETE /api/devices/1` right now? How would you secure this?"

**Expected Senior Response**:
> "Currently, anyone—which is obviously not production-ready. I'd implement:
>
> **Layer 1: API Gateway (external)**
>
> - OAuth2/JWT validation at edge
> - Rate limiting per client
>
> **Layer 2: Spring Security (service level)**
>
> ```java
> @PreAuthorize("hasRole('DEVICE_ADMIN')")
> public void delete(Long id) { ... }
> ```
>
> **Layer 3: Audit logging**
>
> - Log userId, action, deviceId for compliance
>
> For this API, I'd use **Spring Security with OAuth2 Resource Server** for stateless JWT validation."

**Implementation Example**:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())  // Stateless API
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/devices/**").hasAuthority("SCOPE_read:devices")
                .requestMatchers(HttpMethod.POST, "/api/v1/devices").hasAuthority("SCOPE_write:devices")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/devices/**").hasAuthority("SCOPE_admin:devices")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

---

### Finding 3.2: No Rate Limiting [P1-HIGH]

**Current State**: No throttling on any endpoint.

**Production Risk**:

- Single malicious client can exhaust resources
- No protection against DDoS
- Noisy neighbor problem in multi-tenant scenario

**Interview Question**:
> "A client starts making 10,000 requests per second. What happens?"

**Expected Senior Response**:
> "Currently, we'd exhaust the connection pool and crash. I'd implement rate limiting at multiple layers:
>
> **Layer 1: API Gateway** (first line of defense)
>
> - 1000 req/min per API key
> - Bucket4j or Redis-based token bucket
>
> **Layer 2: Application level** (defense in depth)
>
> ```java
> @RateLimiter(name = "deviceApi", fallbackMethod = "rateLimitFallback")
> public DeviceResponse findById(Long id) { ... }
> ```
>
> **Response**: Return `429 Too Many Requests` with `Retry-After` header."

---

### Finding 3.3: No Circuit Breaker [P2-MEDIUM]

**Current State**: No resilience patterns for downstream failures.

**Interview Question**:
> "If your database becomes slow, what happens to all incoming requests?"

**Expected Senior Response**:
> "They'd queue up waiting for connections, eventually timing out. I'd implement Resilience4j:
>
> ```java
> @CircuitBreaker(name = "database", fallbackMethod = "fallbackFindById")
> @Retry(name = "database")
> public DeviceResponse findById(Long id) { ... }
> ```
>
> **Circuit states**:
>
> - CLOSED: Normal operation
> - OPEN: Fail fast after threshold (50% failure rate)
> - HALF_OPEN: Test if recovery happened
>
> This prevents cascade failures and allows graceful degradation."

---

## 🟠 CATEGORY 4: SCALABILITY & PERFORMANCE

### Finding 4.1: No Pagination [P1-HIGH]

**Current State**:

```java
public List<DeviceResponse> findAll() {
    return deviceRepository.findAll().stream()  // Returns ALL 43M devices
            .map(this::toResponse)
            .toList();
}
```

**Production Risk**:

- `GET /api/devices` returns 43M records → OOM → crash
- Response time: minutes (if it doesn't crash)
- Network bandwidth: gigabytes per request

**Interview Question**:
> "What happens when someone calls `GET /api/devices` with 43 million devices?"

**Expected Senior Response**:
> "OutOfMemoryError or timeout—definitely a bug. Pagination is mandatory:
>
> ```java
> @GetMapping
> public ResponseEntity<Page<DeviceResponse>> listDevices(
>         @RequestParam(defaultValue = "0") int page,
>         @RequestParam(defaultValue = "20") int size,
>         @RequestParam(defaultValue = "creationTime") String sortBy,
>         @RequestParam(defaultValue = "desc") String sortDir) {
>
>     Pageable pageable = PageRequest.of(page, Math.min(size, 100),  // Max 100 per page
>             Sort.by(Sort.Direction.fromString(sortDir), sortBy));
>
>     return ResponseEntity.ok(deviceService.findAll(pageable));
> }
> ```
>
> **Response includes**: `totalElements`, `totalPages`, `number`, `size`, `content[]`
>
> For cursor-based pagination (better for real-time data): use `creationTime` or `id` as cursor instead of offset."

**Implementation Example**:

```java
// Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Page<Device> findByState(DeviceState state, Pageable pageable);
    Page<Device> findByBrandIgnoreCase(String brand, Pageable pageable);
}

// Service
@Transactional(readOnly = true)
public Page<DeviceResponse> findAll(Pageable pageable) {
    return deviceRepository.findAll(pageable)
            .map(this::toResponse);
}

// Controller
@GetMapping
public ResponseEntity<Page<DeviceResponse>> listDevices(
        @RequestParam(required = false) String brand,
        @RequestParam(required = false) DeviceState state,
        @ParameterObject Pageable pageable) {  // Spring's @ParameterObject for Swagger

    Page<DeviceResponse> devices;
    if (brand != null) {
        devices = deviceService.findByBrand(brand, pageable);
    } else if (state != null) {
        devices = deviceService.findByState(state, pageable);
    } else {
        devices = deviceService.findAll(pageable);
    }
    return ResponseEntity.ok(devices);
}
```

---

### Finding 4.2: No Caching Strategy [P2-MEDIUM]

**Current State**: Every request hits the database.

**Production Risk**:

- Redundant database queries for same data
- Higher latency than necessary
- Increased database load

**Interview Question**:
> "Device ID 1 is requested 1000 times per second. How do you optimize this?"

**Expected Senior Response**:
> "Cache it. Devices change infrequently, so caching is high-value:
>
> **Layer 1: Local cache (Caffeine)**
>
> - 10K devices, 5-minute TTL
> - Zero network latency
>
> **Layer 2: Distributed cache (Redis)**
>
> - Shared across instances
> - 15-minute TTL
>
> **Cache invalidation**:
>
> ```java
> @Cacheable(value = "devices", key = "#id")
> public DeviceResponse findById(Long id) { ... }
>
> @CacheEvict(value = "devices", key = "#id")
> public DeviceResponse update(Long id, DeviceUpdateRequest request) { ... }
> ```
>
> **Cache hit rate target**: 95%+ for device reads."

---

## � CATEGORY 4B: CODE QUALITY DEEP DIVES

### Finding 4.3: Why @Data on JPA Entities is Problematic [P3-LOW but INTERVIEW HOT TOPIC]

**Current State**:

```java
@Entity
@Data  // Lombok generates equals/hashCode
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ...
}
```

**The Problem** (30-second answer):
> "Lombok's `@Data` generates `equals()` and `hashCode()` based on ALL fields. For JPA entities, this causes two critical issues:
>
> 1. **Identity changes after persist**: Before `save()`, `id=null`. After `save()`, `id=42`. Same object, different hash → breaks Sets/Maps.
>
> 2. **Proxy comparison fails**: Hibernate wraps entities in proxies for lazy loading. `equals()` comparing field-by-field can fail between proxy and real object.
>
> **The fix**: Use `@Getter`/`@Setter` only, and implement `equals`/`hashCode` on the business key or just the `id` with a constant `hashCode()` for unpersisted entities."

**Code Example**:

```java
// ❌ BROKEN: @Data
@Entity
@Data
public class Device {
    @Id @GeneratedValue
    private Long id;  // null before persist, 42 after
}

Set<Device> devices = new HashSet<>();
Device d = new Device();
devices.add(d);          // hashCode based on id=null
deviceRepository.save(d); // id becomes 42
devices.contains(d);      // FALSE! hashCode changed

// ✅ CORRECT: Manual implementation
@Entity
@Getter
@Setter
public class Device {
    @Id @GeneratedValue
    private Long id;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Device device)) return false;
        return id != null && id.equals(device.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();  // Constant for all Device instances
    }
}
```

**Follow-up Q&A**:

> Q: "Why return `getClass().hashCode()` instead of `Objects.hash(id)`?"
>
> A: "If `id` is null before persist, `Objects.hash(null)` returns 0. After persist, `Objects.hash(42)` returns 42. The object moves buckets in a HashMap, and `contains()` returns false. Using `getClass().hashCode()` returns the same value regardless of persist state, so the entity stays in its original bucket."

> Q: "What about using a UUID as primary key instead?"
>
> A: "UUIDs solve the hashCode problem (assigned at construction, not by DB), but add 16 bytes per row, don't cluster well for range queries, and complicate debugging. For most cases, Long IDs with proper equals/hashCode are simpler."

**⚠️ GOTCHA TRAPS TO AVOID**:

| ❌ Don't Say                                 | ✅ Say Instead                                                                |
| ------------------------------------------- | ---------------------------------------------------------------------------- |
| "I just use @Data, it's convenient"         | "@Data is fine for DTOs but problematic for entities due to identity issues" |
| "I've never had issues with it"             | "The bug is subtle—it only manifests with Sets/Maps and can be intermittent" |
| "Just use @EqualsAndHashCode.Exclude on id" | "That helps but doesn't fully solve the proxy comparison issue"              |

---

### Finding 4.4: Virtual Threads - Why and When [STRENGTH TO HIGHLIGHT]

**Current State** (this is a STRENGTH):

```properties
spring.threads.virtual.enabled=true  # Java 21 Virtual Threads
```

**30-Second Answer**:
> "Virtual Threads are lightweight threads managed by the JVM, not the OS. Traditional platform threads are ~1MB each, limiting us to thousands. Virtual threads are ~1KB, allowing millions.
>
> For I/O-bound APIs like this one (DB queries, HTTP calls), virtual threads eliminate thread pool sizing headaches. Each request gets its own thread—no more tuning `server.tomcat.threads.max`.
>
> I enabled them with one property: `spring.threads.virtual.enabled=true`. Spring Boot 3.2 handles the rest."

**Why This Matters for 100K req/sec**:

| Metric              | Platform Threads     | Virtual Threads               |
| ------------------- | -------------------- | ----------------------------- |
| Memory per thread   | ~1MB                 | ~1KB                          |
| Max concurrent      | ~2,000-10,000        | Millions                      |
| Thread pool tuning  | Complex              | Not needed                    |
| Context switch      | OS-level (expensive) | JVM-level (cheap)             |
| Blocking I/O impact | Wastes thread        | Thread yields, reuses carrier |

**Follow-up Q&A**:

> Q: "When should you NOT use virtual threads?"
>
> A: "Three cases:
> 1. **CPU-bound work**: Virtual threads yield on I/O, not CPU. For compute-heavy tasks, platform threads are fine.
> 2. **Synchronized blocks holding I/O**: Virtual threads 'pin' to carrier threads inside `synchronized`. Use `ReentrantLock` instead.
> 3. **ThreadLocal abuse**: Each virtual thread gets its own ThreadLocal, but creating millions of TL instances can bloat memory."

> Q: "How does this interact with database connection pools?"
>
> A: "Great question—this is a gotcha. Virtual threads can spawn millions, but HikariCP still has a fixed pool (default 10). Millions of threads waiting on 10 connections = thread starvation. You need to:
> 1. Right-size the connection pool for expected concurrency
> 2. Consider connection-per-request patterns with PgBouncer
> 3. Or use reactive (R2DBC) for true non-blocking, but that's a bigger rewrite."

**⚠️ GOTCHA TRAPS TO AVOID**:

| ❌ Don't Say                         | ✅ Say Instead                                                                                   |
| ----------------------------------- | ----------------------------------------------------------------------------------------------- |
| "Virtual threads are always faster" | "They're better for I/O-bound workloads; CPU-bound sees no benefit"                             |
| "They replace async/reactive"       | "They provide blocking-style code with non-blocking benefits, but reactive still has use cases" |
| "Just enable and forget"            | "You still need to monitor for pinning and connection pool bottlenecks"                         |

---

### Finding 4.5: v2 API Rollback Strategy [FREQUENTLY ASKED]

**Interview Question**:
> "You've deployed v2 of your API. A critical bug is discovered in production. What's your rollback strategy?"

**30-Second Answer**:
> "My rollback strategy has three layers:
>
> 1. **Feature flags**: v2 endpoints are behind a flag. Flip it off → instant rollback, no deployment.
> 2. **Traffic splitting**: API gateway routes 10% to v2 initially. If errors spike, shift 100% back to v1.
> 3. **Blue-green deployment**: v1 and v2 run simultaneously. DNS/load balancer switch is instant.
>
> Key principle: v2 must be **backward-compatible** with the database. Schema changes use expand-contract pattern—add new columns in v2, don't remove old ones until v1 is sunset."

**Implementation Approaches**:

```java
// Approach 1: Feature Flag with @Value
@RestController
@RequestMapping("/api/v2/devices")
public class DeviceControllerV2 {

    @Value("${feature.api.v2.enabled:false}")
    private boolean v2Enabled;

    @GetMapping("/{id}")
    public ResponseEntity<?> getDevice(@PathVariable Long id) {
        if (!v2Enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("v2 API is currently disabled");
        }
        return ResponseEntity.ok(deviceService.findByIdV2(id));
    }
}

// Approach 2: Spring Profiles for full rollback
// application-v2-enabled.properties
feature.api.v2.enabled=true

// Rollback: deploy with profile=v1-only
java -jar app.jar --spring.profiles.active=v1-only

// Approach 3: API Gateway routing (AWS API Gateway / Kong)
// Route: /api/v2/* → 10% to v2-deployment, 90% to v1-deployment
// Rollback: change weight to 0% v2, 100% v1
```

**Follow-up Q&A**:

> Q: "What if v2 made database schema changes that v1 can't handle?"
>
> A: "That's why I use the expand-contract pattern:
> 1. **Expand**: v2 adds new column `created_at` alongside `creation_time`
> 2. **Migrate**: Background job copies data from old to new column
> 3. **v1 keeps working**: It still reads/writes `creation_time`
> 4. **Contract**: Only after v1 is sunset, drop `creation_time`
>
> The schema is always compatible with both versions during transition."

> Q: "How do you know when it's safe to remove v1?"
>
> A: "Metrics. I track v1 request counts. When it hits zero (or below threshold) for 30 days, and all partners have confirmed migration, I sunset it. The deprecation headers (`Sunset: <date>`) give advance warning."

**⚠️ GOTCHA TRAPS TO AVOID**:

| ❌ Don't Say                               | ✅ Say Instead                                                                 |
| ----------------------------------------- | ----------------------------------------------------------------------------- |
| "Just redeploy the old JAR"               | "That's a last resort—feature flags give instant rollback without deployment" |
| "We'd just revert the database migration" | "Database rollbacks are dangerous; I use expand-contract to avoid them"       |
| "Rollbacks don't happen if we test well"  | "Even with testing, production issues occur; rollback strategy is mandatory"  |

---

## �🟡 CATEGORY 5: OBSERVABILITY & OPERATIONS

### Finding 5.1: No Metrics Endpoint [P2-MEDIUM]

**Current State**:

```properties
management.endpoints.web.exposure.include=health,info  # No metrics
```

**Production Risk**:

- Cannot measure p50/p95/p99 latency
- No request rate visibility
- No error rate tracking
- SLI/SLO measurement impossible

**Interview Question**:
> "How do you know if your API is meeting its latency SLO?"

**Expected Senior Response**:
> "Currently, I can't—that's a gap. I'd add Micrometer + Prometheus:
>
> ```properties
> management.endpoints.web.exposure.include=health,info,prometheus,metrics
> management.metrics.tags.application=${spring.application.name}
> ```
>
> **Key metrics to track**:
>
> - `http_server_requests_seconds` (latency histogram)
> - `hikaricp_connections_active` (connection pool)
> - `jvm_memory_used_bytes` (memory pressure)
>
> **SLO example**: p99 latency < 100ms for GET requests
>
> Visualize in Grafana, alert in PagerDuty when SLO breached."

**Implementation Example**:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```properties
# application.properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.slo.http.server.requests=50ms,100ms,200ms,500ms
```

---

### Finding 5.2: No Request Correlation/Tracing [P3-LOW]

**Current State**: Logs have no trace IDs; cross-service debugging impossible.

**Interview Question**:
> "A request fails. How do you trace it across services?"

**Expected Senior Response**:
> "Add distributed tracing with Micrometer Tracing + Zipkin/Jaeger:
>
> ```properties
> management.tracing.sampling.probability=1.0  # 100% in dev, 10% in prod
> ```
>
> Each request gets a `traceId` propagated via headers. Logs include trace context:
>
> ```
> 2026-01-23 10:30:00 [traceId=abc123] INFO DeviceService - Finding device id=1
> ```"

---

### Finding 5.3: MDC RequestId Configured But Not Populated [P2-MEDIUM]

**Current State**:

```xml
<!-- logback-spring.xml -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %X{requestId} %-5level %logger{36} - %msg%n</pattern>
```

But no filter populates `requestId` in MDC.

**Production Risk**:

- Logs have empty space where requestId should be
- Cannot correlate logs for a single request
- Debugging distributed issues becomes guesswork

**Interview Question**:
> "I see `%X{requestId}` in your logback config. How does a request get its ID?"

**Expected Senior Response**:
> "Good catch—it's configured but not wired. I need a filter:
>
> ```java
> @Component
> @Order(Ordered.HIGHEST_PRECEDENCE)
> public class RequestIdFilter extends OncePerRequestFilter {
>
>     @Override
>     protected void doFilterInternal(HttpServletRequest request,
>                                     HttpServletResponse response,
>                                     FilterChain filterChain)
>             throws ServletException, IOException {
>
>         String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
>                 .orElse(UUID.randomUUID().toString());
>
>         MDC.put("requestId", requestId);
>         response.setHeader("X-Request-Id", requestId);
>
>         try {
>             filterChain.doFilter(request, response);
>         } finally {
>             MDC.remove("requestId");
>         }
>     }
> }
> ```
>
> Now every log line includes the request ID, and the response header lets clients correlate their requests."

---

## 🟢 CATEGORY 6: TESTING & QUALITY

### Finding 6.1: No Load/Performance Tests [P2-MEDIUM]

**Current State**: Unit and integration tests only.

**Interview Question**:
> "How do you know this API can handle 100K requests per second?"

**Expected Senior Response**:
> "I don't—yet. I'd add:
>
> **Gatling or k6 load tests**:
>
> ```javascript
> // k6 script
> export default function() {
>   http.get('http://localhost:8080/api/v1/devices/1');
> }
> export let options = {
>   vus: 1000,
>   duration: '5m',
> };
> ```
>
> **Run in CI/CD** against staging environment.
> **Baseline metrics**: p50 < 20ms, p99 < 100ms, error rate < 0.1%"

---

### Finding 6.2: No Contract Tests [P3-LOW]

**Current State**: No Pact or Spring Cloud Contract tests.

**Interview Question**:
> "How do you ensure API changes don't break clients?"

**Expected Senior Response**:
> "Consumer-driven contract testing with Pact or Spring Cloud Contract. Clients define expected request/response, provider verifies against contracts. Breaking change = failed build."

---

## 🟢 CATEGORY 7: CODE QUALITY

### Finding 7.1: Inconsistent Logging Levels [P3-LOW]

**Current State**: INFO for create/update/delete, DEBUG for reads.

**Interview Question**:
> "Why is `findById` logged at DEBUG but `create` at INFO?"

**Expected Senior Response**:
> "Good catch. Reads are high-volume, so DEBUG prevents log noise. Writes are lower volume and audit-worthy, so INFO. I'd make this consistent with a logging strategy document."

---

### Finding 7.2: No Input Sanitization Beyond Validation [P2-MEDIUM]

**Current State**: `@NotBlank` validation but no sanitization.

**Interview Question**:
> "What if someone creates a device named `<script>alert('xss')</script>`?"

**Expected Senior Response**:
> "It would be stored as-is. For defense in depth:
>
> 1. Encode output (JSON serialization handles this)
> 2. Add input sanitization for HTML/script tags
> 3. Use `@SafeHtml` from Hibernate Validator (deprecated) or custom validator"

---

## 📋 INTERVIEW SCRIPT: QUESTIONS IN ORDER

### Opening (5 min)

1. "Walk me through the architecture of DeviceHub API."
2. "What trade-offs did you make given the time constraint?"

### Deep Dive - Critical Gaps (20 min)

1. "I see `/api/devices` with no version. How would you handle breaking changes?"
2. "You're using `ddl-auto=update`. What's your migration strategy for production?"
3. "Your queries have no indexes. What happens at 43 million rows?"

### Scalability Probes (15 min)

1. "What happens when `GET /api/devices` is called with 43M devices?"
2. "How would you handle 100K requests per second?"
3. "Device ID 1 is requested 1000 times/second. How do you optimize?"

### Security & Resilience (10 min)

1. "Who can delete devices right now? How would you secure this?"
2. "A client makes 10K req/sec. What happens?"
3. "The database becomes slow. What happens to requests?"

### Observability (5 min)

1. "How do you know if your API meets its latency SLO?"
2. "A request fails. How do you trace it?"

### Closing (5 min)

1. "What would you fix first if given 2 hours?"
2. "Any questions for us about our architecture?"

---

## 🚩 RED FLAGS TO WATCH FOR

| Red Flag                              | Why It's Concerning     |
| ------------------------------------- | ----------------------- |
| Defensive about gaps                  | Cannot receive feedback |
| "I didn't have time" without solution | Excuses over solutions  |
| Doesn't know index impact             | DB fundamentals missing |
| "H2 is fine for production"           | No scale awareness      |
| Cannot explain migration strategy     | Never deployed to prod  |
| No mention of monitoring              | Ops experience missing  |

---

## ✅ GREEN FLAGS TO LOOK FOR

| Green Flag                                 | Why It Impresses          |
| ------------------------------------------ | ------------------------- |
| Acknowledges gaps proactively              | Self-aware, honest        |
| Provides production solution for each gap  | Knows how to fix it       |
| Mentions specific numbers (10ms vs 400ms)  | Quantitative thinking     |
| Discusses trade-offs (Flyway vs Liquibase) | Pragmatic decision-making |
| Asks about their architecture              | Curious, engaged          |
| Connects to 43M device context             | Understands the scale     |

---

## ⏱️ 2-HOUR IMPLEMENTATION PLAN

If given 2 hours during/after interview to improve:

### Hour 1: Critical Fixes

| Time      | Task                                   | Impact                     |
| --------- | -------------------------------------- | -------------------------- |
| 0:00-0:20 | Add API versioning (`/api/v1/devices`) | Breaking change protection |
| 0:20-0:40 | Add Liquibase + initial migration      | Schema tracking            |
| 0:40-1:00 | Add database indexes migration         | Query performance          |

### Hour 2: High-Priority Fixes

| Time      | Task                            | Impact          |
| --------- | ------------------------------- | --------------- |
| 1:00-1:20 | Add pagination to list endpoint | OOM prevention  |
| 1:20-1:40 | Add Spring Security skeleton    | Auth foundation |
| 1:40-2:00 | Add Prometheus metrics endpoint | Observability   |

---

## 🎯 YOUR NARRATIVE (Memorize This)

> "I built DeviceHub API in 4 hours to demonstrate Spring Boot fundamentals—clean architecture, business rule enforcement, comprehensive testing. I intentionally deferred production concerns like versioning, migrations, and indexing given time constraints.
>
> Now that you've identified these gaps, here's how I'd address each:
>
> - **Versioning**: URI-based `/api/v1/` with deprecation headers
> - **Migrations**: Liquibase with 4-phase zero-downtime pattern
> - **Indexes**: B-Tree on state, brand, creation_time; composite for combined filters
>
> These aren't theoretical—I've implemented them at scale. Want me to walk through the Liquibase migration strategy?"

---

## 📚 REFERENCE: Production Checklist

```
□ API versioned (/api/v1/)
□ Schema migrations (Liquibase/Flyway)
□ Database indexes on query columns
□ Pagination on list endpoints
□ Authentication (OAuth2/JWT)
□ Rate limiting (429 responses)
□ Metrics endpoint (/actuator/prometheus)
□ Health checks (deep, not just /health)
□ Connection pool tuning (HikariCP)
□ Caching strategy (Caffeine + Redis)
□ Circuit breakers (Resilience4j)
□ Structured logging with trace IDs
□ Load tests (Gatling/k6)
□ Contract tests (Pact)
□ Security scan (OWASP dependency-check)
```

---

---

## 🆘 RESPONSE TEMPLATE FOR KNOWLEDGE GAPS

When asked about something you haven't implemented:

**Formula**:
> "I haven't implemented [X] in this project, but here's my production approach:
> 1. [First step with specific tool/technique]
> 2. [Second step addressing scale]
> 3. [How I'd validate it works]
>
> I've done this at [previous context] where we [brief success story]."

**Example**:
> "I haven't implemented rate limiting here, but my production approach would be:
> 1. Use Bucket4j with Redis for distributed rate limiting
> 2. Configure 100 req/sec per API key with token bucket algorithm
> 3. Return 429 with `Retry-After` header showing when to retry
>
> At my previous role, we used this pattern to handle a traffic spike from 10K to 100K RPS during a product launch."

**Key phrases that impress**:
- "My production approach would be..."
- "The trade-off I'd consider is..."
- "At scale, this becomes critical because..."
- "I'd validate this by..."

---

## 🔄 PIVOT STRATEGY (When Stuck on a Topic)

If you don't know the answer, pivot to a related strength:

| If Stuck On                     | Pivot To                                                                         |
| ------------------------------- | -------------------------------------------------------------------------------- |
| Specific caching implementation | "I'd use Spring's @Cacheable—here's how I handle cache invalidation..."          |
| Kubernetes specifics            | "My Docker setup shows production thinking—multi-stage, non-root, health checks" |
| Specific monitoring tool        | "I'd expose Prometheus metrics—here's how I'd define SLOs..."                    |
| Database internals              | "I understand the access patterns—here's what indexes I'd add..."                |
| Authentication specifics        | "I'd use Spring Security with OAuth2—here's the role-based access I'd design..." |

**Pivot phrase**: "I'm less familiar with [X] specifically, but I'd approach it by [Y], similar to how I [related experience]."

---

## ❓ CLOSING QUESTIONS TO ASK INTERVIEWERS

Asking smart questions shows engagement and helps you evaluate the role:

### Architecture Questions (Pick 2-3)

1. "With 43 million devices, what's your current database sharding strategy?"
2. "How do you handle schema migrations at this scale? Flyway, Liquibase, or custom?"
3. "What's your API versioning approach for partner integrations?"
4. "How do you handle the 12-country deployment—single region or geo-distributed?"

### Team & Process Questions (Pick 1-2)

5. "What does the on-call rotation look like for backend engineers?"
6. "How do you balance feature work vs. technical debt?"
7. "What's the code review process like—PRs, pair programming, both?"

### Growth Questions (Pick 1)

8. "What would success look like in the first 90 days?"
9. "What's the biggest technical challenge the team is facing right now?"
10. "How do backend engineers contribute to architectural decisions?"

**Pro tip**: Listen to their answers and ask follow-ups. It shows genuine curiosity.

---

## ⚡ 10 CRITICAL TOPICS: QUICK REFERENCE CARD

| #   | Topic                               | 30-Second Answer Location | Code Location     |
| --- | ----------------------------------- | ------------------------- | ----------------- |
| 1   | Why no API versioning?              | Finding 1.1               | NOTES_CODE.md §3  |
| 2   | How would you add Liquibase?        | Finding 2.1               | NOTES_CODE.md §8  |
| 3   | What indexes would you add?         | Finding 2.2               | NOTES_CODE.md §1  |
| 4   | How does optimistic locking work?   | Finding 2.5               | NOTES_CODE.md §2  |
| 5   | Why is @Data on entities bad?       | Finding 4.3               | NOTES_CODE.md §11 |
| 6   | How would you paginate 43M devices? | Finding 4.1               | NOTES_CODE.md §4  |
| 7   | What's your v2 rollback strategy?   | Finding 4.5               | NOTES_CODE.md §13 |
| 8   | How prevent race conditions?        | Finding 2.5               | NOTES_CODE.md §2  |
| 9   | Why Virtual Threads?                | Finding 4.4               | NOTES_CODE.md §12 |
| 10  | What metrics would you monitor?     | Finding 5.1               | NOTES_CODE.md §7  |

---

*Audit completed: January 23, 2026*
*Total findings: 22 (3 Critical, 6 High, 7 Medium, 6 Low)*
*Strengths identified: 13*
*Interview topics fully covered: 10/10*
