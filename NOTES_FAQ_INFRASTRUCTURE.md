# DeviceHub API - Interview FAQ: Infrastructure & Spring Internals

**Purpose**: Deep understanding of infrastructure, Spring internals, and configuration for interview defense.
**Format**: Each question answered at 3 levels (ELI8 → Junior → Senior)
**Cross-references**: Links to NOTES_FAQ_CORE.md and NOTES_FAQ_ADVANCED.md

---

## 📚 Table of Contents

13. [Q13: Easy authentication/authorization](#q13-easy-authenticationauthorization)
14. [Q14: Pagination in Spring](#q14-pagination-in-spring)
15. [Q15: Async Logging with AsyncAppender](#q15-async-logging-with-asyncappender)
16. [Q16: @RestController vs @Controller](#q16-restcontroller-vs-controller)
17. [Q17: DDL-auto and NOT NULL on 43M rows](#q17-ddl-auto-and-not-null-on-43m-rows)
18. [Q18: How Liquibase tracks migrations](#q18-how-liquibase-tracks-migrations)
19. [Q19: Migration failure and testing strategy](#q19-migration-failure-and-testing-strategy)
20. [Q20: Top 50 Java/Spring Interview Questions](#q20-top-50-javaspring-interview-questions)
21. [Q21: HikariCP Connection Pool](#q21-hikaricp-connection-pool)
22. [Q22: ETag Headers for Optimistic Locking](#q22-etag-headers-for-optimistic-locking)
23. [Q23: OptimisticLockException Handling](#q23-optimisticlockexception-handling)
24. [Q24: Validation Flow and GlobalExceptionHandler](#q24-validation-flow-and-globalexceptionhandler)

---

## Q13: Easy authentication/authorization

### 🧒 Like I'm 8 (ELI8)

**Authentication** = "Who are you?" (showing your ID card at the door)
**Authorization** = "What can you do?" (the ID card says if you can enter the VIP room)

Right now, DeviceHub's door is WIDE OPEN. Anyone can walk in and do anything!

### 👶 Junior Developer Level

**Current state (NO security):**

```bash
# ANYONE can delete any device!
curl -X DELETE http://localhost:8080/api/devices/1
# Response: 204 No Content 😱
```

**Adding basic authentication with Spring Security:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disable for REST API
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/devices/**").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/api/devices").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/devices/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/devices/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());  // Basic auth for simplicity

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
            .username("user")
            .password("password")
            .roles("USER")
            .build();

        UserDetails admin = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("admin123")
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(user, admin);
    }
}
```

**Now:**

```bash
# Without auth: 401 Unauthorized
curl http://localhost:8080/api/devices/1

# With basic auth:
curl -u admin:admin123 http://localhost:8080/api/devices/1
# Response: 200 OK ✓
```

### 🎓 Senior Developer Level

**Production setup with JWT:**

```
                    ┌─────────────────────────────────────┐
                    │         API Gateway (Kong)          │
                    │  - OAuth2 token validation          │
                    │  - Rate limiting                    │
                    │  - JWT verification                 │
                    └────────────────┬────────────────────┘
                                     │ (Token validated at edge)
                                     │ (Forward: X-User-Id, X-User-Roles)
                                     ▼
                    ┌─────────────────────────────────────┐
                    │         DeviceHub API               │
                    │  - Trust gateway headers            │
                    │  - Role-based authorization         │
                    │  - Method-level security            │
                    └─────────────────────────────────────┘
```

**Option 1: JWT validation at gateway (recommended for microservices):**

Gateway handles OAuth2/JWT validation. DeviceHub trusts headers from gateway:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // For @PreAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new GatewayAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}

// Filter that trusts gateway headers
public class GatewayAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String roles = request.getHeader("X-User-Roles");

        if (userId != null && roles != null) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

            Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
```

**Option 2: JWT validation in app (simpler, single service):**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
```

```properties
# application.properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.company.com
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.company.com/.well-known/jwks.json
```

**Method-level security for fine-grained control:**

```java
@Service
public class DeviceService {

    @PreAuthorize("hasRole('ADMIN') or @deviceSecurityService.isOwner(#id, principal)")
    public void delete(Long id) {
        // Only admin or device owner can delete
    }

    @PostAuthorize("returnObject.ownerId == principal.id or hasRole('ADMIN')")
    public DeviceResponse findById(Long id) {
        // Filter result based on ownership
    }
}
```

### 🎤 Interview Sound Bite

> "Currently there's no authentication—anyone can DELETE devices. For quick implementation, I'd add Spring Security with JWT validation. In a microservices architecture, I'd validate tokens at the API gateway and forward trusted headers to internal services. For authorization, I'd use method-level security with `@PreAuthorize` to enforce role-based access and ownership checks."

---

## Q14: Pagination in Spring

### 🧒 Like I'm 8 (ELI8)

Imagine a book with 1000 pages. You can't read all 1000 pages at once!

Instead, you read page 1, then page 2, then page 3...

**Pagination** is reading data one "page" at a time. Instead of getting ALL 43 million devices, you get 20 devices, then the next 20, then the next 20...

### 👶 Junior Developer Level

**Spring Data makes pagination EASY:**

**Step 1: Repository already supports it!**

```java
// JpaRepository already has:
Page<Device> findAll(Pageable pageable);
Page<Device> findByState(DeviceState state, Pageable pageable);
```

**Step 2: Controller accepts pagination parameters:**

```java
@GetMapping
public ResponseEntity<Page<DeviceResponse>> listDevices(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "creationTime,desc") String[] sort) {

    // Cap size to prevent abuse
    size = Math.min(size, 100);

    // Create Pageable from parameters
    Sort sortOrder = Sort.by(Sort.Direction.DESC, "creationTime");
    Pageable pageable = PageRequest.of(page, size, sortOrder);

    Page<DeviceResponse> devices = deviceService.findAll(pageable);
    return ResponseEntity.ok(devices);
}
```

**Step 3: Client calls with pagination:**

```bash
# Get first page (20 items)
curl "http://localhost:8080/api/devices?page=0&size=20"

# Get second page
curl "http://localhost:8080/api/devices?page=1&size=20"

# Get sorted by name
curl "http://localhost:8080/api/devices?page=0&size=20&sort=name,asc"
```

**Response includes pagination metadata:**

```json
{
  "content": [
    { "id": 1, "name": "iPhone", ... },
    { "id": 2, "name": "Galaxy", ... }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 43000000,
  "totalPages": 2150000,
  "last": false,
  "first": true
}
```

### 🎓 Senior Developer Level

**Problem: Offset pagination is SLOW on deep pages!**

```sql
-- Page 0: Fast
SELECT * FROM devices ORDER BY creation_time DESC LIMIT 20 OFFSET 0;

-- Page 50,000: SLOW! Database must skip 1,000,000 rows
SELECT * FROM devices ORDER BY creation_time DESC LIMIT 20 OFFSET 1000000;
```

**Solution: Keyset (Cursor) Pagination**

Instead of "skip 1 million rows", use: "get rows AFTER this timestamp"

```java
// Repository with keyset pagination
public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("""
        SELECT d FROM Device d
        WHERE d.creationTime < :cursor
        ORDER BY d.creationTime DESC
        """)
    List<Device> findAfterCursor(
        @Param("cursor") LocalDateTime cursor,
        Pageable pageable
    );
}

// Service
public CursorPage<DeviceResponse> findAllCursor(LocalDateTime cursor, int size) {
    Pageable pageable = PageRequest.of(0, size + 1);  // Fetch one extra to check hasNext

    List<Device> devices = cursor == null
        ? deviceRepository.findAll(PageRequest.of(0, size + 1, Sort.by("creationTime").descending())).getContent()
        : deviceRepository.findAfterCursor(cursor, pageable);

    boolean hasNext = devices.size() > size;
    if (hasNext) {
        devices = devices.subList(0, size);
    }

    LocalDateTime nextCursor = hasNext
        ? devices.get(devices.size() - 1).getCreationTime()
        : null;

    return new CursorPage<>(
        devices.stream().map(this::toResponse).toList(),
        nextCursor,
        hasNext
    );
}

// DTO for cursor pagination
public record CursorPage<T>(
    List<T> content,
    LocalDateTime nextCursor,
    boolean hasNext
) {}
```

**Controller:**

```java
@GetMapping
public ResponseEntity<CursorPage<DeviceResponse>> listDevices(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime cursor,
        @RequestParam(defaultValue = "20") int size) {

    size = Math.min(size, 100);
    return ResponseEntity.ok(deviceService.findAllCursor(cursor, size));
}
```

**Client usage:**

```bash
# First request (no cursor)
curl "http://localhost:8080/api/devices?size=20"
# Response includes: "nextCursor": "2026-01-24T10:30:00"

# Next page (use the cursor)
curl "http://localhost:8080/api/devices?size=20&cursor=2026-01-24T10:30:00"
```

**Performance comparison:**

| Method     | Page 1 | Page 50,000 |
| ---------- | ------ | ----------- |
| **Offset** | 5ms    | 2,500ms     |
| **Keyset** | 5ms    | 5ms         |

Keyset is O(1) regardless of page depth!

### 🎤 Interview Sound Bite

> "Spring Data supports pagination out of the box via `Pageable` and `Page<T>`. The controller accepts `page` and `size` parameters, and the response includes total counts. However, offset pagination degrades on deep pages—accessing page 50,000 of 43M rows requires scanning 1 million rows. For production, I'd implement keyset pagination using a timestamp cursor, which maintains O(1) performance regardless of how deep you paginate."

---

## Q15: Async Logging with AsyncAppender

### 🧒 Like I'm 8 (ELI8)

Imagine you're playing a game, and every time something happens, you have to STOP and write it in your diary. The game freezes while you write!

**Async logging** is like having a helper. You TELL them what to write, and they write it later while you keep playing. The game never freezes!

### 👶 Junior Developer Level

**The problem with synchronous logging:**

```java
log.info("Processing device {}", id);
// ↑ This line BLOCKS until the log is written to disk/console!
```

If your disk is slow or you're logging to a file:
- Each log statement takes 1-5ms
- 100 log statements = 100-500ms added to request!

**Async logging with Logback:**

```xml
<!-- logback-spring.xml -->
<configuration>
    <!-- The actual appender that writes to console -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Async wrapper - logs are queued and written in background thread -->
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="CONSOLE"/>
        <queueSize>512</queueSize>           <!-- Buffer 512 log events -->
        <discardingThreshold>0</discardingThreshold>  <!-- Never discard -->
        <neverBlock>true</neverBlock>        <!-- Don't block if queue full -->
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC"/>  <!-- Use async appender -->
    </root>
</configuration>
```

**How it works:**

```
Your code                     Background thread
    │                              │
    ▼                              │
log.info("Hello")                  │
    │                              │
    ▼                              │
[Put in queue] ─────────────────►  │
    │                              ▼
    │ (returns immediately)    [Write to console]
    │                              │
    ▼                              │
(continues processing)             │
```

**DeviceHub already has this!**

```xml
<!-- From logback-spring.xml in DeviceHub -->
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="CONSOLE"/>
</appender>
```

### 🎓 Senior Developer Level

**AsyncAppender configuration parameters:**

| Parameter             | Default | Recommended   | Purpose                        |
| --------------------- | ------- | ------------- | ------------------------------ |
| `queueSize`           | 256     | 512-1024      | Buffer capacity                |
| `discardingThreshold` | 20%     | 0             | Drop logs when queue X% full   |
| `neverBlock`          | false   | true for REST | Don't block if queue full      |
| `includeCallerData`   | false   | false         | Include class/line (expensive) |

**Trade-offs:**

| Setting              | Pros               | Cons                             |
| -------------------- | ------------------ | -------------------------------- |
| **neverBlock=true**  | Never adds latency | May lose logs under extreme load |
| **neverBlock=false** | No log loss        | Can add latency if queue fills   |
| **Large queue**      | Handle bursts      | Uses more memory                 |
| **Small queue**      | Less memory        | More likely to drop/block        |

**Monitoring queue health:**

```java
@Scheduled(fixedRate = 60000)
public void monitorAsyncAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    AsyncAppender asyncAppender = (AsyncAppender) context.getLogger("ROOT")
        .getAppender("ASYNC");

    int queueSize = asyncAppender.getQueueSize();
    int remainingCapacity = asyncAppender.getRemainingCapacity();
    int discarded = asyncAppender.getNumberOfDiscardedMessages();

    if (remainingCapacity < queueSize * 0.2) {
        log.warn("Async log queue nearly full: {}/{}",
            queueSize - remainingCapacity, queueSize);
    }
    if (discarded > 0) {
        log.error("Discarded {} log messages!", discarded);
    }
}
```

### 🎤 Interview Sound Bite

> "The logback configuration uses `AsyncAppender` to prevent logging I/O from blocking request processing. Log statements are queued and written by a background thread. I configured `neverBlock=true` so requests never wait for logging, with a 512-event queue to handle bursts. The trade-off is potential log loss under extreme load, but for a REST API, latency is more important than capturing every debug message."

---

## Q16: @RestController vs @Controller

### 🧒 Like I'm 8 (ELI8)

**@Controller** is like a librarian who gives you a MAP to find the book yourself.
**@RestController** is like a librarian who HANDS you the book directly.

- Controller: "Here's directions to the 'devices' shelf" (returns a view name)
- RestController: "Here's the actual device data" (returns JSON)

### 👶 Junior Developer Level

**@Controller (for web pages):**

```java
@Controller
public class WebController {

    @GetMapping("/devices")
    public String listDevices(Model model) {
        model.addAttribute("devices", deviceService.findAll());
        return "devices-list";  // Returns VIEW NAME
        // ↑ Spring looks for: templates/devices-list.html
    }
}
```

**@RestController (for APIs):**

```java
@RestController  // = @Controller + @ResponseBody
public class DeviceController {

    @GetMapping("/api/devices")
    public List<DeviceResponse> listDevices() {
        return deviceService.findAll();  // Returns DATA directly
        // ↑ Automatically serialized to JSON
    }
}
```

**@RestController is just shorthand:**

```java
// These are EQUIVALENT:

@RestController
public class DeviceController {
    @GetMapping("/api/devices")
    public List<Device> list() { ... }
}

@Controller
public class DeviceController {
    @GetMapping("/api/devices")
    @ResponseBody  // ← Must add this to each method!
    public List<Device> list() { ... }
}
```

### 🎓 Senior Developer Level

**When to use which:**

| Annotation                      | Use Case                         | Response Type     |
| ------------------------------- | -------------------------------- | ----------------- |
| `@Controller`                   | Server-rendered HTML (Thymeleaf) | View name → HTML  |
| `@RestController`               | REST APIs                        | Object → JSON/XML |
| `@Controller` + `@ResponseBody` | Mix of both in same controller   | Flexible          |

**Mixing in the same application:**

```java
// REST API endpoints
@RestController
@RequestMapping("/api")
public class DeviceApiController {
    @GetMapping("/devices")
    public List<DeviceResponse> listDevices() { ... }
}

// Web UI endpoints
@Controller
public class DeviceWebController {
    @GetMapping("/devices")
    public String listDevicesPage(Model model) {
        model.addAttribute("devices", deviceService.findAll());
        return "devices";  // → templates/devices.html
    }
}
```

**Content negotiation:**

```java
@Controller
public class FlexibleController {

    @GetMapping(value = "/devices/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String getDeviceHtml(@PathVariable Long id, Model model) {
        model.addAttribute("device", deviceService.findById(id));
        return "device-detail";
    }

    @GetMapping(value = "/devices/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DeviceResponse getDeviceJson(@PathVariable Long id) {
        return deviceService.findById(id);
    }
}
```

### 🎤 Interview Sound Bite

> "`@RestController` is a convenience annotation combining `@Controller` and `@ResponseBody`. It marks the class as a web controller where every method's return value is serialized directly to the response body—typically JSON. `@Controller` alone returns view names for server-side rendering with Thymeleaf. DeviceHub uses `@RestController` since it's a pure REST API with no HTML views."

---

## Q17: DDL-auto and NOT NULL on 43M rows

### 🧒 Like I'm 8 (ELI8)

**DDL** (Data Definition Language) = Instructions for building tables.

`ddl-auto=update` is like saying: "Hey computer, look at my Java code and AUTOMATICALLY make the database tables match."

**The problem:** If you add a rule "everyone MUST have a phone number" to a table with 43 million people who DON'T have phone numbers... the database has to check EVERY single person. That takes FOREVER and freezes everything!

### 👶 Junior Developer Level

**What DDL-auto options mean:**

| Value         | Behavior                      | Safe for Production?      |
| ------------- | ----------------------------- | ------------------------- |
| `none`        | Do nothing                    | ✅ Yes (manual migrations) |
| `validate`    | Check schema matches entities | ✅ Yes (read-only)         |
| `update`      | Add missing columns/tables    | ⚠️ Risky                   |
| `create`      | Drop and recreate all tables  | ❌ NO! Data loss!          |
| `create-drop` | Create on start, drop on stop | ❌ NO! Testing only        |

**DeviceHub uses `update`:**

```properties
spring.jpa.hibernate.ddl-auto=update
```

**The NOT NULL problem:**

```java
// You add a new required field:
@Entity
public class Device {
    @Column(nullable = false)  // NOT NULL!
    private String serialNumber;
}

// Hibernate tries to run:
ALTER TABLE devices ADD COLUMN serial_number VARCHAR(255) NOT NULL;
```

**What happens:**

```
Database: "You want NOT NULL? Let me check 43 million rows..."
Database: "Row 1: NULL! Can't add constraint!"
Database: "FAILED! Rolling back..."

Time taken: 45 minutes of table lock
Result: Application fails to start
Impact: All users blocked during this time
```

### 🎓 Senior Developer Level

**Why `ddl-auto=update` is P0-CRITICAL for production:**

1. **Table locks**: ALTER TABLE locks the entire table
2. **No rollback**: If it fails halfway, database is in inconsistent state
3. **Untracked changes**: No record of what changed when
4. **Environment drift**: Dev and prod schemas diverge
5. **No testing**: Can't test migrations before production

**The right approach: Liquibase with 4-phase migration**

**Phase 1: Add column as NULLABLE**

```yaml
# V1_add_serial_number_nullable.yaml
databaseChangeLog:
  - changeSet:
      id: add-serial-number-nullable
      author: herman
      changes:
        - addColumn:
            tableName: devices
            columns:
              - column:
                  name: serial_number
                  type: varchar(255)
                  # Note: No NOT NULL constraint yet!
```

**Phase 2: Deploy code that WRITES to new column**

```java
// New code writes serialNumber
device.setSerialNumber(generateSerialNumber());
// Old rows still have NULL
```

**Phase 3: Backfill existing rows (batched, off-peak)**

```yaml
# V2_backfill_serial_numbers.yaml
databaseChangeLog:
  - changeSet:
      id: backfill-serial-numbers
      author: herman
      changes:
        - sql:
            sql: |
              UPDATE devices
              SET serial_number = 'LEGACY-' || id
              WHERE serial_number IS NULL
              LIMIT 10000;  -- Batched!
```

Or better, a Java migration:

```java
@Component
@RequiredArgsConstructor
public class SerialNumberBackfillJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 2 * * *")  // Run at 2 AM
    public void backfill() {
        int batchSize = 10000;
        int updated;

        do {
            updated = jdbcTemplate.update("""
                UPDATE devices
                SET serial_number = CONCAT('LEGACY-', id)
                WHERE serial_number IS NULL
                LIMIT ?
                """, batchSize);

            log.info("Backfilled {} rows", updated);
            Thread.sleep(1000);  // Pause between batches

        } while (updated == batchSize);

        log.info("Backfill complete!");
    }
}
```

**Phase 4: Add NOT NULL constraint (after backfill complete)**

```yaml
# V3_add_not_null_constraint.yaml
databaseChangeLog:
  - changeSet:
      id: add-serial-number-not-null
      author: herman
      preConditions:
        - onFail: MARK_RAN
        - sqlCheck:
            expectedResult: 0
            sql: SELECT COUNT(*) FROM devices WHERE serial_number IS NULL
      changes:
        - addNotNullConstraint:
            tableName: devices
            columnName: serial_number
```

**Timeline:**

```
Day 1: Deploy V1 (add nullable column)
Day 1: Deploy new code (writes serial number)
Day 2-5: Backfill job runs at night
Day 6: Verify all rows have values
Day 7: Deploy V3 (add NOT NULL)
```

### 🎤 Interview Sound Bite

> "`ddl-auto=update` is dangerous in production because adding a NOT NULL column to 43 million rows causes a table lock while the database validates every row. Instead, I'd use Liquibase with a 4-phase zero-downtime migration: first add the column as nullable, deploy code that writes to it, run a batched backfill job off-peak, then add the NOT NULL constraint only after verifying no nulls exist. This prevents table locks and allows rollback at each phase."

---

## Q18: How Liquibase tracks migrations

### 🧒 Like I'm 8 (ELI8)

Imagine you have a checklist of chores:
- ✓ Clean room (done Monday)
- ✓ Wash dishes (done Tuesday)
- ☐ Take out trash (not done yet)

Liquibase keeps a similar checklist in the database. Before running any chore, it checks: "Did I already do this? Yes? Skip it!"

That's how it knows to run new migrations but not repeat old ones.

### 👶 Junior Developer Level

**Liquibase creates a tracking table:**

```sql
-- Liquibase automatically creates this:
CREATE TABLE DATABASECHANGELOG (
    ID VARCHAR(255),           -- Changeset ID
    AUTHOR VARCHAR(255),       -- Who wrote it
    FILENAME VARCHAR(255),     -- Which file
    DATEEXECUTED TIMESTAMP,    -- When it ran
    MD5SUM VARCHAR(35),        -- Hash of changeset content
    EXECTYPE VARCHAR(10),      -- EXECUTED, FAILED, RERAN
    ...
);
```

**How it works:**

```yaml
# 001-create-devices.yaml
databaseChangeLog:
  - changeSet:
      id: create-devices-table  # ← Unique ID
      author: herman            # ← Author
      changes:
        - createTable:
            tableName: devices
            ...
```

**On first run:**

```
Liquibase: "Checking DATABASECHANGELOG..."
Liquibase: "No entry for 'create-devices-table' by 'herman'"
Liquibase: "Running changeset..."
Liquibase: "Done! Recording in DATABASECHANGELOG"
```

```sql
INSERT INTO DATABASECHANGELOG
VALUES ('create-devices-table', 'herman', '001-create-devices.yaml',
        NOW(), 'abc123...', 'EXECUTED', ...);
```

**On second run:**

```
Liquibase: "Checking DATABASECHANGELOG..."
Liquibase: "Found entry for 'create-devices-table' by 'herman'"
Liquibase: "Already executed, skipping!"
```

### 🎓 Senior Developer Level

**The MD5SUM (checksum) protection:**

```yaml
# If someone modifies an already-executed changeset:
- changeSet:
    id: create-devices-table
    author: herman
    changes:
      - createTable:
          tableName: devices
          columns:
            - column:
                name: id
                type: bigint
            - column:
                name: name
                type: varchar(255)  # Changed from 100 to 255!
```

```
Liquibase: "Checksum mismatch!"
Liquibase: "DATABASECHANGELOG says MD5SUM = abc123"
Liquibase: "Current changeset MD5SUM = def456"
Liquibase: "REFUSING TO RUN! Someone modified an executed changeset!"
```

**This prevents:**
- Silently changing executed migrations
- Divergence between environments
- "Works on my machine" issues

**Handling checksum errors:**

```bash
# Option 1: If change was intentional and safe
liquibase clearChecksums  # Recalculates all checksums

# Option 2: Mark changeset as already run (skip without executing)
liquibase changelogSync

# Option 3: Roll back and redo (if possible)
liquibase rollback --tag=before-change
```

**Lock mechanism for concurrent deployments:**

```sql
-- Liquibase also creates:
CREATE TABLE DATABASECHANGELOGLOCK (
    ID INT,
    LOCKED BOOLEAN,
    LOCKGRANTED TIMESTAMP,
    LOCKEDBY VARCHAR(255)
);
```

When migration runs:
1. Acquire lock (set LOCKED=true)
2. Run migrations
3. Release lock (set LOCKED=false)

If two instances try to migrate simultaneously, one waits!

### 🎤 Interview Sound Bite

> "Liquibase tracks executed migrations in a `DATABASECHANGELOG` table, storing the changeset ID, author, and an MD5 checksum of the content. On startup, it compares this log against the changelog files—new changesets run, executed ones are skipped, and modified ones throw an error to prevent drift. There's also a `DATABASECHANGELOGLOCK` table to prevent concurrent migrations from multiple app instances."

---

## Q19: Migration failure and testing strategy

### 🧒 Like I'm 8 (ELI8)

**Migration failure:** You're halfway through cleaning your room when the vacuum breaks. Now your room is half-clean, half-messy!

**Testing migrations:** Before cleaning your REAL room, you practice cleaning a TOY room first. If something breaks, it's just toys!

### 👶 Junior Developer Level

**What happens when migration fails halfway:**

```yaml
# Bad migration with multiple statements:
- changeSet:
    id: risky-migration
    changes:
      - addColumn:                    # 1. Runs successfully
          tableName: devices
          columns:
            - column:
                name: serial_number
                type: varchar(255)
      - sql:                          # 2. FAILS halfway through
          sql: UPDATE devices SET serial_number = 'X' WHERE id < 1000000000
      - addNotNullConstraint:         # 3. Never runs
          columnName: serial_number
          tableName: devices
```

**The problem:**
- Column exists (step 1 done)
- Some rows updated, some not (step 2 partial)
- No NOT NULL (step 3 didn't run)
- State is inconsistent!

**Liquibase behavior:**
- By default, changesets are **not** transactional for DDL
- DDL (CREATE, ALTER) in most databases auto-commits
- Only DML (INSERT, UPDATE) can be rolled back

**Solution: Use runInTransaction and split changesets:**

```yaml
# Safe: One change per changeset
- changeSet:
    id: add-serial-column
    author: herman
    changes:
      - addColumn:
          tableName: devices
          columns:
            - column:
                name: serial_number
                type: varchar(255)

- changeSet:
    id: backfill-serial-numbers
    author: herman
    runInTransaction: true  # DML only
    changes:
      - sql:
          sql: UPDATE devices SET serial_number = 'LEGACY-' || id
```

### 🎓 Senior Developer Level

**Testing migrations before production:**

**1. Local testing against production-like data:**

```bash
# Dump production schema (no data)
pg_dump --schema-only production_db > schema.sql

# Create test database
psql -c "CREATE DATABASE migration_test"
psql migration_test < schema.sql

# Generate sample data matching production volume
./generate-test-data.sh --rows=100000

# Run migrations
./mvnw liquibase:update -Dspring.profiles.active=migration-test
```

**2. CI/CD pipeline testing:**

```yaml
# .github/workflows/migration-test.yml
jobs:
  test-migrations:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: test_db
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v3

      - name: Apply previous migrations
        run: |
          # Get migrations from main branch
          git checkout main -- src/main/resources/db/changelog
          ./mvnw liquibase:update

      - name: Apply new migrations
        run: |
          git checkout - -- src/main/resources/db/changelog
          ./mvnw liquibase:update

      - name: Verify schema
        run: ./mvnw liquibase:diff  # Compare against expected
```

**3. Rollback testing:**

```yaml
# Each migration should have a rollback:
- changeSet:
    id: add-serial-column
    author: herman
    changes:
      - addColumn:
          tableName: devices
          columns:
            - column:
                name: serial_number
                type: varchar(255)
    rollback:
      - dropColumn:
          tableName: devices
          columnName: serial_number
```

```bash
# Test rollback works
./mvnw liquibase:update       # Apply
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1  # Undo last
./mvnw liquibase:update       # Re-apply
```

**Handling 43M row migration failure:**

```
Migration fails at row 20,000,000
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. DON'T PANIC                                              │
│                                                             │
│ 2. Identify the state:                                      │
│    - Which rows were updated?                               │
│    - What's the error?                                      │
│                                                             │
│ 3. Options:                                                 │
│    a) Resume: Fix query, continue from row 20M              │
│    b) Rollback: Revert changes, fix migration, restart      │
│    c) Forward: Mark as done, fix data separately            │
│                                                             │
│ 4. For large tables, always use batched updates:            │
│    - Process 10,000 rows at a time                          │
│    - Record progress (last processed ID)                    │
│    - Can resume from failure point                          │
└─────────────────────────────────────────────────────────────┘
```

**Batched migration with restart capability:**

```java
// Java-based migration for complex scenarios
@Component
public class SerialNumberMigration implements Liquibase CustomTaskChange {

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection conn = (JdbcConnection) database.getConnection();

        long lastProcessedId = getLastProcessedId();  // From state table
        int batchSize = 10000;

        while (true) {
            int updated = conn.prepareStatement("""
                UPDATE devices
                SET serial_number = CONCAT('LEGACY-', id)
                WHERE id > ? AND serial_number IS NULL
                ORDER BY id
                LIMIT ?
                """)
                .setLong(1, lastProcessedId)
                .setInt(2, batchSize)
                .executeUpdate();

            if (updated == 0) break;

            lastProcessedId += batchSize;
            saveLastProcessedId(lastProcessedId);  // Checkpoint!

            Thread.sleep(100);  // Don't overwhelm DB
        }
    }
}
```

### 🎤 Interview Sound Bite

> "If a migration fails halfway through 43M rows, the key is having resumable, batched operations. I'd split large data migrations into separate changesets, process 10K rows at a time with checkpoints, and record progress in a state table. For testing, I'd run migrations against a production-like dataset in CI before deploying. Each changeset should have a rollback block, and I'd test rollback works before going to production."

---

## Q20: Top 50 Java/Spring Interview Questions

This is a condensed quick-reference. See NOTES_JAVA_QA.md for detailed explanations.

### Core Java

| #   | Question                             | Quick Answer                                                                  |
| --- | ------------------------------------ | ----------------------------------------------------------------------------- |
| 1   | `==` vs `.equals()`                  | `==` compares references, `.equals()` compares values                         |
| 2   | String immutability                  | Strings can't be changed; "modification" creates new object                   |
| 3   | `final` vs `finally` vs `finalize()` | Constant/Inheritance prevention / Try block cleanup / GC hook (deprecated)    |
| 4   | Checked vs unchecked exceptions      | Checked: must handle (IOException). Unchecked: runtime (NullPointerException) |
| 5   | `HashMap` vs `Hashtable`             | HashMap: not synchronized, allows null. Hashtable: synchronized, no null      |
| 6   | ArrayList vs LinkedList              | ArrayList: fast random access. LinkedList: fast insert/delete                 |
| 7   | Interface vs Abstract class          | Interface: contract, multiple inheritance. Abstract: partial implementation   |
| 8   | Garbage collection                   | Automatic memory management. Mark-and-sweep, generational GC                  |
| 9   | Memory areas                         | Heap (objects), Stack (primitives, references), Metaspace (class metadata)    |
| 10  | Serialization                        | Converting object to bytes. `implements Serializable`, `serialVersionUID`     |

### Concurrency

| #   | Question                          | Quick Answer                                                            |
| --- | --------------------------------- | ----------------------------------------------------------------------- |
| 11  | Thread vs Runnable                | Thread: extend class. Runnable: implement interface (preferred)         |
| 12  | `synchronized` keyword            | Mutual exclusion lock. Method or block level                            |
| 13  | `volatile` keyword                | Ensures visibility across threads. No caching                           |
| 14  | Thread-safe collections           | `ConcurrentHashMap`, `CopyOnWriteArrayList`, `BlockingQueue`            |
| 15  | `Executor` framework              | Thread pool management. `ExecutorService`, `ThreadPoolExecutor`         |
| 16  | `CompletableFuture`               | Async programming. `.thenApply()`, `.thenCompose()`, `.exceptionally()` |
| 17  | Deadlock                          | Threads waiting for each other. Prevention: lock ordering               |
| 18  | `ReentrantLock` vs `synchronized` | ReentrantLock: more flexible, try-lock, fair mode                       |
| 19  | `AtomicInteger`                   | Lock-free thread-safe counter. CAS operations                           |
| 20  | Virtual Threads (Java 21)         | Lightweight threads. Millions possible. See Q30 in ADVANCED             |

### Spring Core

| #   | Question                   | Quick Answer                                                           |
| --- | -------------------------- | ---------------------------------------------------------------------- |
| 21  | IoC / Dependency Injection | Container creates objects, injects dependencies                        |
| 22  | `@Autowired` vs `@Inject`  | Both inject dependencies. `@Autowired` is Spring, `@Inject` is JSR-330 |
| 23  | Bean scopes                | singleton (default), prototype, request, session, application          |
| 24  | `@Component` vs `@Bean`    | `@Component`: class annotation. `@Bean`: method in `@Configuration`    |
| 25  | `@Configuration`           | Indicates class contains `@Bean` definitions                           |
| 26  | Spring profiles            | Environment-specific configuration. `@Profile("dev")`                  |
| 27  | `@Value`                   | Inject property values. `@Value("${server.port}")`                     |
| 28  | `@Transactional`           | Declarative transaction management. Rollback on exception              |
| 29  | AOP (Aspect Oriented)      | Cross-cutting concerns. `@Before`, `@After`, `@Around`                 |
| 30  | Application context        | Container holding all beans. `ApplicationContext`                      |

### Spring Boot

| #   | Question                           | Quick Answer                                                           |
| --- | ---------------------------------- | ---------------------------------------------------------------------- |
| 31  | Auto-configuration                 | Automatic bean creation based on classpath. `@EnableAutoConfiguration` |
| 32  | `application.properties` vs `.yml` | Both work. YAML is hierarchical, properties is flat                    |
| 33  | Actuator                           | Production monitoring. `/health`, `/metrics`, `/info`                  |
| 34  | Embedded server                    | Tomcat/Jetty/Undertow included. No WAR deployment needed               |
| 35  | `@SpringBootApplication`           | = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`     |
| 36  | Starter dependencies               | Pre-configured dependency bundles. `spring-boot-starter-web`           |
| 37  | DevTools                           | Hot reload for development. `spring-boot-devtools`                     |
| 38  | Externalized config                | Properties from files, env vars, command line. Priority order          |
| 39  | Health indicators                  | Custom health checks. Implement `HealthIndicator`                      |
| 40  | Graceful shutdown                  | Complete in-flight requests. `server.shutdown=graceful`                |

### Spring Data JPA

| #   | Question                     | Quick Answer                                                          |
| --- | ---------------------------- | --------------------------------------------------------------------- |
| 41  | `JpaRepository`              | CRUD + JPA methods. Extends `CrudRepository`                          |
| 42  | Query methods                | `findByName`, `findByStateAndBrand`. Method name = query              |
| 43  | `@Query`                     | Custom JPQL or native SQL. `@Query("SELECT d FROM Device d")`         |
| 44  | N+1 problem                  | Lazy loading causes extra queries. Use `JOIN FETCH` or `@EntityGraph` |
| 45  | Lazy vs Eager loading        | Lazy: load on access. Eager: load immediately                         |
| 46  | `@Transactional` propagation | REQUIRED (default), REQUIRES_NEW, NESTED, etc.                        |
| 47  | Optimistic locking           | `@Version` field. Detects concurrent modifications                    |
| 48  | Auditing                     | `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`                     |
| 49  | Projections                  | Interface/DTO projections. Select specific columns                    |
| 50  | Specifications               | Type-safe dynamic queries. `Specification<Device>`                    |

### Concurrency-Safe Collections Quick Reference

```java
// Thread-safe Map
ConcurrentHashMap<String, Device> map = new ConcurrentHashMap<>();

// Thread-safe List (read-heavy)
CopyOnWriteArrayList<Device> list = new CopyOnWriteArrayList<>();

// Thread-safe Queue
BlockingQueue<Device> queue = new LinkedBlockingQueue<>();

// Thread-safe Set
Set<Device> set = ConcurrentHashMap.newKeySet();

// Synchronized wrappers (less efficient)
List<Device> syncList = Collections.synchronizedList(new ArrayList<>());
```

---

## Q21: HikariCP Connection Pool

### 🧒 Like I'm 8 (ELI8)

Imagine there are only 10 phones in the school office. When you need to call your mom, you borrow a phone, make the call, and give it back.

If 100 kids all want to call at the same time, 90 kids have to WAIT. If a kid keeps the phone too long, everyone else gets mad.

**HikariCP** is the office manager who handles giving out phones (database connections) and making sure nobody hogs them.

### 👶 Junior Developer Level

**Why connection pooling?**

Opening a database connection is SLOW:
1. TCP handshake (network)
2. SSL negotiation (security)
3. Authentication (username/password)
4. Protocol negotiation

**Time:** 50-200ms per connection!

**Without pooling:**
```
Request 1: Open connection (100ms) → Query (5ms) → Close
Request 2: Open connection (100ms) → Query (5ms) → Close
Request 3: Open connection (100ms) → Query (5ms) → Close
Total: 315ms
```

**With pooling:**
```
Startup: Open 10 connections (once)

Request 1: Borrow connection → Query (5ms) → Return
Request 2: Borrow connection → Query (5ms) → Return
Request 3: Borrow connection → Query (5ms) → Return
Total: 15ms
```

**HikariCP is the default in Spring Boot** (fastest pool available).

**Configuration in DeviceHub:**

```properties
# application.properties

# How many connections to keep (default 10)
spring.datasource.hikari.maximum-pool-size=30

# Minimum connections to maintain when idle
spring.datasource.hikari.minimum-idle=10

# How long to wait for a connection before timeout (ms)
spring.datasource.hikari.connection-timeout=30000

# How long an idle connection stays in pool before removal (ms)
spring.datasource.hikari.idle-timeout=600000

# Maximum lifetime of a connection (ms) - helps with DB failover
spring.datasource.hikari.max-lifetime=1800000

# Pool name (for monitoring)
spring.datasource.hikari.pool-name=DeviceHubPool
```

### 🎓 Senior Developer Level

**Parameter deep dive:**

| Parameter                  | Default     | Recommended             | Why                                 |
| -------------------------- | ----------- | ----------------------- | ----------------------------------- |
| `maximum-pool-size`        | 10          | `(cores * 2) + spindle` | Formula for optimal connections     |
| `minimum-idle`             | Same as max | 10                      | Keep some ready, but not all        |
| `connection-timeout`       | 30000       | 30000                   | How long to wait for connection     |
| `idle-timeout`             | 600000      | 600000                  | Close idle connections after 10 min |
| `max-lifetime`             | 1800000     | 1800000                 | Rotate connections every 30 min     |
| `validation-timeout`       | 5000        | 5000                    | Connection health check timeout     |
| `leak-detection-threshold` | 0           | 60000                   | Log if connection held > 1 min      |

**The connection count formula:**

```
pool-size = (core_count * 2) + effective_spindle_count
```

For a server with 16 cores and SSD (spindle = 1):
```
pool-size = (16 * 2) + 1 = 33
```

**Why not 100 connections?**
- More connections ≠ better performance
- Database has its own limits (CPU, memory)
- Context switching overhead
- 30-50 is usually optimal

**Virtual Threads consideration:**

With Virtual Threads enabled, millions of threads can spawn. But HikariCP still has a fixed pool!

```
Virtual Thread 1 ──┐
Virtual Thread 2 ──┤
Virtual Thread 3 ──┼──► HikariCP Pool (30 connections)
...                │
Virtual Thread 1000├──► WAITING... (pool exhausted)
```

**Monitor for this:**

```java
@Scheduled(fixedRate = 60000)
public void monitorConnectionPool() {
    HikariDataSource dataSource = (HikariDataSource) this.dataSource;
    HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();

    log.info("Pool stats: active={}, idle={}, waiting={}, total={}",
        pool.getActiveConnections(),
        pool.getIdleConnections(),
        pool.getThreadsAwaitingConnection(),
        pool.getTotalConnections());

    if (pool.getThreadsAwaitingConnection() > 10) {
        log.warn("High connection wait queue! Consider increasing pool size.");
    }
}
```

**Metrics exposed for Prometheus:**

```properties
management.metrics.export.prometheus.enabled=true
```

```
# Available metrics:
hikaricp_connections_active{pool="DeviceHubPool"} 15
hikaricp_connections_idle{pool="DeviceHubPool"} 15
hikaricp_connections_pending{pool="DeviceHubPool"} 0
hikaricp_connections_timeout_total{pool="DeviceHubPool"} 0
```

**Alert on connection exhaustion:**

```yaml
# Prometheus alert
- alert: HikariPoolExhausted
  expr: hikaricp_connections_pending > 5
  for: 1m
  labels:
    severity: warning
  annotations:
    summary: "Connection pool has waiting requests"
```

### 🎤 Interview Sound Bite

> "HikariCP is the default connection pool in Spring Boot—it manages database connections to avoid the overhead of opening new ones per request. I've configured a pool of 30 connections based on the formula `(cores × 2) + 1`. Key parameters include `connection-timeout` (fail fast if pool exhausted), `max-lifetime` (rotate connections for failover), and `leak-detection-threshold` (log if code holds connections too long). With Virtual Threads enabled, I'd monitor `hikaricp_connections_pending` to detect pool exhaustion."

---

## Q22: ETag Headers for Optimistic Locking

### 🧒 Like I'm 8 (ELI8)

Imagine you're editing a drawing with a friend online. The drawing has a "version number" stamped on it: v5.

You download v5 and start coloring. Your friend downloads v5 and starts coloring too.

Your friend saves first → Drawing becomes v6.
You try to save → "Wait! You're saving changes to v5, but it's now v6! Someone else changed it. Please refresh and try again."

**ETag** is that version number, but for web pages and API data.

### 👶 Junior Developer Level

**ETag = Entity Tag**

It's a fingerprint for a resource. When the resource changes, the ETag changes.

**How it works:**

```http
# 1. Client requests a device
GET /api/devices/1
→ Response:
  200 OK
  ETag: "version-42"
  {"id": 1, "name": "iPhone", "version": 42}

# 2. Client wants to update - sends ETag back
PUT /api/devices/1
If-Match: "version-42"
{"name": "New Name"}

# 3a. If version still 42 → Success
→ 200 OK
  ETag: "version-43"
  {"id": 1, "name": "New Name", "version": 43}

# 3b. If version changed (conflict) → Fail
→ 412 Precondition Failed
  "Resource was modified since you last fetched it"
```

**Implementation:**

```java
@GetMapping("/{id}")
public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long id) {
    DeviceResponse device = deviceService.findById(id);

    return ResponseEntity.ok()
        .eTag("\"" + device.version() + "\"")  // ETag from @Version
        .body(device);
}

@PutMapping("/{id}")
public ResponseEntity<DeviceResponse> updateDevice(
        @PathVariable Long id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody DeviceUpdateRequest request) {

    if (ifMatch != null) {
        long expectedVersion = parseETag(ifMatch);
        DeviceResponse device = deviceService.findById(id);

        if (device.version() != expectedVersion) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(null);  // 412
        }
    }

    DeviceResponse updated = deviceService.update(id, request);
    return ResponseEntity.ok()
        .eTag("\"" + updated.version() + "\"")
        .body(updated);
}

private long parseETag(String etag) {
    // ETag format: "version-42" or just "42"
    return Long.parseLong(etag.replaceAll("[^0-9]", ""));
}
```

### 🎓 Senior Developer Level

**ETags enable HTTP-level caching too:**

```http
# Conditional GET - avoid transferring unchanged data
GET /api/devices/1
If-None-Match: "version-42"

# If version is still 42:
→ 304 Not Modified (empty body, saves bandwidth!)

# If version changed:
→ 200 OK
  ETag: "version-43"
  {...new data...}
```

**Spring's built-in ETag support:**

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
            new FilterRegistrationBean<>();
        registration.setFilter(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
```

**Shallow vs Deep ETags:**

| Type        | How it works          | Pros                        | Cons                          |
| ----------- | --------------------- | --------------------------- | ----------------------------- |
| **Shallow** | Hash of response body | Automatic, no code changes  | Still processes request fully |
| **Deep**    | Based on data version | Efficient, skips processing | Requires `@Version` field     |

**Deep ETag with ResponseEntity:**

```java
@GetMapping("/{id}")
public ResponseEntity<DeviceResponse> getDevice(
        @PathVariable Long id,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

    // First, just get the version (cheap)
    Long currentVersion = deviceService.getVersion(id);
    String etag = "\"" + currentVersion + "\"";

    // If client has current version, return 304
    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
            .eTag(etag)
            .build();  // No body!
    }

    // Otherwise, fetch full resource (more expensive)
    DeviceResponse device = deviceService.findById(id);
    return ResponseEntity.ok()
        .eTag(etag)
        .body(device);
}
```

**Combined with optimistic locking:**

```java
@PutMapping("/{id}")
public ResponseEntity<DeviceResponse> updateDevice(
        @PathVariable Long id,
        @RequestHeader("If-Match") String ifMatch,
        @RequestBody DeviceUpdateRequest request) {

    long expectedVersion = parseETag(ifMatch);

    try {
        DeviceResponse updated = deviceService.update(id, request, expectedVersion);
        return ResponseEntity.ok()
            .eTag("\"" + updated.version() + "\"")
            .body(updated);
    } catch (OptimisticLockException e) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(null);
    }
}

// Service method checks version
public DeviceResponse update(Long id, DeviceUpdateRequest request, Long expectedVersion) {
    Device device = deviceRepository.findById(id).orElseThrow();

    if (!device.getVersion().equals(expectedVersion)) {
        throw new OptimisticLockException("Version mismatch");
    }

    // Update and save (JPA will also check @Version)
    device.setName(request.name());
    return toResponse(deviceRepository.save(device));
}
```

### 🎤 Interview Sound Bite

> "ETags provide HTTP-level optimistic locking and caching. For writes, clients send `If-Match` with the ETag—if it doesn't match the current version, we return 412 Precondition Failed. For reads, clients send `If-None-Match`—if the resource hasn't changed, we return 304 Not Modified, saving bandwidth. I'd derive the ETag from the `@Version` field to avoid hashing the entire response body."

---

## Q23: OptimisticLockException Handling

### 🧒 Like I'm 8 (ELI8)

You and your sister are both trying to change the family calendar. You both see "Saturday: Nothing planned."

You write "Saturday: Beach!"
Sister writes "Saturday: Zoo!"

The calendar is smart. It says: "Hey! Both of you tried to change the same thing. I can only accept one. Sister's change went first, so you need to look at the new calendar and try again."

That's what `OptimisticLockException` does—it stops you from accidentally overwriting someone else's changes.

### 👶 Junior Developer Level

**When does it happen?**

```java
@Entity
public class Device {
    @Id
    private Long id;

    @Version
    private Long version;  // JPA tracks this
}
```

**Scenario:**
1. Request A reads Device (version=1)
2. Request B reads Device (version=1)
3. Request B updates Device → version becomes 2
4. Request A tries to update → JPA sees version mismatch!

```
Expected: version=1 (what Request A read)
Actual:   version=2 (what's in database)
→ OptimisticLockException!
```

**Adding exception handler:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockException ex,
            HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Resource was modified by another request. Please refresh and retry."
        );
        problem.setType(URI.create("https://devicehub.api/errors/concurrent-modification"));
        problem.setTitle("Concurrent Modification");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("retryable", true);
        problem.setProperty("suggestion", "Fetch the latest version and resubmit your changes");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
```

**Response:**

```json
{
  "type": "https://devicehub.api/errors/concurrent-modification",
  "title": "Concurrent Modification",
  "status": 409,
  "detail": "Resource was modified by another request. Please refresh and retry.",
  "instance": "/api/devices/1",
  "retryable": true,
  "suggestion": "Fetch the latest version and resubmit your changes"
}
```

### 🎓 Senior Developer Level

**Which HTTP status code?**

| Status                      | When to use                                                |
| --------------------------- | ---------------------------------------------------------- |
| **409 Conflict**            | ✅ Resource state conflict (optimistic lock, business rule) |
| **412 Precondition Failed** | ETag/If-Match mismatch                                     |
| **423 Locked**              | Resource locked by another user (pessimistic lock)         |
| **429 Too Many Requests**   | Rate limiting (not concurrency)                            |

**Why 409, not 412?**

- **412 Precondition Failed**: Client sent a precondition header (`If-Match`) that wasn't met
- **409 Conflict**: Request conflicts with current state of the resource

If the client used `If-Match` header → 412
If the server detected the conflict internally (no client precondition) → 409

**Comprehensive handler with logging:**

```java
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<ProblemDetail> handleOptimisticLock(
        OptimisticLockException ex,
        HttpServletRequest request) {

    String requestId = MDC.get("requestId");
    log.warn("[{}] Optimistic lock conflict at {}: {}",
        requestId, request.getRequestURI(), ex.getMessage());

    // Increment metric
    meterRegistry.counter("optimistic_lock_conflicts_total",
        "endpoint", request.getRequestURI()).increment();

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "Resource was modified by another request. Please refresh and retry."
    );
    problem.setType(URI.create("https://devicehub.api/errors/concurrent-modification"));
    problem.setTitle("Concurrent Modification");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("retryable", true);
    problem.setProperty("requestId", requestId);

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header("X-Request-Id", requestId)
        .body(problem);
}
```

**Client-side handling:**

```javascript
// Retry with exponential backoff
async function updateDevice(id, data, maxRetries = 3) {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      // 1. Fetch latest version
      const current = await fetch(`/api/devices/${id}`);
      const device = await current.json();

      // 2. Apply changes
      const merged = { ...device, ...data };

      // 3. Update with version
      const response = await fetch(`/api/devices/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'If-Match': current.headers.get('ETag')
        },
        body: JSON.stringify(merged)
      });

      if (response.ok) return await response.json();

      if (response.status === 409 || response.status === 412) {
        console.log(`Conflict, retry ${attempt + 1}/${maxRetries}`);
        await sleep(Math.pow(2, attempt) * 100);  // Exponential backoff
        continue;
      }

      throw new Error(`Update failed: ${response.status}`);

    } catch (error) {
      if (attempt === maxRetries - 1) throw error;
    }
  }
}
```

### 🎤 Interview Sound Bite

> "I handle `OptimisticLockException` in the `GlobalExceptionHandler`, returning 409 Conflict with a RFC 7807 Problem Detail that includes `retryable: true`. The client should fetch fresh data and resubmit. I also log the conflict and increment a metric to monitor collision rates—if they're high, we might need to review the update patterns or consider pessimistic locking for specific hot resources."

---

## Q24: Validation Flow and GlobalExceptionHandler

### 🧒 Like I'm 8 (ELI8)

Imagine a bouncer at a party checking invitations.

1. **First check:** "Is this even an invitation?" (Is it valid JSON?)
2. **Second check:** "Is your name on the list?" (Are all required fields present?)
3. **Third check:** "Are you old enough?" (Does the data meet our rules?)

If any check fails, the bouncer says WHY and sends you away. That's what validation does!

### 👶 Junior Developer Level

**The validation flow:**

```
Request arrives with JSON body
        │
        ▼
┌─────────────────────────────────────┐
│ 1. Jackson Deserialization          │
│    - Is it valid JSON?              │
│    - Can it map to DTO?             │
│    ✗ Fail → HttpMessageNotReadable  │
└───────────────┬─────────────────────┘
                │ ✓
                ▼
┌─────────────────────────────────────┐
│ 2. Bean Validation (@Valid)         │
│    - @NotBlank, @Size, @NotNull     │
│    - @Pattern, @Email, @Min, @Max   │
│    ✗ Fail → MethodArgumentNotValid  │
└───────────────┬─────────────────────┘
                │ ✓
                ▼
┌─────────────────────────────────────┐
│ 3. Business Validation (Service)    │
│    - Is device IN_USE?              │
│    - Does user have permission?     │
│    ✗ Fail → Custom exceptions       │
└───────────────┬─────────────────────┘
                │ ✓
                ▼
         Success! Process request
```

**GlobalExceptionHandler catches all of these:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. JSON parsing errors
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleBadJson(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid JSON format"
        );
        return ResponseEntity.badRequest().body(problem);
    }

    // 2. Bean validation errors (@Valid fails)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        problem.setProperty("errors", errors);

        return ResponseEntity.badRequest().body(problem);
    }

    // 3. Business rule violations
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
```

**Validation DOES go through GlobalExceptionHandler:**

```java
@PostMapping
public ResponseEntity<DeviceResponse> createDevice(
        @Valid @RequestBody DeviceCreateRequest request) {  // @Valid triggers validation
    // If validation fails, MethodArgumentNotValidException is thrown
    // GlobalExceptionHandler catches it
    // 400 Bad Request is returned
    return ResponseEntity.status(201).body(deviceService.create(request));
}
```

### 🎓 Senior Developer Level

**Annotation validation vs method validation:**

```java
// Option 1: Annotation-based (declarative)
public record DeviceCreateRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name too long")
    String name,

    @NotBlank
    String brand
) {}

// Option 2: Method-based (programmatic)
@Service
public class DeviceService {

    public DeviceResponse create(DeviceCreateRequest request) {
        // Manual validation
        if (request.name() == null || request.name().isBlank()) {
            throw new ValidationException("Name is required");
        }
        if (request.name().length() > 255) {
            throw new ValidationException("Name too long");
        }
        // ...
    }
}
```

**When to use which:**

| Approach         | Best for                  | Pros                        | Cons                  |
| ---------------- | ------------------------- | --------------------------- | --------------------- |
| **Annotations**  | Simple field rules        | Declarative, DRY, automatic | Limited complex logic |
| **Programmatic** | Complex/conditional logic | Full control                | More code, scattered  |
| **Both**         | Production systems        | Layered validation          | Slight overhead       |

**Complex validation with custom validator:**

```java
// Cross-field validation
@Constraint(validatedBy = ValidDeviceStateTransition.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDeviceStateTransition {
    String message() default "Invalid state transition";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidDeviceStateTransition, DeviceUpdateRequest> {
        @Override
        public boolean isValid(DeviceUpdateRequest request, ConstraintValidatorContext context) {
            // Complex logic: Can't go from INACTIVE to IN_USE
            if (request.currentState() == INACTIVE && request.newState() == IN_USE) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "Cannot transition from INACTIVE to IN_USE")
                    .addPropertyNode("state")
                    .addConstraintViolation();
                return false;
            }
            return true;
        }
    }
}
```

**Complete exception handler with all validation errors:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bean Validation (@Valid on @RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        return buildValidationResponse(ex.getBindingResult());
    }

    // Bean Validation (@Valid on @PathVariable, @RequestParam)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage
            ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    // Type mismatch (e.g., "abc" for Long id)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType().getSimpleName())
        );
        return ResponseEntity.badRequest().body(problem);
    }

    private ResponseEntity<ProblemDetail> buildValidationResponse(BindingResult result) {
        Map<String, String> errors = result.getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Invalid value",
                (e1, e2) -> e1  // Keep first if duplicate
            ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields failed validation");
        problem.setProperty("errors", errors);

        return ResponseEntity.badRequest().body(problem);
    }
}
```

### 🎤 Interview Sound Bite

> "Validation flows through multiple layers: Jackson deserializes JSON, then `@Valid` triggers Bean Validation annotations, then business logic validates rules. All exceptions are caught by `GlobalExceptionHandler` and converted to RFC 7807 Problem Details. For 400 errors, I include a structured `errors` map with field names and messages. This gives clients clear, actionable feedback like `{\"name\": \"must not be blank\", \"brand\": \"must be between 1 and 100 characters\"}`."

---

## 🔗 Cross-References

| Topic                | Related Topics                                     |
| -------------------- | -------------------------------------------------- |
| Q13 (Auth)           | Q26 (OAuth2/JWT in ADVANCED)                       |
| Q14 (Pagination)     | Q1 (OOM in CORE)                                   |
| Q16 (Controllers)    | Q13 (Security config)                              |
| Q17 (DDL-auto)       | Q18 (Liquibase), Q19 (Migrations)                  |
| Q21 (HikariCP)       | Q30 (Virtual Threads in ADVANCED)                  |
| Q22 (ETag)           | Q23 (OptimisticLock), Q3 (Race Conditions in CORE) |
| Q23 (OptimisticLock) | Q3 (Race Conditions in CORE)                       |
| Q24 (Validation)     | Q4 (DoS Payload in CORE), Q5 (PUT nulling in CORE) |

---

*See also:*
- **NOTES_FAQ_CORE.md** for Questions 1-12
- **NOTES_FAQ_ADVANCED.md** for Questions 25-34 + Company Research
- **NOTES_CODE.md** for implementation examples
