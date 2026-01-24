# DeviceHub API - Interview FAQ: Core Concepts

**Purpose**: Deep understanding of foundational concepts for interview defense.
**Format**: Each question answered at 3 levels (ELI8 → Junior → Senior)
**Cross-references**: Links to NOTES_CODE.md for implementation details

---

## 📚 Table of Contents

1. [Q1: What is OOM on large datasets?](#q1-what-is-oom-on-large-datasets)
2. [Q2: How can no rate limit cause DDoS?](#q2-how-can-no-rate-limit-cause-ddos)
3. [Q3: Race condition - Lost updates & business rule bypass](#q3-race-condition---lost-updates--business-rule-bypass)
4. [Q4: DoS via payload size (no string limits)](#q4-dos-via-payload-size-no-string-limits)
5. [Q5: PUT allows nulling required fields](#q5-put-allows-nulling-required-fields)
6. [Q6: H2 scalability + SQL vs NoSQL](#q6-h2-scalability--sql-vs-nosql)
7. [Q7: SLIs, SLOs, and metrics](#q7-slis-slos-and-metrics)
8. [Q8: Caching strategy (multi-layer)](#q8-caching-strategy-multi-layer)
9. [Q9: MDC and log correlation](#q9-mdc-and-log-correlation)
10. [Q10: Idempotency keys for POST](#q10-idempotency-keys-for-post)
11. [Q11: Contract tests and test strategy](#q11-contract-tests-and-test-strategy)
12. [Q12: OSIV Disabled](#q12-osiv-disabled)

---

## Q1: What is OOM on large datasets?

### 🧒 Like I'm 8 (ELI8)

Imagine you have a toy box that can only hold 100 toys. If someone dumps 1 million toys into it, the box explodes! **OOM** is when your computer's memory "toy box" gets too full and crashes.

In DeviceHub: Someone asks for ALL devices, the computer tries to hold 43 million device cards in its hands at once, and its hands break.

### 👶 Junior Developer Level

**OOM = Out Of Memory Error**

When your Java application tries to use more memory than the JVM (Java Virtual Machine) has available, it throws `java.lang.OutOfMemoryError` and usually crashes.

**The problem in DeviceHub:**

```java
// DeviceService.java - Current implementation
public List<DeviceResponse> findAll() {
    return deviceRepository.findAll()  // Loads ALL 43 million devices into RAM
            .stream()
            .map(this::toResponse)
            .toList();  // Creates another list of 43M objects
}
```

**What happens step by step:**

1. Database returns 43M rows
2. JPA creates 43M `Device` objects (~500 bytes each = **21.5 GB**)
3. You map to 43M `DeviceResponse` objects (~200 bytes each = **8.6 GB**)
4. Total: **~30 GB needed**, but JVM might only have 512 MB
5. 💥 `OutOfMemoryError: Java heap space`

**Common mistake:** Thinking "it works on my laptop with 10 records" means it'll work in production.

**The fix - Pagination:**

```java
// Instead of List, return Page with size limits
public Page<DeviceResponse> findAll(Pageable pageable) {
    return deviceRepository.findAll(pageable).map(this::toResponse);
}
```

### 🎓 Senior Developer Level

**Production implications at 43M devices:**

| Scenario           | Memory Required | Typical JVM Heap | Result              |
| ------------------ | --------------- | ---------------- | ------------------- |
| 1,000 devices      | ~5 MB           | 512 MB           | ✅ Works             |
| 100,000 devices    | ~500 MB         | 512 MB           | ⚠️ Slow, GC pressure |
| 43,000,000 devices | ~30 GB          | 512 MB - 4 GB    | 💥 OOM Crash         |

**Why it's worse than just crashing:**

1. **GC Thrashing**: Before OOM, the JVM desperately runs garbage collection, causing 100% CPU and frozen requests
2. **Cascading failure**: One bad request takes down the entire instance
3. **Health check fails**: Kubernetes restarts the pod, losing in-flight requests
4. **Memory pressure affects other services** if co-located

**Trade-offs with pagination:**

| Approach                                           | Pros                   | Cons                                      |
| -------------------------------------------------- | ---------------------- | ----------------------------------------- |
| **Offset pagination** (`?page=5&size=20`)          | Simple, familiar       | Slow on deep pages (OFFSET 1M)            |
| **Cursor pagination** (`?after=abc123`)            | Consistent performance | More complex, can't jump to page N        |
| **Keyset pagination** (`?createdAfter=2026-01-01`) | Very fast              | Requires indexed column, no random access |

**For 43M devices, I'd use keyset pagination:**

```java
@Query("SELECT d FROM Device d WHERE d.creationTime > :cursor ORDER BY d.creationTime LIMIT :size")
List<Device> findAfterCursor(@Param("cursor") LocalDateTime cursor, @Param("size") int size);
```

### 💻 Code Example

See **NOTES_CODE.md §4** for full pagination implementation.

```java
// Controller with pagination
@GetMapping
public ResponseEntity<Page<DeviceResponse>> listDevices(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String brand) {

    // Cap size to prevent abuse
    size = Math.min(size, 100);

    Pageable pageable = PageRequest.of(page, size, Sort.by("creationTime").descending());
    Page<DeviceResponse> devices = deviceService.findAll(pageable);

    return ResponseEntity.ok(devices);
}
```

### 🎤 Interview Sound Bite

> "The current `findAll()` would cause an OOM error at scale because it loads all 43 million devices into memory. I'd fix this with pagination—returning 20-100 items per page using Spring Data's `Pageable`. For deep pagination on large datasets, I'd use keyset pagination with an indexed `creationTime` column to maintain consistent O(1) performance regardless of page depth."

---

## Q2: How can no rate limit cause DDoS?

### 🧒 Like I'm 8 (ELI8)

Imagine you're giving out free cookies at a table. If one greedy kid keeps running back 1000 times per second saying "cookie please! cookie please! cookie please!", you can't give cookies to anyone else. You're so busy with that one kid that the whole line is stuck.

**Rate limiting** is like saying "only 5 cookies per kid per minute—go sit down and wait."

### 👶 Junior Developer Level

**DDoS = Distributed Denial of Service**

Without rate limiting, one client (or many clients coordinated) can make unlimited requests:

```bash
# Attacker runs this script:
while true; do
    curl http://your-api.com/api/devices &
done
# Sends thousands of requests per second
```

**What happens to DeviceHub:**

```
Normal: 100 req/sec → App handles fine
Attack: 10,000 req/sec →
  ┌─────────────────────────────────────────┐
  │ 1. Each request needs a DB connection   │
  │    (HikariCP default pool: 10)          │
  │                                         │
  │ 2. 10,000 requests waiting for          │
  │    10 connections = 9,990 waiting       │
  │                                         │
  │ 3. Connection timeout (30 sec default)  │
  │    → Threads pile up                    │
  │                                         │
  │ 4. Thread pool exhausted                │
  │    → New requests rejected              │
  │                                         │
  │ 5. Legitimate users get 503             │
  │    "Service Unavailable"                │
  └─────────────────────────────────────────┘
```

**The API is "denied" to everyone = Denial of Service**

**Simple rate limiting with Bucket4j:**

```java
@RestController
public class DeviceController {

    private final Bucket bucket = Bucket.builder()
        .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))  // 100 req/min
        .build();

    @GetMapping("/api/devices")
    public ResponseEntity<?> listDevices() {
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(429)
                .header("Retry-After", "60")
                .body("Rate limit exceeded");
        }
        // ... normal logic
    }
}
```

### 🎓 Senior Developer Level

**Attack vectors without rate limiting:**

| Attack Type           | Mechanism                             | Impact on DeviceHub             |
| --------------------- | ------------------------------------- | ------------------------------- |
| **Volumetric**        | Flood of simple GET requests          | Connection pool exhaustion      |
| **Application-layer** | Expensive queries (unindexed filters) | Database CPU 100%               |
| **Slowloris**         | Slow, incomplete HTTP requests        | Tomcat thread pool exhaustion   |
| **Payload**           | 10MB POST bodies                      | Memory exhaustion (see Q4)      |
| **Auth brute force**  | Millions of login attempts            | If we had auth, account lockout |

**Defense in depth (multi-layer):**

```
                    ┌─────────────────────────┐
Internet ──────────►│  CDN (Cloudflare)       │──── Geographic blocking, CAPTCHA
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  API Gateway (Kong)     │──── 1000 req/min per API key
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  Load Balancer (Nginx)  │──── Connection limits per IP
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  Spring Boot App        │──── @RateLimiter per endpoint
                    │  (Bucket4j + Redis)     │
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  Database (PostgreSQL)  │──── Connection pool limits
                    └─────────────────────────┘
```

**Why each layer matters:**

- **CDN**: Stops attack before it reaches your infrastructure (cheapest)
- **API Gateway**: Per-client limits with API keys
- **Load Balancer**: Per-IP connection limits
- **Application**: Fine-grained per-endpoint limits
- **Database**: Last resort, prevents total DB meltdown

**Rate limiting strategies:**

| Algorithm          | How it works                                               | Best for                         |
| ------------------ | ---------------------------------------------------------- | -------------------------------- |
| **Token Bucket**   | Tokens refill at fixed rate, each request consumes a token | Bursty traffic OK                |
| **Leaky Bucket**   | Requests processed at fixed rate, excess queued            | Smooth output rate               |
| **Fixed Window**   | Count requests per time window (e.g., 100/minute)          | Simple, but burst at window edge |
| **Sliding Window** | Weighted average of current and previous window            | Best accuracy, more complex      |

**For distributed systems (multiple app instances):**

```java
// Redis-backed rate limiter for shared state across instances
@Bean
public RateLimiter rateLimiter(RedisClient redisClient) {
    return RateLimiter.of("devicehub-api",
        RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
}
```

### 🎤 Interview Sound Bite

> "Without rate limiting, a single malicious actor can exhaust our connection pool by sending thousands of requests, causing denial of service for legitimate users. I'd implement defense in depth: coarse-grained limits at the API gateway (1000 req/min per API key), and fine-grained limits in the application with Bucket4j using Redis for distributed state across instances. The response would include `429 Too Many Requests` with a `Retry-After` header."

---

## Q3: Race Condition - Lost updates & business rule bypass

### 🧒 Like I'm 8 (ELI8)

**The TV channel story:**

You and your sister both want to change the TV channel. The TV is on channel 5.

1. You look at the TV: "It's on 5, I can change it!"
2. Your sister looks: "It's on 5, I can change it too!"
3. You press 7
4. She presses 9 (she didn't see you press 7!)
5. TV ends up on 9, but you're confused—you thought you changed it to 7!

**The broken rule story:**

Mom says: "You can only change the channel IF it's on channel 5."

1. You see channel 5 ✓
2. Sister sees channel 5 ✓
3. You change it to 7 (allowed, because you saw 5)
4. Sister changes it to 9 (she STILL thinks it's 5!)
5. The rule got broken—she changed it when it wasn't on 5 anymore!

That's a **race condition**—two people racing to change something, and weird stuff happens.

### 👶 Junior Developer Level

**The bug in DeviceHub:**

Our business rule says: "Cannot change name or brand when device is IN_USE."

```java
@Transactional
public DeviceResponse update(Long id, DeviceUpdateRequest request) {
    // STEP 1: Read device from database (at time T=0)
    Device device = deviceRepository.findById(id).orElseThrow();
    // device.state = AVAILABLE ✓

    // STEP 2: Check business rule
    validateUpdateAllowed(device, request);
    // "State is AVAILABLE, so we CAN change the name!" ✓

    // ⏰ PROBLEM: Another request changes state to IN_USE here!

    // STEP 3: Make changes and save (at time T=1)
    device.setName(request.name());  // We're changing name...
    deviceRepository.save(device);    // ...but state is now IN_USE! ❌
}
```

**Timeline showing the bug:**

| Time | Request A (Update name)       | Request B (Change state) | Database State       |
| ---- | ----------------------------- | ------------------------ | -------------------- |
| T=0  | Reads device: state=AVAILABLE |                          | AVAILABLE            |
| T=1  | Validates: "OK to update" ✓   | Reads device             | AVAILABLE            |
| T=2  |                               | Changes state to IN_USE  | AVAILABLE            |
| T=3  |                               | Saves                    | **IN_USE**           |
| T=4  | Changes name to "NewName"     |                          | IN_USE               |
| T=5  | Saves                         |                          | IN_USE + "NewName" ❌ |

**Result:** Request A changed the name of an IN_USE device—**business rule violated!**

**This is called TOCTOU: Time Of Check To Time Of Use**

The gap between checking (T=1) and using (T=5) is the vulnerability window.

**The fix - Optimistic Locking:**

```java
@Entity
public class Device {
    @Id
    private Long id;

    @Version  // JPA manages this automatically
    private Long version;

    // ... other fields
}
```

**How it works:**

| Time | Request A                              | Request B        | Database version |
| ---- | -------------------------------------- | ---------------- | ---------------- |
| T=0  | Reads: version=1                       |                  | version=1        |
| T=1  |                                        | Reads: version=1 | version=1        |
| T=3  |                                        | Saves: version=2 | version=2        |
| T=5  | Tries to save with version=1           |                  | version=2        |
| T=5  | **FAILS!** "Expected version 1, got 2" |                  | version=2        |

JPA throws `OptimisticLockException`, and we return 409 Conflict to the client.

### 🎓 Senior Developer Level

**Production impact at scale:**

At 100K requests/second, even a 0.1% collision rate means:
- 100 corrupted updates per second
- 8.6 million data integrity violations per day
- Business rules become unenforceable

**Three concurrency solutions:**

| Solution                   | Mechanism                          | Throughput Impact                   | Best For                            |
| -------------------------- | ---------------------------------- | ----------------------------------- | ----------------------------------- |
| **Optimistic Locking**     | `@Version` field, fail on conflict | Minimal (retry rare conflicts)      | Read-heavy, low contention          |
| **Pessimistic Locking**    | `SELECT ... FOR UPDATE`            | Reduces concurrency (holds DB lock) | High contention, short transactions |
| **Serializable Isolation** | Database-level full isolation      | Significant performance hit         | Critical financial operations       |

**Why Optimistic > Pessimistic for DeviceHub:**

```
DeviceHub access pattern:
- 90% reads, 10% writes
- Low contention (different devices updated)
- Users can retry on conflict

→ Optimistic locking is ideal
```

**Implementation:**

```java
// 1. Entity with @Version
@Entity
public class Device {
    @Version
    private Long version;
}

// 2. Exception handler for conflicts
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<ProblemDetail> handleConflict(OptimisticLockException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,  // 409
        "Resource was modified by another request. Please refresh and retry."
    );
    problem.setProperty("retryable", true);
    problem.setProperty("retryAfterMs", 100);
    return ResponseEntity.status(409).body(problem);
}

// 3. Client-side: Retry with exponential backoff
// Fetch fresh data, re-apply changes, retry
```

**ETag headers for HTTP-level optimistic locking:**

```java
@GetMapping("/{id}")
public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long id) {
    DeviceResponse device = deviceService.findById(id);
    return ResponseEntity.ok()
        .eTag("\"" + device.version() + "\"")  // ETag from version
        .body(device);
}

@PutMapping("/{id}")
public ResponseEntity<DeviceResponse> updateDevice(
        @PathVariable Long id,
        @RequestHeader("If-Match") String ifMatch,  // Client sends ETag back
        @RequestBody DeviceUpdateRequest request) {

    long expectedVersion = parseETag(ifMatch);
    // Service checks version matches before update
    DeviceResponse updated = deviceService.update(id, request, expectedVersion);
    return ResponseEntity.ok(updated);
}
```

### 🎤 Interview Sound Bite

> "The current code has a TOCTOU race condition—between reading the device state and saving changes, another request can modify it, bypassing business rules like 'IN_USE devices are immutable.' I'd fix this with optimistic locking using JPA's `@Version` field. When a concurrent modification is detected, we return 409 Conflict with `retryable: true`, letting the client fetch fresh data and retry. For HTTP clients, I'd expose the version as an ETag header with If-Match preconditions."

---

## Q4: DoS via payload size (no string limits)

### 🧒 Like I'm 8 (ELI8)

Imagine the mailman asks "What's your name?" and you say:

"My name is Aaaaaaaaaaaaa..." and you keep going for a MILLION letters!

The mailman's notebook is too small. He runs out of pages. His bag explodes. Nobody gets their mail today.

That's what happens when someone sends a reeeeeally long device name to our computer—the computer's memory fills up and crashes.

### 👶 Junior Developer Level

**The vulnerability:**

```java
// DeviceCreateRequest.java - Current (UNSAFE)
public record DeviceCreateRequest(
    @NotBlank String name,   // No size limit! Could be 100MB!
    @NotBlank String brand,  // No size limit!
    @NotNull DeviceState state
) {}
```

**Attack:**

```bash
# Create a 100MB device name
NAME=$(python3 -c "print('A' * 100000000)")  # 100 million A's
curl -X POST http://api/devices \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"$NAME\", \"brand\": \"Evil\", \"state\": \"AVAILABLE\"}"
```

**What happens:**

```
Request arrives (100MB)
     │
     ▼
┌────────────────────────────────────────┐
│ 1. Tomcat reads request body           │
│    → Allocates 100MB for JSON string   │
│                                        │
│ 2. Jackson deserializes JSON           │
│    → Creates another 100MB String      │
│                                        │
│ 3. JPA creates Device entity           │
│    → Another 100MB for entity          │
│                                        │
│ 4. Total: ~300MB for ONE request       │
│                                        │
│ 5. 10 concurrent malicious requests    │
│    → 3GB consumed                      │
│                                        │
│ 6. JVM heap exhausted                  │
│    → OutOfMemoryError 💥               │
└────────────────────────────────────────┘
```

**The fix:**

```java
// DeviceCreateRequest.java - SAFE
public record DeviceCreateRequest(
    @NotBlank
    @Size(min = 1, max = 255, message = "Name must be 1-255 characters")
    String name,

    @NotBlank
    @Size(min = 1, max = 100, message = "Brand must be 1-100 characters")
    String brand,

    @NotNull(message = "State is required")
    DeviceState state
) {}
```

Now invalid requests fail fast with 400 Bad Request before consuming resources.

### 🎓 Senior Developer Level

**Defense in depth (multiple layers):**

| Layer              | Protection         | Configuration                                   | Rejects At              |
| ------------------ | ------------------ | ----------------------------------------------- | ----------------------- |
| **CDN/WAF**        | Request size limit | Cloudflare: 100MB default                       | Edge (cheapest)         |
| **Nginx**          | Body size limit    | `client_max_body_size 1m;`                      | Load balancer           |
| **Tomcat**         | Form post size     | `server.tomcat.max-http-form-post-size=1MB`     | App container           |
| **Spring**         | Multipart limit    | `spring.servlet.multipart.max-request-size=1MB` | Framework               |
| **DTO Validation** | Field size         | `@Size(max=255)`                                | Application (fast fail) |
| **JPA Entity**     | Column length      | `@Column(length=255)`                           | Persistence             |
| **Database**       | Column type        | `VARCHAR(255)`                                  | Last resort             |

**Why validate at multiple layers:**

```
Layer 1 (CDN): "Body too large" → 413
  ✓ Attack stopped before reaching your infrastructure
  ✓ No bandwidth cost

Layer 2 (Nginx): "Body too large" → 413
  ✓ Stopped before app processes request

Layer 3 (DTO): "Name too long" → 400
  ✓ Clear error message: "name: must be 1-255 characters"
  ✓ No database round-trip

Layer 4 (Database): Column truncation or error
  ✗ Wasteful - already consumed all previous resources
  ✗ Poor error message
```

**Application configuration:**

```properties
# application.properties

# Limit request body size (JSON payloads)
server.tomcat.max-http-form-post-size=1MB
server.tomcat.max-swallow-size=1MB

# Limit multipart uploads (if applicable)
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Jackson: Limit string length during deserialization
spring.jackson.mapper.DEFAULT_STRING_READER_FACTORY=com.example.LimitedStringReader
```

**Error response for validation failure:**

```json
{
  "type": "https://devicehub.api/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Request body contains invalid fields",
  "errors": {
    "name": "must be between 1 and 255 characters",
    "brand": "must be between 1 and 100 characters"
  }
}
```

### 🎤 Interview Sound Bite

> "Without `@Size` limits on string fields, an attacker could send 100MB payloads, causing memory exhaustion. I'd implement defense in depth: `@Size(max=255)` on DTOs for fast-fail with clear 400 errors, `@Column(length=255)` on entities for schema enforcement, and `server.tomcat.max-http-form-post-size=1MB` to reject oversized payloads at the container level before they even reach our code."

---

## Q5: PUT allows nulling required fields

### 🧒 Like I'm 8 (ELI8)

You have a box that should ALWAYS have a toy car, a toy plane, and a toy boat.

Someone says "I want to replace everything in the box with... nothing." And your rule checker says "Okay!" because nobody told it to check if the box would be empty.

The checker only knows to say "yes" or "no" if you tell it WHAT to check. We forgot to tell it "the box can't be empty."

### 👶 Junior Developer Level

**The bug:**

```java
// Controller - Has @Valid ✓
@PutMapping("/{id}")
public ResponseEntity<DeviceResponse> updateDevice(
        @PathVariable Long id,
        @Valid @RequestBody DeviceUpdateRequest request) {  // @Valid is here...
    return ResponseEntity.ok(deviceService.update(id, request));
}

// DTO - NO validation! ❌
public record DeviceUpdateRequest(
    String name,     // No @NotBlank!
    String brand,    // No @NotBlank!
    DeviceState state
) {}
```

**The problem:** `@Valid` triggers validation, but there's nothing TO validate!

```java
// This request passes validation and corrupts data:
PUT /api/devices/1
{
    "name": null,      // Sets device name to NULL!
    "brand": null,     // Sets device brand to NULL!
    "state": "AVAILABLE"
}
```

**Why didn't tests catch it?**

```java
// The tests that existed:
@Test
void shouldUpdateDevice_whenValidRequest() {
    var request = new DeviceUpdateRequest("NewName", "NewBrand", AVAILABLE);
    mockMvc.perform(put("/api/devices/1").content(toJson(request)))
        .andExpect(status().isOk());  // ✓ This passes!
}

// The test that was MISSING:
@Test
void shouldRejectPut_whenNameIsNull() {
    var request = new DeviceUpdateRequest(null, "Brand", AVAILABLE);
    mockMvc.perform(put("/api/devices/1").content(toJson(request)))
        .andExpect(status().isBadRequest());  // ❌ This test didn't exist!
}
```

**The fix - Add validation:**

```java
public record DeviceUpdateRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @NotBlank(message = "Brand is required")
    @Size(max = 100)
    String brand,

    @NotNull(message = "State is required")
    DeviceState state
) {}
```

### 🎓 Senior Developer Level

**But wait—now PATCH is broken!**

REST semantics:
- **PUT** = Replace the entire resource (ALL fields required)
- **PATCH** = Modify specific fields (fields are optional, null = "don't change")

**If we add `@NotBlank` to the shared DTO, PATCH can't send partial updates!**

**Solution: Separate DTOs for PUT and PATCH:**

```java
// For PUT - Complete replacement, all fields required
public record DeviceReplaceRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @NotBlank(message = "Brand is required")
    @Size(max = 100)
    String brand,

    @NotNull(message = "State is required")
    DeviceState state
) {}

// For PATCH - Partial update, all fields optional
public record DevicePatchRequest(
    @Size(max = 255)  // Still validate size IF provided
    String name,      // null = don't change this field

    @Size(max = 100)
    String brand,

    DeviceState state
) {}
```

**Controller:**

```java
@PutMapping("/{id}")
public ResponseEntity<DeviceResponse> replaceDevice(
        @PathVariable Long id,
        @Valid @RequestBody DeviceReplaceRequest request) {
    return ResponseEntity.ok(deviceService.replace(id, request));
}

@PatchMapping("/{id}")
public ResponseEntity<DeviceResponse> patchDevice(
        @PathVariable Long id,
        @Valid @RequestBody DevicePatchRequest request) {
    return ResponseEntity.ok(deviceService.patch(id, request));
}
```

**Service logic difference:**

```java
// PUT - Replace all fields
public DeviceResponse replace(Long id, DeviceReplaceRequest request) {
    Device device = findOrThrow(id);
    device.setName(request.name());    // Always set
    device.setBrand(request.brand());  // Always set
    device.setState(request.state());  // Always set
    return toResponse(deviceRepository.save(device));
}

// PATCH - Only update non-null fields
public DeviceResponse patch(Long id, DevicePatchRequest request) {
    Device device = findOrThrow(id);
    if (request.name() != null) {
        device.setName(request.name());
    }
    if (request.brand() != null) {
        device.setBrand(request.brand());
    }
    if (request.state() != null) {
        device.setState(request.state());
    }
    return toResponse(deviceRepository.save(device));
}
```

**Test coverage to prevent this:**

```java
@Nested
class PutValidation {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void shouldRejectPut_whenNameInvalid(String name) {
        var request = new DeviceReplaceRequest(name, "Brand", AVAILABLE);
        mockMvc.perform(put("/api/devices/1").content(toJson(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void shouldRejectPut_whenFieldsMissing() {
        mockMvc.perform(put("/api/devices/1")
            .contentType(APPLICATION_JSON)
            .content("{}"))  // Empty body
            .andExpect(status().isBadRequest());
    }
}
```

### 🎤 Interview Sound Bite

> "The tests covered happy paths but missed null-value edge cases. PUT and PATCH shared the same DTO, but semantically they're different: PUT replaces the entire resource requiring all fields, while PATCH modifies specific fields allowing nulls. I'd create separate DTOs—`DeviceReplaceRequest` with `@NotBlank` for PUT, and `DevicePatchRequest` with optional fields for PATCH. I'd also add parameterized tests covering null, empty, and whitespace-only values."

---

## Q6: H2 scalability + SQL vs NoSQL

### 🧒 Like I'm 8 (ELI8)

**H2 is like a diary:**
- Only ONE person can write in it at a time
- If you want 3 friends to write at the same time, you need 3 different diaries
- But then Friend 1's diary doesn't know what Friend 2 wrote!

**PostgreSQL is like a magical whiteboard:**
- Everyone can see it
- Everyone can write on it
- If someone writes something, everyone sees it immediately

**MongoDB is like a big box of sticky notes:**
- You can throw any shape of paper in it
- Super flexible!
- But finding things later can be messy

### 👶 Junior Developer Level

**Why H2 can't scale horizontally:**

```
DeviceHub with H2 (Single Instance - Works):
┌─────────────────────────┐
│     DeviceHub App       │
│        │                │
│   ┌────▼────┐           │
│   │   H2    │ ◄─── File on disk: ./data/devicehub.mv.db
│   │ (file)  │           │
│   └─────────┘           │
└─────────────────────────┘

DeviceHub with H2 (Multiple Instances - BROKEN):
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   Instance 1     │  │   Instance 2     │  │   Instance 3     │
│       │          │  │       │          │  │       │          │
│  ┌────▼────┐     │  │  ┌────▼────┐     │  │  ┌────▼────┐     │
│  │   H2    │     │  │  │   H2    │     │  │  │   H2    │     │
│  │ (file?) │     │  │  │ (file?) │     │  │  │ (file?) │     │
│  └─────────┘     │  │  └─────────┘     │  │  └─────────┘     │
└──────────────────┘  └──────────────────┘  └──────────────────┘
       ↑                     ↑                     ↑
       └─── Each instance has different data! ────┘
                         💥 BROKEN
```

**H2 file-based limitations:**
- One file = one machine
- No built-in replication
- No clustering
- File lock prevents multiple writers

**PostgreSQL solution:**

```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   Instance 1     │  │   Instance 2     │  │   Instance 3     │
└────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
         │                     │                     │
         └──────────┬──────────┴──────────┬──────────┘
                    │                     │
                    ▼                     ▼
           ┌─────────────────────────────────────┐
           │            PostgreSQL                │
           │       (Shared by all instances)      │
           └─────────────────────────────────────┘
```

All instances connect to the SAME database!

### 🎓 Senior Developer Level

**When to use SQL vs NoSQL:**

| Factor             | SQL (PostgreSQL)            | NoSQL (MongoDB)               | DeviceHub Choice          |
| ------------------ | --------------------------- | ----------------------------- | ------------------------- |
| **Data structure** | Fixed schema, relations     | Flexible, documents           | Fixed → **SQL**           |
| **Transactions**   | Full ACID                   | Limited (multi-doc since 4.0) | Need ACID → **SQL**       |
| **Queries**        | Complex JOINs, aggregations | Simple lookups, embedded      | Complex filters → **SQL** |
| **Consistency**    | Strong by default           | Eventual (tunable)            | Strong needed → **SQL**   |
| **Scaling writes** | Single primary, replicas    | Sharded, multi-primary        | Read-heavy → **SQL OK**   |
| **Ecosystem**      | JPA, Hibernate mature       | Spring Data Mongo OK          | Team knows JPA → **SQL**  |

**Why NOT MongoDB for DeviceHub:**

1. **Device data is structured**: Fixed fields (name, brand, state, creationTime)
2. **Strong consistency needed**: Device state changes must be immediately visible
3. **Complex queries**: Filter by state AND brand, date ranges, aggregations
4. **ACID transactions**: Business rules require atomic updates
5. **JPA expertise**: Most Java teams know JPA better than Mongo

**When I WOULD choose MongoDB:**

- Storing device logs (variable schema, append-only)
- User preferences (flexible structure per user)
- Event sourcing (immutable events)
- Rapid prototyping (schema can change frequently)

**At 43M devices, 12 countries:**

**Option 1: PostgreSQL + Read Replicas (Recommended)**

```
                    ┌─────────────────────────────────┐
                    │    PostgreSQL Primary (EU)       │
                    │         (All writes)             │
                    └───────────────┬─────────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
     ┌────────▼────────┐   ┌────────▼────────┐   ┌────────▼────────┐
     │  EU Replica     │   │  US Replica     │   │  APAC Replica   │
     │  (EU reads)     │   │  (US reads)     │   │  (APAC reads)   │
     └─────────────────┘   └─────────────────┘   └─────────────────┘
```

- Writes go to EU primary (may have latency from other regions)
- Reads from local replica (fast, low latency)
- Good for read-heavy workloads (90% reads)
- Replication lag: ~100ms typically

**Option 2: CockroachDB / Spanner (Multi-region writes)**

```
     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
     │  EU Node    │◄───►│  US Node    │◄───►│  APAC Node  │
     │  (Primary)  │     │  (Primary)  │     │  (Primary)  │
     └─────────────┘     └─────────────┘     └─────────────┘
```

- Writes to any region with local latency
- Automatic replication and consistency
- More complex, more expensive
- Best for: global writes with local latency requirements

### 🎤 Interview Sound Bite

> "H2 is file-based and single-instance—it can't share data across multiple app instances, making horizontal scaling impossible. For production with 43M devices, I'd use PostgreSQL for its ACID guarantees, mature indexing, and read replica support for geo-distribution. NoSQL like MongoDB is better suited for unstructured data or when you need horizontal write scaling, but for our structured device data with strong consistency requirements, relational is the right choice."

---

## Q7: SLIs, SLOs, and metrics

### 🧒 Like I'm 8 (ELI8)

- **Metric** = All the thermometers in your house (kitchen: 72°F, bedroom: 68°F, basement: 65°F)
- **SLI** = The ONE thermometer that matters most (living room: 70°F)
- **SLO** = The goal Mom set ("Keep living room between 68-72°F")
- **SLA** = The promise to Mom ("If it goes outside 68-72°F, I'll tell you and fix it, or no dessert")

Spring Actuator gives you LOTS of thermometers. But it doesn't tell you if you're meeting Mom's goal.

### 👶 Junior Developer Level

**Definitions:**

| Term                              | What it means                            | DeviceHub Example                                  |
| --------------------------------- | ---------------------------------------- | -------------------------------------------------- |
| **Metric**                        | Any measurable value                     | CPU usage: 45%, memory: 2GB, request count: 10,000 |
| **SLI** (Service Level Indicator) | A SPECIFIC metric that matters for users | p99 latency = 87ms                                 |
| **SLO** (Service Level Objective) | Target for the SLI                       | p99 latency < 100ms                                |
| **SLA** (Service Level Agreement) | Legal contract with penalties            | 99.9% requests < 200ms, or refund                  |

**What Spring Actuator provides:**

```bash
# Visit /actuator/metrics/http.server.requests
{
  "name": "http.server.requests",
  "measurements": [
    { "statistic": "COUNT", "value": 12345 },
    { "statistic": "TOTAL_TIME", "value": 567.89 }
  ]
}
```

This tells you:
- 12,345 requests happened
- Total processing time was 567.89 seconds

**What it DOESN'T tell you:**
- ❓ "Is 87ms latency good or bad?"
- ❓ "Are we meeting our 100ms target?"
- ❓ "Alert me when we breach the target"

**To get SLIs/SLOs, you need:**

1. **Prometheus** to collect and store metrics
2. **Histogram buckets** for percentile calculation
3. **Grafana** to visualize
4. **Alerting** when SLO is breached

**Configuration:**

```properties
# application.properties

# Expose Prometheus endpoint
management.endpoints.web.exposure.include=health,prometheus

# Enable histogram for percentile calculation (p50, p95, p99)
management.metrics.distribution.percentiles-histogram.http.server.requests=true

# Define SLO buckets (for visualization)
management.metrics.distribution.slo.http.server.requests=50ms,100ms,200ms,500ms
```

### 🎓 Senior Developer Level

**The SLI/SLO/SLA hierarchy:**

```
         ┌─────────────────────────────────────────────────┐
         │  SLA (External Promise)                         │
         │  "99.9% of requests < 200ms, or money back"     │
         └────────────────────────┬────────────────────────┘
                                  │ (Must be safer than SLO)
         ┌────────────────────────▼────────────────────────┐
         │  SLO (Internal Target)                          │
         │  "99.95% of requests < 100ms"                   │
         │  (Stricter than SLA to give buffer)             │
         └────────────────────────┬────────────────────────┘
                                  │ (Measured by)
         ┌────────────────────────▼────────────────────────┐
         │  SLI (Actual Measurement)                       │
         │  "p99 latency = 87ms" ✓                         │
         │  "Error rate = 0.01%" ✓                         │
         └────────────────────────┬────────────────────────┘
                                  │ (Derived from)
         ┌────────────────────────▼────────────────────────┐
         │  Metrics (Raw Data)                             │
         │  http_server_requests_seconds_bucket{le="0.1"}  │
         │  http_server_requests_seconds_count             │
         └─────────────────────────────────────────────────┘
```

**Error Budget concept:**

If your SLO is 99.9% success rate:
- 0.1% of requests can fail = **Error Budget**
- 100K requests/day = 100 failures allowed
- If you've used 80 failures by noon, slow down deploys!

**Common SLIs for APIs:**

| SLI              | How to measure                | Typical SLO |
| ---------------- | ----------------------------- | ----------- |
| **Latency**      | p99 response time             | < 100ms     |
| **Availability** | Successful responses / total  | 99.9%       |
| **Error rate**   | 5xx responses / total         | < 0.1%      |
| **Throughput**   | Requests per second sustained | > 1000 rps  |

**Full observability stack:**

```yaml
# docker-compose.yml addition
services:
  prometheus:
    image: prom/prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'devicehub'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['devicehub:8080']
```

**Alerting rule for SLO breach:**

```yaml
# alerts.yml
groups:
  - name: slo-alerts
    rules:
      - alert: LatencySLOBreach
        expr: |
          histogram_quantile(0.99,
            rate(http_server_requests_seconds_bucket[5m])
          ) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "p99 latency {{ $value | humanizeDuration }} exceeds 100ms SLO"
```

### 🎤 Interview Sound Bite

> "Actuator exposes raw metrics like request count and total time, but SLOs require interpretation. An SLI like 'p99 latency is 87ms' is the current measurement, the SLO is the target 'p99 < 100ms', and the SLA is the customer commitment with penalties. I'd add Micrometer Prometheus registry, configure histogram buckets for percentile calculation, visualize in Grafana, and set alerts when the error budget is being consumed too fast."

---

## Q8: Caching strategy (multi-layer)

### 🧒 Like I'm 8 (ELI8)

Imagine you ask Mom a question a LOT: "What's for dinner?"

1. **Your memory** (fastest): You remember she said "pizza" 5 minutes ago
2. **Ask your sister** (fast): She remembers too
3. **Ask Mom** (slowest): She has to think and answer again

Caching is like remembering answers so you don't have to ask Mom every time!

**But what if dinner changes?** Mom says "Actually, pasta now." Your memory has the WRONG answer! That's why we need rules for when to forget and ask again.

### 👶 Junior Developer Level

**Why cache?**

Without caching:
```
Request → Controller → Service → Repository → Database
                                                 ↑
                                        50ms each query
```

With caching:
```
Request → Controller → Service → Cache hit! → Return immediately
                                    ↑
                              0.1ms (500x faster!)
```

**Simple Spring caching:**

```java
@Service
public class DeviceService {

    @Cacheable(value = "devices", key = "#id")
    public DeviceResponse findById(Long id) {
        // This method is only called if NOT in cache
        return deviceRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow();
    }

    @CacheEvict(value = "devices", key = "#id")
    public DeviceResponse update(Long id, DeviceUpdateRequest request) {
        // After update, remove from cache (stale data)
        Device device = deviceRepository.findById(id).orElseThrow();
        device.setName(request.name());
        return toResponse(deviceRepository.save(device));
    }

    @CacheEvict(value = "devices", allEntries = true)
    public void clearAllDeviceCache() {
        // Clears entire cache (nuclear option)
    }
}
```

**Enable caching:**

```java
@SpringBootApplication
@EnableCaching  // Add this!
public class DeviceHubApplication { }
```

**Problem: What about multiple app instances?**

```
Instance 1: Updates device, clears ITS cache
Instance 2: Still has OLD cached data!
                    💥 Stale data served
```

### 🎓 Senior Developer Level

**Multi-layer caching architecture:**

```
Request
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 1: Local Cache (Caffeine)                                │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • Per-instance, in-memory                                   ││
│  │ • Fastest (nanoseconds)                                     ││
│  │ • 10,000 entries, 5 minute TTL                              ││
│  │ • Problem: Each instance has different cache                ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────┬─────────────────────────────────────┘
                            │ Cache miss
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 2: Distributed Cache (Redis)                             │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • Shared by all instances                                   ││
│  │ • Fast (1-2ms network)                                      ││
│  │ • Consistent across instances                               ││
│  │ • 15 minute TTL                                             ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────┬─────────────────────────────────────┘
                            │ Cache miss
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 3: Database Query Cache / Connection Pool                │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • Database-level caching                                    ││
│  │ • Buffer pool, query cache                                  ││
│  │ • HikariCP connection reuse                                 ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────┬─────────────────────────────────────┘
                            │ Query
                            ▼
                    ┌───────────────┐
                    │   PostgreSQL   │
                    │   (Disk I/O)   │
                    └───────────────┘
```

**Implementation with Spring + Caffeine + Redis:**

```java
// Configuration
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // Layer 1: Local Caffeine cache
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats());  // For monitoring hit rate

        // Layer 2: Redis distributed cache
        RedisCacheManager redis = RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15)))
            .build();

        // Composite: Try Caffeine first, then Redis
        return new CompositeCacheManager(caffeine, redis);
    }
}
```

**Cache invalidation strategy:**

| Strategy               | How it works                       | Best for                        |
| ---------------------- | ---------------------------------- | ------------------------------- |
| **TTL (Time To Live)** | Cache expires after X minutes      | Data that can be slightly stale |
| **Write-through**      | Update cache when data changes     | Strong consistency needed       |
| **Write-behind**       | Queue cache updates asynchronously | High write throughput           |
| **Event-driven**       | Kafka/Redis pub-sub on change      | Microservices, real-time        |

**For DeviceHub, I'd use:**

```java
@Service
public class DeviceService {

    // Read: Check L1 (Caffeine) → L2 (Redis) → Database
    @Cacheable(value = "devices", key = "#id", cacheManager = "compositeCacheManager")
    public DeviceResponse findById(Long id) {
        return deviceRepository.findById(id).map(this::toResponse).orElseThrow();
    }

    // Write: Update DB, then invalidate BOTH caches
    @Caching(evict = {
        @CacheEvict(value = "devices", key = "#id", cacheManager = "caffeineCacheManager"),
        @CacheEvict(value = "devices", key = "#id", cacheManager = "redisCacheManager")
    })
    @Transactional
    public DeviceResponse update(Long id, DeviceUpdateRequest request) {
        // ... update logic
    }
}
```

**Monitoring cache effectiveness:**

```java
@Scheduled(fixedRate = 60000)
public void logCacheStats() {
    CaffeineCache cache = (CaffeineCache) cacheManager.getCache("devices");
    CacheStats stats = cache.getNativeCache().stats();
    log.info("Cache hit rate: {}%, evictions: {}",
        stats.hitRate() * 100, stats.evictionCount());
}
```

Target: **>90% hit rate** for hot data like frequently accessed devices.

### 🎤 Interview Sound Bite

> "A single `@Cacheable` annotation isn't enough for distributed systems—each instance would have its own cache with stale data. I'd implement multi-layer caching: Caffeine for a fast local L1 cache (10K entries, 5-minute TTL), Redis for a shared L2 cache across instances (15-minute TTL), and the database's buffer pool as L3. On writes, I'd evict from both layers to prevent stale reads. I'd monitor hit rate (target >90%) and adjust TTLs based on access patterns."

---

## Q9: MDC and log correlation

### 🧒 Like I'm 8 (ELI8)

Imagine 10 kids are all talking at the same time in a classroom. The teacher is writing down everything everyone says. Later, she tries to figure out what ONE kid said—but it's all mixed up!

**MDC** is like giving each kid a colored sticker. Now when the teacher writes things down, she adds the sticker color. Later, she can easily find "everything the kid with the BLUE sticker said."

In computers, the sticker is a **Request ID**, and it helps us follow one request through all the logs.

### 👶 Junior Developer Level

**The problem without MDC:**

```log
2026-01-24 10:30:00.001 INFO  - Finding device by id=1
2026-01-24 10:30:00.002 INFO  - Database query executed
2026-01-24 10:30:00.003 INFO  - Finding device by id=2
2026-01-24 10:30:00.004 ERROR - Device not found
2026-01-24 10:30:00.005 INFO  - Database query executed
2026-01-24 10:30:00.006 INFO  - Returning response
```

Which request failed? We can't tell! The logs are interleaved.

**With MDC (Request ID):**

```log
2026-01-24 10:30:00.001 [req-abc123] INFO  - Finding device by id=1
2026-01-24 10:30:00.002 [req-abc123] INFO  - Database query executed
2026-01-24 10:30:00.003 [req-def456] INFO  - Finding device by id=2
2026-01-24 10:30:00.004 [req-def456] ERROR - Device not found  ← This one!
2026-01-24 10:30:00.005 [req-abc123] INFO  - Database query executed
2026-01-24 10:30:00.006 [req-abc123] INFO  - Returning response
```

Now we can grep for `req-def456` and see ONLY that request's logs!

**MDC = Mapped Diagnostic Context**

It's a thread-local map that automatically adds values to every log statement.

**The bug in DeviceHub:**

```xml
<!-- logback-spring.xml has this pattern: -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %X{requestId} %-5level %logger{36} - %msg%n</pattern>
                                              ↑
                                    This should print the requestId...
```

But `%X{requestId}` is empty because **nothing populates it!**

**The fix - Add a filter:**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Get requestId from header (if provided) or generate new one
        String requestId = Optional.ofNullable(request.getHeader("X-Request-Id"))
            .orElse(UUID.randomUUID().toString().substring(0, 8));

        try {
            MDC.put("requestId", requestId);  // Put it in MDC
            response.addHeader("X-Request-Id", requestId);  // Return to client
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();  // Clean up to prevent memory leak
        }
    }
}
```

### 🎓 Senior Developer Level

**Why MDC matters in distributed systems:**

```
User Request
     │
     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  API Gateway │ ───► │  DeviceHub   │ ───► │  Auth Service │
│  req-abc123  │      │  req-abc123  │      │  req-abc123   │
└──────────────┘      └──────────────┘      └──────────────┘
                              │
                              ▼
                      ┌──────────────┐
                      │  PostgreSQL  │
                      └──────────────┘
```

The SAME requestId flows through all services, enabling cross-service tracing.

**Implementation with propagation:**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";  // OpenTelemetry

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
            .orElseGet(() -> generateRequestId());

        String traceId = Optional.ofNullable(request.getHeader(TRACE_ID_HEADER))
            .orElse(requestId);  // Use requestId if no trace context

        try {
            // Populate MDC for logging
            MDC.put("requestId", requestId);
            MDC.put("traceId", traceId);
            MDC.put("clientIp", getClientIp(request));
            MDC.put("userAgent", request.getHeader("User-Agent"));

            // Add to response for client correlation
            response.addHeader(REQUEST_ID_HEADER, requestId);

            // Log request start
            log.info("Incoming request: {} {}", request.getMethod(), request.getRequestURI());

            long startTime = System.currentTimeMillis();
            filterChain.doFilter(request, response);

            // Log request end with duration
            MDC.put("durationMs", String.valueOf(System.currentTimeMillis() - startTime));
            log.info("Request completed: status={}", response.getStatus());

        } finally {
            MDC.clear();  // CRITICAL: Prevent ThreadLocal pollution
        }
    }

    private String generateRequestId() {
        // Short UUID for readability: "a1b2c3d4" instead of full UUID
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
```

**Logback configuration for structured JSON logs:**

```xml
<!-- logback-spring.xml -->
<configuration>
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>requestId</includeMdcKeyName>
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>clientIp</includeMdcKeyName>
                <includeMdcKeyName>durationMs</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>
</configuration>
```

**Result in production (JSON logs to ELK/Datadog):**

```json
{
  "timestamp": "2026-01-24T10:30:00.001Z",
  "level": "INFO",
  "logger": "DeviceService",
  "message": "Finding device by id=1",
  "requestId": "abc123",
  "traceId": "abc123",
  "clientIp": "192.168.1.100",
  "durationMs": "45"
}
```

Now you can:
- Search for `requestId:abc123` to see all logs from one request
- Alert on requests where `durationMs > 500`
- Track users via `clientIp`

### 🎤 Interview Sound Bite

> "The logback config references `%X{requestId}` but nothing populates the MDC. I'd add a `OncePerRequestFilter` that extracts the `X-Request-Id` header (or generates one), puts it in the MDC, and also returns it in the response for client-side correlation. For distributed systems, I'd propagate this ID to downstream services, enabling end-to-end tracing across microservices. The `finally` block must call `MDC.clear()` to prevent thread-local pollution in pooled threads."

---

## Q10: Idempotency keys for POST

### 🧒 Like I'm 8 (ELI8)

You're ordering pizza on the phone. You say "One pepperoni pizza!" but the phone cuts off.

Did they hear you? You call again: "One pepperoni pizza!"

Now the question is: Do you get ONE pizza (they remembered your first call) or TWO pizzas (they thought it was a new order)?

**Idempotency** means: No matter how many times you call, you only get ONE pizza for that order.

### 👶 Junior Developer Level

**The problem:**

```bash
# Client creates a device
curl -X POST /api/devices -d '{"name": "iPhone"}'
# Response: 201 Created, id=42

# Network hiccup! Client didn't see the response.
# Client retries the SAME request:
curl -X POST /api/devices -d '{"name": "iPhone"}'
# Response: 201 Created, id=43  ← DUPLICATE!
```

Now we have TWO identical devices (id=42 and id=43).

**Why POST is different from PUT/DELETE:**

| Method | Idempotent by design? | Example                                       |
| ------ | --------------------- | --------------------------------------------- |
| GET    | ✅ Yes                 | Same query, same result                       |
| PUT    | ✅ Yes                 | Replace resource with same data = same result |
| DELETE | ✅ Yes                 | Delete same resource twice = still deleted    |
| POST   | ❌ No!                 | Create twice = two resources                  |

**The solution - Idempotency Keys:**

Client sends a unique key with each logical request:

```bash
# First attempt
curl -X POST /api/devices \
  -H "Idempotency-Key: order-12345" \
  -d '{"name": "iPhone"}'
# Response: 201 Created, id=42

# Network hiccup, client retries with SAME key:
curl -X POST /api/devices \
  -H "Idempotency-Key: order-12345" \
  -d '{"name": "iPhone"}'
# Response: 201 Created, id=42  ← Same response, no duplicate!
```

**Implementation:**

```java
@PostMapping
public ResponseEntity<DeviceResponse> createDevice(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody DeviceCreateRequest request) {

    if (idempotencyKey != null) {
        // Check if we've seen this key before
        Optional<DeviceResponse> cached = idempotencyService.get(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(201).body(cached.get());
        }
    }

    // Create the device
    DeviceResponse response = deviceService.create(request);

    // Cache the response for this key
    if (idempotencyKey != null) {
        idempotencyService.store(idempotencyKey, response, Duration.ofHours(24));
    }

    return ResponseEntity.status(201).body(response);
}
```

### 🎓 Senior Developer Level

**Why it's P2-MEDIUM, not P0-CRITICAL:**

| Scenario                | Impact                             | Likelihood                |
| ----------------------- | ---------------------------------- | ------------------------- |
| Network timeout on POST | Duplicate device created           | Medium (3-5% of requests) |
| Client retry logic      | Duplicate if not handled           | Depends on client         |
| Financial transaction   | CRITICAL (double charge!)          | Would be P0               |
| Device creation         | Annoying, but detectable/deletable | Medium impact             |

For DeviceHub, duplicates are annoying but not catastrophic. For payments, this would be P0.

**Production implementation with Redis:**

```java
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "idempotency:";

    public <T> Optional<T> get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (json == null) return Optional.empty();
        return Optional.of(objectMapper.readValue(json, type));
    }

    public <T> void store(String key, T response, Duration ttl) {
        String json = objectMapper.writeValueAsString(response);
        redisTemplate.opsForValue().set(KEY_PREFIX + key, json, ttl);
    }

    public boolean tryLock(String key, Duration lockDuration) {
        // Prevent concurrent processing of same idempotency key
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + key + ":lock",
                "locked",
                lockDuration
            )
        );
    }
}
```

**Full controller with race condition handling:**

```java
@PostMapping
public ResponseEntity<DeviceResponse> createDevice(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody DeviceCreateRequest request) {

    if (idempotencyKey == null) {
        // No idempotency key = normal create
        return ResponseEntity.status(201).body(deviceService.create(request));
    }

    // Check cache first
    Optional<DeviceResponse> cached = idempotencyService.get(idempotencyKey, DeviceResponse.class);
    if (cached.isPresent()) {
        log.info("Idempotency cache hit: {}", idempotencyKey);
        return ResponseEntity.status(201).body(cached.get());
    }

    // Try to acquire lock (prevent parallel processing of same key)
    if (!idempotencyService.tryLock(idempotencyKey, Duration.ofSeconds(30))) {
        // Another request is processing this key, wait and retry cache check
        Thread.sleep(100);
        return createDevice(idempotencyKey, request);  // Retry (with recursion limit!)
    }

    try {
        // Double-check cache after acquiring lock
        cached = idempotencyService.get(idempotencyKey, DeviceResponse.class);
        if (cached.isPresent()) {
            return ResponseEntity.status(201).body(cached.get());
        }

        // Actually create
        DeviceResponse response = deviceService.create(request);
        idempotencyService.store(idempotencyKey, response, Duration.ofHours(24));
        return ResponseEntity.status(201).body(response);

    } finally {
        idempotencyService.releaseLock(idempotencyKey);
    }
}
```

### 🎤 Interview Sound Bite

> "POST is not idempotent by design—retrying the same create request produces duplicates. I'd implement idempotency keys where clients send a unique `Idempotency-Key` header. We store the response in Redis with a 24-hour TTL, and if we see the same key again, we return the cached response without creating a duplicate. This is especially critical for financial operations, though for device creation it's P2 since duplicates are detectable and deletable."

---

## Q11: Contract tests and test strategy

### 🧒 Like I'm 8 (ELI8)

**The test triangle is like building a LEGO castle:**

1. **Unit tests** = Testing each LEGO brick works (hundreds of them)
2. **Integration tests** = Testing that bricks stick together (fewer of these)
3. **E2E tests** = Testing the whole castle looks right (just a few)

**Contract tests** are different—they're like a PROMISE LETTER between you and your friend:

"Dear Friend, when you ask me for a red brick, I PROMISE to give you a red brick, not a blue one."

If you break that promise, the letter tells you!

### 👶 Junior Developer Level

**The Test Pyramid:**

```
                    ▲
                   ╱ ╲
                  ╱ E2E ╲           Few, slow, expensive
                 ╱───────╲          (5-10 tests)
                ╱Integration╲       Some, medium speed
               ╱─────────────╲      (20-50 tests)
              ╱    Unit Tests  ╲    Many, fast, cheap
             ╱───────────────────╲  (100-500 tests)
```

| Type            | What it tests                    | Speed  | DeviceHub Example                         |
| --------------- | -------------------------------- | ------ | ----------------------------------------- |
| **Unit**        | Single class/method in isolation | ~1ms   | `DeviceService.create()` with mocked repo |
| **Integration** | Multiple components together     | ~100ms | Controller + Service + H2 DB              |
| **E2E**         | Entire system black-box          | ~1sec+ | Docker container, real HTTP calls         |

**DeviceHub current coverage (44 tests):**

```
Unit Tests (DeviceServiceTest):
- shouldCreateDevice_whenValidRequest
- shouldThrowException_whenDeviceNotFound
- shouldEnforceBusinessRule_whenDeviceInUse

Integration Tests (DeviceApiIntegrationTest):
- shouldCreateAndRetrieveDevice
- shouldReturnProblemDetails_onValidationError

E2E Tests (with @SpringBootTest):
- fullCrudWorkflow
```

**What's missing: Contract Tests**

Contract tests verify that your API keeps its promises to consumers:

```java
// Without contract tests:
// You rename 'creationTime' to 'createdAt'
// Deploy to production
// Partner's app breaks at 3 AM
// Partner is ANGRY 😠

// With contract tests:
// You rename 'creationTime' to 'createdAt'
// Contract test fails: "Expected field 'creationTime' not found"
// You catch the breaking change BEFORE production
// Partner is happy 😊
```

### 🎓 Senior Developer Level

**Contract Testing with Pact:**

**Consumer-Driven Contract (CDC) Testing:**

```
┌─────────────────┐         ┌─────────────────┐
│    Consumer     │         │    Provider     │
│  (Mobile App)   │         │  (DeviceHub)    │
│                 │         │                 │
│ "When I call    │   ───►  │ Must respond    │
│  GET /devices/1 │         │ with id, name,  │
│  I expect..."   │         │ brand, state"   │
└─────────────────┘         └─────────────────┘
        │                           │
        │      Contract (Pact)      │
        │    ┌─────────────────┐    │
        └───►│ GET /devices/1  │◄───┘
             │ Response:       │
             │ {               │
             │   "id": 1,      │
             │   "name": "...",│
             │   "brand": "...",│
             │   "state": "..." │
             │ }               │
             └─────────────────┘
```

**Consumer test (in Mobile App repo):**

```java
@ExtendWith(PactConsumerTestExt.class)
class DeviceServiceConsumerTest {

    @Pact(consumer = "MobileApp", provider = "DeviceHub")
    public RequestResponsePact getDevicePact(PactDslWithProvider builder) {
        return builder
            .given("Device 1 exists")
            .uponReceiving("A request for device 1")
                .path("/api/devices/1")
                .method("GET")
            .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                    .integerType("id", 1)
                    .stringType("name", "iPhone")
                    .stringType("brand", "Apple")
                    .stringType("state", "AVAILABLE")
                    .datetime("creationTime", "yyyy-MM-dd'T'HH:mm:ss"))
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getDevicePact")
    void testGetDevice(MockServer mockServer) {
        // Test that consumer correctly handles the expected response
        DeviceClient client = new DeviceClient(mockServer.getUrl());
        Device device = client.getDevice(1L);

        assertThat(device.getName()).isEqualTo("iPhone");
    }
}
```

**Provider verification (in DeviceHub repo):**

```java
@Provider("DeviceHub")
@PactBroker(url = "https://pact-broker.company.com")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DeviceControllerProviderTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("Device 1 exists")
    void setupDevice1() {
        deviceRepository.save(new Device(1L, "iPhone", "Apple", AVAILABLE));
    }
}
```

**What happens when you break the contract:**

```
You rename 'creationTime' to 'createdAt'
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│ Provider Verification Test                              │
│                                                         │
│ ❌ FAILED: Contract broken                              │
│                                                         │
│ Expected: Response contains field "creationTime"        │
│ Actual: Response contains field "createdAt"             │
│                                                         │
│ Consumers affected:                                     │
│   - MobileApp (contract version 1.0.3)                  │
│   - PartnerIntegration (contract version 2.1.0)         │
└─────────────────────────────────────────────────────────┘
```

**Alternative: Spring Cloud Contract**

```groovy
// contracts/shouldReturnDevice.groovy
Contract.make {
    request {
        method 'GET'
        url '/api/devices/1'
    }
    response {
        status 200
        body([
            id: 1,
            name: $(anyNonBlankString()),
            brand: $(anyNonBlankString()),
            state: $(regex('AVAILABLE|IN_USE|INACTIVE')),
            creationTime: $(iso8601DateTime())
        ])
        headers {
            contentType(applicationJson())
        }
    }
}
```

### 🎤 Interview Sound Bite

> "The test pyramid covers unit, integration, and E2E tests, but we're missing contract tests. Contract tests use Pact or Spring Cloud Contract for consumer-driven verification—consumers define their expected request/response format, and the provider runs verification tests. If I rename `creationTime` to `createdAt`, the contract test fails immediately, preventing breaking changes from reaching production. This is especially critical when external partners depend on our API."

---

## Q12: OSIV Disabled

### 🧒 Like I'm 8 (ELI8)

Imagine you're at the library. You check out a book, but instead of taking it home, you leave it on the counter and say "Hold this, I'll be back in 5 minutes!"

But then you take an HOUR. Meanwhile, other kids can't borrow that book because it's "checked out" by you.

**OSIV** is like leaving the book on the counter. You're holding onto something (a database connection) longer than you need.

**OSIV Disabled** means: Take the book, do your stuff, return it IMMEDIATELY. Then other kids can use it.

### 👶 Junior Developer Level

**OSIV = Open Session In View**

It's a pattern where the Hibernate session (database connection) stays open for the ENTIRE HTTP request, even during view rendering.

**Why it exists:**

```java
@Entity
public class Device {
    @ManyToOne(fetch = FetchType.LAZY)  // Lazy loading
    private User owner;
}

// In Controller (with OSIV ENABLED):
@GetMapping("/{id}")
public String devicePage(@PathVariable Long id, Model model) {
    Device device = deviceService.findById(id);  // Session still open
    model.addAttribute("device", device);
    return "device-view";  // View renders, accesses device.getOwner().getName()
                           // Lazy load works because session is STILL OPEN
}
```

**The problem with OSIV:**

```
Request arrives
     │
     ▼
┌─────────────────────────────────────────────────┐
│  Session opens                                  │
│     │                                           │
│     ▼                                           │
│  Controller executes                            │
│     │                                           │
│     ▼                                           │
│  Service executes (queries done)                │
│     │                                           │
│     ▼                                           │
│  View renders (JSON serialization)              │  ← Session STILL OPEN!
│     │                                           │     Connection STILL HELD!
│     ▼                                           │
│  Response sent                                  │
│     │                                           │
│  Session finally closes                         │
└─────────────────────────────────────────────────┘
                    │
                    ▼
        Session open for 500ms instead of 50ms
        = 10x fewer concurrent requests possible!
```

**DeviceHub has OSIV disabled:**

```properties
# application.properties
spring.jpa.open-in-view=false  # ✓ Good!
```

This means:
- Session closes at the end of the `@Transactional` service method
- Lazy loading after that throws `LazyInitializationException`
- Connections are returned to pool immediately

### 🎓 Senior Developer Level

**Why disabling OSIV is a strength:**

| Aspect                         | OSIV Enabled                    | OSIV Disabled (DeviceHub)         |
| ------------------------------ | ------------------------------- | --------------------------------- |
| **Connection holding**         | Entire request duration         | Only during @Transactional        |
| **Concurrent capacity**        | Lower (connections held longer) | Higher (fast connection turnover) |
| **N+1 queries**                | Can happen silently in view     | Fails fast with exception         |
| **Lazy loading in controller** | Works (dangerous!)              | Fails (forces proper DTO design)  |
| **Debugging**                  | Harder (queries scattered)      | Easier (all queries in service)   |

**The N+1 problem with OSIV:**

```java
// With OSIV enabled, this works but is TERRIBLE:
@GetMapping
public List<Device> listDevices() {
    List<Device> devices = deviceRepository.findAll();  // 1 query
    return devices;  // Jackson serializes, lazy-loads owner for EACH device
                     // = N additional queries (N+1 problem!)
}

// Log shows:
// Query: SELECT * FROM devices                     -- 1 query
// Query: SELECT * FROM users WHERE id = 1          -- +1
// Query: SELECT * FROM users WHERE id = 2          -- +1
// Query: SELECT * FROM users WHERE id = 3          -- +1
// ... 43 million more queries 💥
```

**With OSIV disabled, you're forced to do it right:**

```java
// Option 1: Fetch eagerly in repository
@Query("SELECT d FROM Device d JOIN FETCH d.owner")
List<Device> findAllWithOwner();

// Option 2: Use DTO projection (best)
@Query("SELECT new com.example.DeviceDto(d.id, d.name, d.owner.name) FROM Device d")
List<DeviceDto> findAllAsDto();

// Option 3: EntityGraph
@EntityGraph(attributePaths = {"owner"})
List<Device> findAll();
```

**Exception you'll see if you access lazy data after session close:**

```
org.hibernate.LazyInitializationException:
    could not initialize proxy - no Session
```

**This is a GOOD thing** - it catches bad design early!

### 🎤 Interview Sound Bite

> "OSIV disabled is a strength—it means database connections are returned to the pool immediately after the `@Transactional` method completes, rather than being held for the entire request duration. This improves concurrent capacity and forces proper DTO design. If we accidentally try to lazy-load data in the controller, we get a `LazyInitializationException` which catches the problem early. The alternative—OSIV enabled—leads to hidden N+1 queries and connection pool exhaustion under load."

---

## 🔗 Cross-References

| Topic                | Related Topics                               |
| -------------------- | -------------------------------------------- |
| Q1 (OOM)             | Q14 (Pagination), Q8 (Caching)               |
| Q2 (DDoS)            | Q27 (Rate Limiting), Q28 (Circuit Breaker)   |
| Q3 (Race Condition)  | Q22 (ETag Headers), Q23 (Optimistic Locking) |
| Q4 (Payload DoS)     | Q24 (Validation Flow), Q2 (DDoS)             |
| Q5 (PUT/PATCH)       | Q25 (PUT vs PATCH Semantics)                 |
| Q6 (H2/NoSQL)        | Q31 (MongoDB Consideration)                  |
| Q7 (SLIs/SLOs)       | Q32 (Observability Metrics)                  |
| Q8 (Caching)         | See NOTES_FAQ_INFRASTRUCTURE.md              |
| Q9 (MDC)             | Q33 (MDC Implementation)                     |
| Q10 (Idempotency)    | Q28 (Circuit Breaker for retries)            |
| Q11 (Contract Tests) | Q34 (Contract Testing Deep Dive)             |
| Q12 (OSIV)           | Q3 (Lazy Loading in Race Conditions)         |

---

*See also:*
- **NOTES_FAQ_INFRASTRUCTURE.md** for Questions 13-24
- **NOTES_FAQ_ADVANCED.md** for Questions 25-34 + Company Research
- **NOTES_CODE.md** for implementation examples
