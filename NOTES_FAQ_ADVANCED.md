# DeviceHub API - Interview FAQ: Advanced Concepts & Company Research

**Purpose**: Deep understanding of advanced patterns, company research, and interview roleplay preparation.
**Format**: Each question answered at 3 levels (ELI8 → Junior → Senior)
**Cross-references**: Links to NOTES_FAQ_CORE.md and NOTES_FAQ_INFRASTRUCTURE.md

---

## 📚 Table of Contents

### Advanced Technical Questions
25. [Q25: PUT vs PATCH semantics](#q25-put-vs-patch-semantics)
26. [Q26: OAuth2/JWT at Edge vs In-App](#q26-oauth2jwt-at-edge-vs-in-app)
27. [Q27: Bucket4j and Token Bucket Algorithm](#q27-bucket4j-and-token-bucket-algorithm)
28. [Q28: Circuit Breaker States and Fallbacks](#q28-circuit-breaker-states-and-fallbacks)
29. [Q29: hashCode and equals Deep Dive](#q29-hashcode-and-equals-deep-dive)
30. [Q30: Virtual Threads Detailed](#q30-virtual-threads-detailed)
31. [Q31: MongoDB for v2 Schema Flexibility](#q31-mongodb-for-v2-schema-flexibility)
32. [Q32: Observability Acronyms (p50/p95/p99)](#q32-observability-acronyms-p50p95p99)
33. [Q33: MDC RequestId Filter Implementation](#q33-mdc-requestid-filter-implementation)
34. [Q34: Contract Testing Elaboration](#q34-contract-testing-elaboration)

### Bonus Sections
- [Flyway vs Liquibase Trade-offs](#flyway-vs-liquibase-trade-offs)
- [Company Research: 1GLOBAL](#company-research-1global)
- [Company Research: Nord Security](#company-research-nord-security)
- [Mock Interview Roleplay Scripts](#mock-interview-roleplay-scripts)
- [Questions to Ask Interviewers](#questions-to-ask-interviewers)

---

## Q25: PUT vs PATCH semantics

### 🧒 Like I'm 8 (ELI8)

**PUT** = Replacing your entire LEGO castle with a new one. Even if you just wanted to change the flag, you rebuild EVERYTHING.

**PATCH** = Just changing the flag on your castle. Everything else stays the same.

### 👶 Junior Developer Level

**PUT - Full replacement:**

```http
# Current state:
{"id": 1, "name": "iPhone", "brand": "Apple", "state": "AVAILABLE"}

# PUT request (replace everything):
PUT /api/devices/1
{"name": "New Phone", "brand": "Samsung", "state": "IN_USE"}

# Result:
{"id": 1, "name": "New Phone", "brand": "Samsung", "state": "IN_USE"}
```

If you omit a field in PUT:

```http
PUT /api/devices/1
{"name": "New Phone"}  # Missing brand and state!

# Result (problematic):
{"id": 1, "name": "New Phone", "brand": null, "state": null}  # 😱 Data lost!
```

**PATCH - Partial update:**

```http
# Current state:
{"id": 1, "name": "iPhone", "brand": "Apple", "state": "AVAILABLE"}

# PATCH request (change only name):
PATCH /api/devices/1
{"name": "New Phone"}

# Result:
{"id": 1, "name": "New Phone", "brand": "Apple", "state": "AVAILABLE"}  # ✓
```

**The rule:**
- **PUT**: Client sends the COMPLETE resource
- **PATCH**: Client sends only the fields to change

### 🎓 Senior Developer Level

**Problem in DeviceHub:**

Currently both PUT and PATCH use the same DTO:

```java
// PROBLEM: Same DTO for PUT and PATCH
public record DeviceUpdateRequest(
    String name,
    String brand,
    DeviceState state
) {}
```

With PUT, null could mean "set to null" OR "I forgot to include it."

**Solution: Separate DTOs**

```java
// PUT: All fields required
public record DevicePutRequest(
    @NotBlank String name,
    @NotBlank String brand,
    @NotNull DeviceState state
) {}

// PATCH: All fields optional (use Optional or JSON merge patch)
public record DevicePatchRequest(
    Optional<String> name,
    Optional<String> brand,
    Optional<DeviceState> state
) {}
```

**Or use JSON Merge Patch (RFC 7396):**

```java
@PatchMapping(path = "/{id}", consumes = "application/merge-patch+json")
public ResponseEntity<DeviceResponse> patchDevice(
        @PathVariable Long id,
        @RequestBody JsonMergePatch patch) {

    DeviceResponse current = deviceService.findById(id);
    DeviceResponse patched = applyPatch(patch, current);
    return ResponseEntity.ok(deviceService.update(id, patched));
}

private DeviceResponse applyPatch(JsonMergePatch patch, DeviceResponse target) {
    JsonValue patched = patch.apply(objectMapper.convertValue(target, JsonValue.class));
    return objectMapper.convertValue(patched, DeviceResponse.class);
}
```

**Explicit null handling:**

```java
// With Optional, you can distinguish:
// - Optional.empty() = field not sent (don't change)
// - Optional.of(value) = set to this value
// - Optional.of(null) = explicitly set to null (if allowed)

public DeviceResponse partialUpdate(Long id, DevicePatchRequest request) {
    Device device = findDevice(id);

    request.name().ifPresent(name -> {
        if (name == null) throw new ValidationException("Name cannot be null");
        device.setName(name);
    });

    request.brand().ifPresent(device::setBrand);
    request.state().ifPresent(device::setState);

    return toResponse(deviceRepository.save(device));
}
```

### 🎤 Interview Sound Bite

> "PUT is a full replacement—clients must send the entire resource, and missing fields should fail validation or default to null. PATCH is partial—only fields in the request body are updated. The current implementation uses the same DTO for both, which is a gap. I'd use separate DTOs: PUT with all `@NotNull` validations, PATCH with `Optional<T>` fields to distinguish 'not sent' from 'explicitly null'."

---

## Q26: OAuth2/JWT at Edge vs In-App

### 🧒 Like I'm 8 (ELI8)

Imagine a theme park with 50 rides. Each ride has a person checking if you have a wristband.

**Option 1 (Edge - at the park entrance):** Check your ticket ONCE at the gate, give you a wristband, and all rides just look for the wristband.

**Option 2 (In-App - at each ride):** Every ride has to call the ticket office: "Is ticket #12345 valid?" This is SLOW and if the ticket office closes, no rides work!

The gate is smarter and faster.

### 👶 Junior Developer Level

**OAuth2 Flow:**

```
┌──────────────┐
│    User      │
│   (Mobile)   │
└──────┬───────┘
       │ 1. Login with username/password
       ▼
┌──────────────┐
│ Auth Server  │  (Keycloak, Auth0, Okta)
│   (IdP)      │
└──────┬───────┘
       │ 2. Returns JWT token
       ▼
┌──────────────┐
│    User      │
│   (Mobile)   │
└──────┬───────┘
       │ 3. API request with JWT
       ▼
┌──────────────┐
│ DeviceHub    │
│    API       │
└──────────────┘
```

**JWT structure:**

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user123",
    "name": "John Doe",
    "roles": ["USER", "ADMIN"],
    "iat": 1706100000,
    "exp": 1706103600,
    "iss": "https://auth.company.com"
  },
  "signature": "..."
}
```

**Option A: Validate at Edge (API Gateway):**

```
                    ┌─────────────────────────────────┐
Client ────────────►│     API Gateway (Kong/Envoy)    │
  (with JWT)        │  - Validate JWT signature       │
                    │  - Check expiration             │
                    │  - Extract user ID and roles    │
                    └──────────────┬──────────────────┘
                                   │ (Forward trusted headers)
                                   │ X-User-Id: user123
                                   │ X-User-Roles: USER,ADMIN
                                   ▼
                    ┌─────────────────────────────────┐
                    │     DeviceHub API               │
                    │  - Trust headers from gateway   │
                    │  - No JWT validation needed     │
                    └─────────────────────────────────┘
```

**Option B: Validate In-App:**

```
                    ┌─────────────────────────────────┐
Client ────────────►│     API Gateway                 │
  (with JWT)        │  - Just routes traffic          │
                    │  - No auth logic                │
                    └──────────────┬──────────────────┘
                                   │ (Forward JWT as-is)
                                   ▼
                    ┌─────────────────────────────────┐
                    │     DeviceHub API               │
                    │  - Validate JWT signature       │
                    │  - Check expiration             │
                    │  - Extract claims               │
                    └─────────────────────────────────┘
```

### 🎓 Senior Developer Level

**Trade-offs:**

| Aspect           | Edge (Gateway)                      | In-App                     |
| ---------------- | ----------------------------------- | -------------------------- |
| **Performance**  | ✅ One validation for all services   | ❌ Each service validates   |
| **Latency**      | ✅ Services skip crypto              | ❌ ~1-5ms per validation    |
| **Key rotation** | ✅ Update one place                  | ❌ Update all services      |
| **Complexity**   | ❌ Requires gateway                  | ✅ Self-contained           |
| **Debugging**    | ❌ Headers can be spoofed internally | ✅ Full token context       |
| **Offline**      | ❌ Gateway is SPOF                   | ✅ Each service independent |

**When to use which:**

| Scenario                     | Recommendation         |
| ---------------------------- | ---------------------- |
| Microservices (10+ services) | Edge validation        |
| Single service / monolith    | In-app validation      |
| Zero-trust internal network  | In-app (re-validate)   |
| High-traffic (100K req/sec)  | Edge (reduce CPU load) |

**For DeviceHub at scale (43M devices, 100K req/sec):**

```
I would recommend EDGE validation because:

1. 100K req/sec × 2ms JWT validation = 200,000ms CPU per second saved
2. Single point for key rotation
3. Gateway already exists for rate limiting
4. Internal network is trusted (Kubernetes)

But with a fallback:
- Services SHOULD still verify signature for defense-in-depth
- Use caching for JWT verification results (60s TTL)
```

**Implementation pattern:**

```java
// Gateway validated - just extract headers
@Component
public class GatewayTrustFilter extends OncePerRequestFilter {

    @Value("${gateway.trusted-header:X-Gateway-Trust}")
    private String trustHeader;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Only trust headers if request came through gateway
        if ("true".equals(request.getHeader(trustHeader))) {
            String userId = request.getHeader("X-User-Id");
            String roles = request.getHeader("X-User-Roles");

            if (userId != null) {
                List<SimpleGrantedAuthority> authorities = parseRoles(roles);
                Authentication auth = new PreAuthenticatedAuthenticationToken(
                    userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }
}
```

### 🎤 Interview Sound Bite

> "For a microservices architecture at 100K req/sec, I'd validate JWTs at the API Gateway and forward trusted headers internally. This reduces CPU load—each service saves 1-2ms of crypto per request—and centralizes key rotation. The gateway adds `X-User-Id` and `X-User-Roles` headers, and internal services trust these. For defense-in-depth, services can optionally re-verify the signature with result caching."

---

## Q27: Bucket4j and Token Bucket Algorithm

### 🧒 Like I'm 8 (ELI8)

Imagine you have a bucket with 10 game tokens. Every minute, 2 new tokens appear in the bucket (refill).

To play a game, you need 1 token. If you play 10 games fast, your bucket is empty! You have to WAIT for more tokens.

This stops greedy kids from hogging all the games.

### 👶 Junior Developer Level

**Token Bucket Algorithm:**

```
Bucket capacity: 10 tokens
Refill rate: 2 tokens per second

Time 0: [🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢] = 10 tokens

Request 1: consume 1 token
[🟢🟢🟢🟢🟢🟢🟢🟢🟢⚪] = 9 tokens

Requests 2-10: consume 9 tokens
[⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪] = 0 tokens

Request 11: DENIED! Wait for refill.
→ 429 Too Many Requests

After 1 second: +2 tokens
[🟢🟢⚪⚪⚪⚪⚪⚪⚪⚪] = 2 tokens

Requests 12-13: allowed
[⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪] = 0 tokens
```

**Bucket4j implementation:**

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

```java
@Component
public class RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String clientId) {
        return buckets.computeIfAbsent(clientId, this::createBucket);
    }

    private Bucket createBucket(String clientId) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(100)                    // Max 100 tokens
            .refillGreedy(10, Duration.ofSeconds(1))  // Add 10 tokens/sec
            .build();

        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    public boolean tryConsume(String clientId) {
        return resolveBucket(clientId).tryConsume(1);
    }
}
```

**Filter for global rate limiting:**

```java
@Component
@Order(1)  // Run early in filter chain
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String clientId = extractClientId(request);

        if (!rateLimiter.tryConsume(clientId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                {"error": "Rate limit exceeded", "retryAfter": 1}
                """);
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractClientId(HttpServletRequest request) {
        // Priority: API key > JWT subject > IP address
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) return "api:" + apiKey;

        String userId = request.getHeader("X-User-Id");
        if (userId != null) return "user:" + userId;

        return "ip:" + request.getRemoteAddr();
    }
}
```

### 🎓 Senior Developer Level

**Distributed rate limiting with Redis:**

For multiple app instances, you need shared state:

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.7.0</version>
</dependency>
```

```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public ProxyManager<String> proxyManager(RedissonClient redissonClient) {
        return Bucket4jRedisson.casBasedBuilder(redissonClient)
            .build();
    }
}

@Component
public class DistributedRateLimiter {

    private final ProxyManager<String> proxyManager;

    public boolean tryConsume(String clientId) {
        BucketConfiguration config = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(100)
                .refillGreedy(10, Duration.ofSeconds(1))
                .build())
            .build();

        Bucket bucket = proxyManager.builder()
            .build(clientId, config);

        return bucket.tryConsume(1);
    }
}
```

**Tiered rate limits (different limits for different users):**

```java
public enum RateLimitTier {
    FREE(10, 1),       // 10 req/sec
    BASIC(100, 1),     // 100 req/sec
    PREMIUM(1000, 1);  // 1000 req/sec

    private final int capacity;
    private final int refillPerSecond;

    RateLimitTier(int capacity, int refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = capacity;  // Refill to full each second
    }

    public BucketConfiguration toConfig() {
        return BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillPerSecond, Duration.ofSeconds(1))
                .build())
            .build();
    }
}

// Usage
public boolean tryConsume(String clientId, RateLimitTier tier) {
    Bucket bucket = proxyManager.builder()
        .build(clientId, tier.toConfig());
    return bucket.tryConsume(1);
}
```

**Response headers for client awareness:**

```java
@Override
protected void doFilterInternal(...) {
    Bucket bucket = rateLimiter.resolveBucket(clientId);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    // Add rate limit headers
    response.setHeader("X-RateLimit-Limit", String.valueOf(bucket.getConfiguration().getBandwidths()[0].getCapacity()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
    response.setHeader("X-RateLimit-Reset", String.valueOf(
        System.currentTimeMillis() + probe.getNanosToWaitForRefill() / 1_000_000));

    if (!probe.isConsumed()) {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(
            probe.getNanosToWaitForRefill() / 1_000_000_000));
        return;
    }

    chain.doFilter(request, response);
}
```

### 🎤 Interview Sound Bite

> "Bucket4j implements the token bucket algorithm for rate limiting. Each client gets a bucket with a fixed capacity that refills at a steady rate. For distributed deployments, I'd use Bucket4j with Redis as the backend so all instances share the same counters. The filter adds `X-RateLimit-Remaining` headers so clients know their budget, and returns `429 Too Many Requests` with a `Retry-After` header when limits are exceeded."

---

## Q28: Circuit Breaker States and Fallbacks

### 🧒 Like I'm 8 (ELI8)

Imagine your toaster keeps burning toast. After the 5th burned toast, Mom puts a sign on it: "DON'T USE - BROKEN."

Every 5 minutes, someone tests it: "Is it fixed?" If yes, remove the sign. If no, keep the sign.

The **Circuit Breaker** is like Mom. When something keeps failing, it stops everyone from trying. Periodically, it tests if things are fixed.

### 👶 Junior Developer Level

**The three states:**

```
    ┌─────────────────────────────────────────────────────────────┐
    │                                                             │
    │   🟢 CLOSED              🟡 HALF-OPEN           🔴 OPEN     │
    │   (Normal)               (Testing)              (Blocked)   │
    │                                                             │
    │   Requests               Limited test           All fail    │
    │   pass through           requests allowed       immediately │
    │                                                             │
    │   ─────────────────────────────────────────────────────     │
    │                                                             │
    │   Failure count          If test succeeds       Wait timer  │
    │   tracked                → CLOSED               Then test   │
    │                          If test fails                      │
    │   If failures            → OPEN                             │
    │   exceed threshold                                          │
    │   → OPEN                                                    │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘

Flow:
CLOSED ──(5 failures)──► OPEN ──(30 sec wait)──► HALF-OPEN ──(test passes)──► CLOSED
                                                     │
                                                     └──(test fails)──► OPEN
```

**Resilience4j implementation:**

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

```java
@Service
public class ExternalDeviceService {

    @CircuitBreaker(name = "externalApi", fallbackMethod = "fallback")
    public DeviceInfo getDeviceInfo(Long deviceId) {
        // Call external API that might fail
        return externalApiClient.getDevice(deviceId);
    }

    // Fallback when circuit is OPEN or call fails
    private DeviceInfo fallback(Long deviceId, Exception e) {
        log.warn("Circuit breaker fallback for device {}: {}", deviceId, e.getMessage());
        return new DeviceInfo(deviceId, "Unknown", "UNAVAILABLE");
    }
}
```

**Configuration:**

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      externalApi:
        failureRateThreshold: 50        # Open if 50% of calls fail
        minimumNumberOfCalls: 10        # Need at least 10 calls to calculate rate
        waitDurationInOpenState: 30s    # Stay OPEN for 30 seconds
        permittedNumberOfCallsInHalfOpenState: 3  # Allow 3 test calls
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10           # Look at last 10 calls
```

### 🎓 Senior Developer Level

**Advanced configuration for production:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      externalApi:
        # Failure detection
        failureRateThreshold: 50
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 2s
        minimumNumberOfCalls: 10

        # State transitions
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 5
        automaticTransitionFromOpenToHalfOpenEnabled: true

        # Sliding window
        slidingWindowType: TIME_BASED
        slidingWindowSize: 60  # 60 seconds

        # What counts as failure
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.HttpServerErrorException
        ignoreExceptions:
          - com.devicehub.api.exception.DeviceNotFoundException
```

**Fallback strategies:**

```java
@Service
public class DeviceEnrichmentService {

    private final ExternalApiClient externalApi;
    private final CacheService cache;

    @CircuitBreaker(name = "enrichment", fallbackMethod = "enrichmentFallback")
    @Retry(name = "enrichment", fallbackMethod = "enrichmentFallback")
    @RateLimiter(name = "enrichment")
    public EnrichedDevice enrich(Device device) {
        DeviceMetadata metadata = externalApi.getMetadata(device.getId());
        return new EnrichedDevice(device, metadata);
    }

    // Fallback 1: Return cached data
    private EnrichedDevice enrichmentFallback(Device device, Exception e) {
        log.warn("Using cached data for device {}", device.getId());

        DeviceMetadata cached = cache.get("metadata:" + device.getId());
        if (cached != null) {
            return new EnrichedDevice(device, cached);
        }

        // Fallback 2: Return degraded response
        return new EnrichedDevice(device, DeviceMetadata.unknown());
    }
}
```

**Monitoring circuit breaker health:**

```java
@Component
@RequiredArgsConstructor
public class CircuitBreakerMonitor {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetrics() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker '{}' state changed: {} -> {}",
                        cb.getName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState());

                    meterRegistry.counter("circuit_breaker_state_change",
                        "name", cb.getName(),
                        "from", event.getStateTransition().getFromState().name(),
                        "to", event.getStateTransition().getToState().name()
                    ).increment();
                })
                .onError(event -> {
                    meterRegistry.counter("circuit_breaker_errors",
                        "name", cb.getName(),
                        "exception", event.getThrowable().getClass().getSimpleName()
                    ).increment();
                });
        });
    }
}
```

**Actuator endpoint:**

```properties
management.endpoints.web.exposure.include=health,circuitbreakers
management.health.circuitbreakers.enabled=true
```

```http
GET /actuator/circuitbreakers

{
  "circuitBreakers": {
    "externalApi": {
      "failureRate": "25.0%",
      "slowCallRate": "0.0%",
      "failureRateThreshold": "50.0%",
      "slowCallRateThreshold": "80.0%",
      "bufferedCalls": 20,
      "failedCalls": 5,
      "slowCalls": 0,
      "slowFailedCalls": 0,
      "notPermittedCalls": 0,
      "state": "CLOSED"
    }
  }
}
```

### 🎤 Interview Sound Bite

> "Circuit breakers prevent cascading failures. There are three states: CLOSED (normal), OPEN (all calls fail fast), and HALF-OPEN (testing if the downstream service recovered). I'd configure Resilience4j with a 50% failure threshold, 30-second open duration, and 5 test calls in half-open state. The fallback method returns cached data or a degraded response. I'd expose circuit breaker state via Actuator and alert on state transitions."

---

## Q29: hashCode and equals Deep Dive

### 🧒 Like I'm 8 (ELI8)

Imagine every kid at school gets a locker number based on their name. "Alex" = Locker 42. "Alex" always goes to Locker 42.

**hashCode** = Your locker number (quick lookup)
**equals** = "Are you REALLY the same person?" (detailed check)

If two kids have the same locker number (hashCode), you still check if they're the same kid (equals). But if they're the same kid, they MUST have the same locker!

### 👶 Junior Developer Level

**The contract:**

1. **If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` MUST be true**
2. If `a.hashCode() == b.hashCode()`, `equals` might be true OR false (collision OK)
3. `equals` must be reflexive, symmetric, transitive, consistent

**Why it matters for JPA:**

```java
@Entity
public class Device {
    @Id
    private Long id;
    private String name;

    // WRONG: Default equals/hashCode compares memory address
    // Two Device objects with same id would be "different"!
}

// In a Set:
Set<Device> devices = new HashSet<>();
Device d1 = deviceRepository.findById(1L);  // Returns new object
Device d2 = deviceRepository.findById(1L);  // Returns another new object

devices.add(d1);
devices.add(d2);
devices.size();  // Returns 2! But they represent the same device!
```

**Correct implementation:**

```java
@Entity
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return id != null && id.equals(device.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();  // Constant for all Device objects
    }
}
```

**Wait, why constant hashCode?**

Because the ID might be `null` before the entity is persisted!

```java
Device d1 = new Device();  // id = null
set.add(d1);               // hashCode based on id would be for "null"

deviceRepository.save(d1); // Now id = 1

set.contains(d1);          // If hashCode changed, it's in wrong bucket!
                           // Returns FALSE even though it's the same object!
```

### 🎓 Senior Developer Level

**The JPA entity hashCode problem:**

| Approach         | Pros                    | Cons                               |
| ---------------- | ----------------------- | ---------------------------------- |
| **Use id**       | Natural, unique         | ID is null before save             |
| **Constant**     | Works before/after save | All entities in same bucket (slow) |
| **Business key** | Stable, meaningful      | Need immutable business field      |

**Best practice: Use business key if available**

```java
@Entity
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String serialNumber;  // Immutable business key

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Device device)) return false;
        return serialNumber != null && serialNumber.equals(device.serialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serialNumber);  // Based on immutable field
    }
}
```

**Or Hibernate's recommendation (id-based with null safety):**

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
    Device device = (Device) o;
    return id != null && Objects.equals(id, device.id);
}

@Override
public int hashCode() {
    return getClass().hashCode();  // Constant per entity type
}
```

**Lombok caution:**

```java
// DON'T use @Data on entities!
@Data  // Includes @EqualsAndHashCode which uses all fields!
@Entity
public class Device {
    private Long id;
    private String name;  // Mutable! Changes during updates
}

// DO use @EqualsAndHashCode carefully
@Entity
@Getter @Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Device {
    @Id
    @EqualsAndHashCode.Include
    private Long id;

    private String name;
}
```

**DeviceHub current implementation:**

```java
// From Device.java - uses @Data which is RISKY
@Data
@Entity
@Table(name = "devices")
public class Device { ... }
```

**Recommended fix:**

```java
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ... fields

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Device device)) return false;
        return id != null && id.equals(device.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

### 🎤 Interview Sound Bite

> "For JPA entities, the challenge is that ID is null before persist. Using ID in hashCode means the hash changes after save, breaking HashSet behavior. The recommended pattern is `equals` based on non-null ID check, and a constant `hashCode` returning `getClass().hashCode()`. This means all entities land in the same hash bucket (O(n) lookup), but that's acceptable for entity collections. If there's an immutable business key like serial number, I'd use that instead."

---

## Q30: Virtual Threads Detailed

### 🧒 Like I'm 8 (ELI8)

**Regular threads** = Big trucks that can carry ONE package. You need 100 packages? You need 100 big trucks. Trucks are expensive!

**Virtual threads** = Tiny drones that can carry ONE package. You need 100 packages? Release 100 drones! Drones are cheap and share the same runway.

When a drone waits (for a door to open), it parks and another drone uses the runway. No wasted time!

### 👶 Junior Developer Level

**Platform Thread (before Java 21):**

```java
// Each thread = ~1MB memory + OS thread
ExecutorService executor = Executors.newFixedThreadPool(200);

// 1000 concurrent requests, but only 200 threads
// 800 requests wait in queue
for (int i = 0; i < 1000; i++) {
    executor.submit(() -> {
        // If this sleeps or waits for I/O, thread is blocked
        Thread.sleep(1000);  // Thread does NOTHING for 1 second
    });
}
```

**Virtual Thread (Java 21+):**

```java
// Each virtual thread = ~few KB memory, managed by JVM
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// 1000 concurrent requests = 1000 virtual threads (cheap!)
for (int i = 0; i < 1000; i++) {
    executor.submit(() -> {
        // When this sleeps, virtual thread is "parked"
        // The carrier (platform) thread handles other virtual threads
        Thread.sleep(1000);  // Carrier thread is FREE to do other work!
    });
}
```

**DeviceHub enables virtual threads:**

```properties
# application.properties
spring.threads.virtual.enabled=true
```

That's it! Spring Boot automatically uses virtual threads for request handling.

### 🎓 Senior Developer Level

**How virtual threads work:**

```
┌─────────────────────────────────────────────────────────────────┐
│                     JVM Thread Scheduler                        │
│                                                                 │
│  Carrier Threads (Platform):  [C1] [C2] [C3] [C4]              │
│  (Usually = CPU cores)                                          │
│                                                                 │
│  Virtual Threads:                                               │
│  [VT1-running on C1]                                            │
│  [VT2-running on C2]                                            │
│  [VT3-waiting for I/O] ←── parked, C3 now free                  │
│  [VT4-running on C3]   ←── C3 picked up VT4                     │
│  [VT5-waiting for DB]  ←── parked                               │
│  [VT6-runnable]        ←── waiting for a carrier                │
│  ...                                                            │
│  [VT1000-runnable]                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**When virtual thread "parks" (I/O, sleep, wait):**
1. JVM saves its stack (continuation)
2. Carrier thread is freed
3. Carrier picks up another virtual thread
4. When I/O completes, virtual thread is scheduled again

**Gotchas:**

**1. Pinning (carrier thread blocked):**

```java
// BAD: synchronized blocks PIN the carrier thread!
synchronized (lock) {
    database.query();  // Carrier is stuck, can't run other VTs
}

// GOOD: Use ReentrantLock instead
lock.lock();
try {
    database.query();  // Carrier can run other VTs while waiting
} finally {
    lock.unlock();
}
```

**2. Thread-local abuse:**

```java
// BAD: Thread locals with expensive objects
private static final ThreadLocal<ExpensiveObject> CACHE =
    ThreadLocal.withInitial(ExpensiveObject::new);

// With 1M virtual threads = 1M expensive objects!
// Memory explosion!

// GOOD: Use scoped values (Java 21+)
private static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

ScopedValue.where(CONTEXT, requestContext).run(() -> {
    // Access CONTEXT.get() here
});
```

**3. Connection pool exhaustion:**

```java
// With platform threads: 200 threads, 200 connections = OK
// With virtual threads: 10,000 VTs, but only 30 DB connections!

// Virtual threads wait for connection, but that's efficient:
// - VT parks while waiting for connection
// - Carrier is free to run other VTs
// - When connection available, VT continues

// BUT: Need to monitor HikariCP queue length
```

**Monitoring virtual threads:**

```java
// JFR events for virtual threads
// jdk.VirtualThreadStart
// jdk.VirtualThreadEnd
// jdk.VirtualThreadPinned <-- Important for debugging!

// Log pinned threads:
-Djdk.tracePinnedThreads=full
```

**When NOT to use virtual threads:**

| Scenario            | Recommendation                                 |
| ------------------- | ---------------------------------------------- |
| CPU-bound work      | Platform threads (no I/O wait to benefit from) |
| synchronized blocks | Platform threads (causes pinning)              |
| Native code (JNI)   | Platform threads (not virtual-thread-aware)    |
| Legacy libraries    | Test carefully for pinning                     |

### 🎤 Interview Sound Bite

> "Virtual threads are lightweight threads managed by the JVM—you can create millions with minimal memory overhead. When a virtual thread does I/O, it 'parks' and the carrier (OS thread) runs other virtual threads. Spring Boot 3.2 enables them with a single property. The gotchas are synchronized blocks causing 'pinning' (use ReentrantLock instead), and connection pool exhaustion since HikariCP still has a fixed size. I'd monitor `hikaricp_connections_pending` and JFR's `VirtualThreadPinned` events."

---

## Q31: MongoDB for v2 Schema Flexibility

### 🧒 Like I'm 8 (ELI8)

**SQL (PostgreSQL)** = A bookshelf where every book MUST have exactly the same sections: Title, Author, Pages. No exceptions!

**NoSQL (MongoDB)** = A toy box where each toy can be different. A car has wheels, a doll has clothes, a puzzle has pieces. They're all toys, but with different parts.

For devices, some devices might have "SIM cards" and some have "battery status." MongoDB lets each device have different fields.

### 👶 Junior Developer Level

**When SQL gets awkward:**

```sql
-- Option 1: Sparse columns (most are NULL)
CREATE TABLE devices (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    brand VARCHAR(255),
    -- eSIM specific
    iccid VARCHAR(20),
    imsi VARCHAR(15),
    -- Router specific
    firmware_version VARCHAR(50),
    last_sync TIMESTAMP,
    -- Sensor specific
    battery_level INT,
    sensor_readings JSONB
    -- 50 more nullable columns...
);

-- Option 2: EAV pattern (hard to query)
CREATE TABLE device_attributes (
    device_id BIGINT,
    attribute_name VARCHAR(100),
    attribute_value TEXT
);

-- Option 3: JSON column (losing type safety)
CREATE TABLE devices (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    properties JSONB  -- Free-form JSON
);
```

**MongoDB solution:**

```javascript
// Each document can have different fields
db.devices.insertMany([
    {
        name: "eSIM-001",
        type: "esim",
        iccid: "8901234567890123456",
        imsi: "310150123456789",
        activatedAt: ISODate("2024-01-15"),
        profile: { carrier: "Verizon", plan: "Unlimited" }
    },
    {
        name: "Router-A1",
        type: "router",
        firmwareVersion: "2.4.1",
        lastSync: ISODate("2024-01-20"),
        connectedDevices: 12,
        config: { ssid: "Office-5G", security: "WPA3" }
    },
    {
        name: "Sensor-T1",
        type: "sensor",
        batteryLevel: 85,
        readings: [
            { timestamp: ISODate("2024-01-20T10:00:00"), temp: 22.5 },
            { timestamp: ISODate("2024-01-20T11:00:00"), temp: 23.1 }
        ]
    }
]);
```

**Spring Data MongoDB:**

```java
@Document(collection = "devices")
public class Device {
    @Id
    private String id;
    private String name;
    private String type;

    // Flexible attributes stored here
    private Map<String, Object> properties;
}

// Or typed subdocuments
@Document(collection = "devices")
public class EsimDevice {
    @Id
    private String id;
    private String name;
    private String iccid;
    private SimProfile profile;
}
```

### 🎓 Senior Developer Level

**Trade-offs:**

| Aspect                 | PostgreSQL                    | MongoDB                        |
| ---------------------- | ----------------------------- | ------------------------------ |
| **Schema flexibility** | Rigid (migrations required)   | ✅ Flexible (schema-less)       |
| **Transactions**       | ✅ Full ACID                   | Limited (multi-doc since 4.0)  |
| **Joins**              | ✅ Efficient                   | ❌ $lookup is slow              |
| **Indexing**           | ✅ B-tree, partial, expression | Flexible, but careful planning |
| **Query complexity**   | ✅ Complex joins, CTEs         | Aggregation pipeline           |
| **Data consistency**   | ✅ Constraints enforced        | Application-level              |
| **Scaling**            | Vertical (sharding complex)   | ✅ Horizontal (sharding native) |

**When to choose MongoDB:**

| Scenario                                       | Why                   |
| ---------------------------------------------- | --------------------- |
| Different device types with varying attributes | Schema flexibility    |
| High write throughput (events, logs)           | Optimized for writes  |
| Horizontal scaling needed                      | Native sharding       |
| Embedded documents (device + readings)         | No joins needed       |
| Rapid prototyping                              | Schema evolution easy |

**When to keep PostgreSQL:**

| Scenario              | Why                    |
| --------------------- | ---------------------- |
| Complex relationships | Efficient joins        |
| Financial data        | Full ACID transactions |
| Reporting needs       | SQL is powerful        |
| Existing expertise    | Team knows SQL         |
| Strict data integrity | Constraints enforced   |

**Hybrid approach (polyglot persistence):**

```
┌────────────────────────────────────────────────────────────────┐
│                      DeviceHub v2 Architecture                  │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  PostgreSQL (relational data)       MongoDB (flexible data)   │
│  ┌──────────────────────┐           ┌────────────────────┐    │
│  │ users                │           │ device_telemetry   │    │
│  │ organizations        │           │ device_configs     │    │
│  │ billing_accounts     │           │ audit_logs         │    │
│  │ device_registry      │◄─────────►│ device_metadata    │    │
│  │   (core: id, name)   │    Link   │   (flexible attrs) │    │
│  └──────────────────────┘           └────────────────────┘    │
│                                                                │
│  - Transactions                     - Flexible schema          │
│  - Joins for reporting              - High write volume        │
│  - Strong consistency               - Time-series data         │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

**Implementation:**

```java
// PostgreSQL entity (Spring Data JPA)
@Entity
public class Device {
    @Id
    private Long id;
    private String name;
    private String brand;
    private DeviceState state;
    // MongoDB document ID for extended attributes
    private String mongoDocId;
}

// MongoDB document (Spring Data MongoDB)
@Document(collection = "device_metadata")
public class DeviceMetadata {
    @Id
    private String id;
    private Long deviceId;  // Links to PostgreSQL
    private String type;
    private Map<String, Object> attributes;
    private List<TelemetryReading> recentReadings;
}

// Service combines both
@Service
public class DeviceService {
    private final DeviceRepository pgRepo;       // JPA
    private final DeviceMetadataRepository mongoRepo;  // MongoDB

    public EnrichedDevice findById(Long id) {
        Device device = pgRepo.findById(id).orElseThrow();
        DeviceMetadata metadata = mongoRepo.findByDeviceId(id);
        return new EnrichedDevice(device, metadata);
    }
}
```

### 🎤 Interview Sound Bite

> "For v2 with diverse device types, I'd consider MongoDB for the flexible schema—each device type can have different attributes without migrations. But I'd likely use a hybrid approach: PostgreSQL for core relational data (users, accounts, device registry) and MongoDB for flexible metadata and high-volume telemetry. This gives us ACID transactions where needed and schema flexibility where it helps. The key trade-off is that cross-store queries require application-level joins."

---

## Q32: Observability Acronyms (p50/p95/p99)

### 🧒 Like I'm 8 (ELI8)

You and your friends run races. You want to know how fast you usually are.

- **Average:** Add all times, divide by number of races. But one super-slow race makes it look bad!
- **p50 (median):** Half your races are faster, half are slower. This is your "normal" speed.
- **p99:** 99% of your races are faster than this. Only 1% are this slow. This is your "worst day" speed.

p99 is important because even if MOST runs are fast, the slow ones make customers angry!

### 👶 Junior Developer Level

**What does p95 = 250ms mean?**

Out of 100 requests:
- 95 requests finish in ≤ 250ms
- 5 requests take longer than 250ms

**Visual:**

```
Response times for 100 requests (sorted):

Request 1:   15ms ─┐
Request 2:   18ms  │
Request 3:   20ms  │
...               │
Request 50:  45ms ─┼─ p50 (median): 50% are faster
...               │
Request 95: 250ms ─┼─ p95: 95% are faster
Request 96: 280ms  │
Request 97: 350ms  │
Request 98: 500ms  │
Request 99: 800ms ─┼─ p99: 99% are faster
Request 100: 2.5s ─┘
```

**Why not use average?**

```
99 requests:  50ms each
1 request: 5000ms (5 seconds)

Average: (99 × 50 + 5000) / 100 = 99.5ms (looks great!)
p99: 5000ms (shows the real problem!)
```

The average hides the bad experience!

**Spring Boot Actuator metrics:**

```properties
management.endpoints.web.exposure.include=metrics,prometheus
```

```http
GET /actuator/metrics/http.server.requests

{
  "name": "http.server.requests",
  "measurements": [
    { "statistic": "COUNT", "value": 12456 },
    { "statistic": "TOTAL_TIME", "value": 156.78 },
    { "statistic": "MAX", "value": 2.5 }
  ],
  "availableTags": [
    { "tag": "uri", "values": ["/api/devices", "/api/devices/{id}"] },
    { "tag": "status", "values": ["200", "404", "500"] }
  ]
}
```

For percentiles, configure:

```java
@Bean
MeterRegistryCustomizer<MeterRegistry> metricsConfig() {
    return registry -> registry.config().meterFilter(
        new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith("http.server.requests")) {
                    return DistributionStatisticConfig.builder()
                        .percentiles(0.5, 0.95, 0.99)
                        .percentilesHistogram(true)
                        .build()
                        .merge(config);
                }
                return config;
            }
        });
}
```

### 🎓 Senior Developer Level

**SLI/SLO/SLA definitions:**

| Term    | Definition                                          | Example                        |
| ------- | --------------------------------------------------- | ------------------------------ |
| **SLI** | Service Level **Indicator** - What you measure      | p99 latency, error rate        |
| **SLO** | Service Level **Objective** - Your target           | p99 < 500ms, error rate < 0.1% |
| **SLA** | Service Level **Agreement** - Contract with penalty | 99.9% uptime or refund         |

**Setting SLOs for DeviceHub:**

```yaml
# SLOs definition (not code, but documentation)
slos:
  - name: availability
    indicator: (successful_requests / total_requests) * 100
    target: 99.9%
    window: 30 days

  - name: latency_p99
    indicator: http_server_requests_seconds{quantile="0.99"}
    target: < 500ms
    window: 30 days

  - name: latency_p50
    indicator: http_server_requests_seconds{quantile="0.5"}
    target: < 100ms
    window: 30 days
```

**Prometheus queries:**

```promql
# p99 latency
histogram_quantile(0.99,
  rate(http_server_requests_seconds_bucket{uri="/api/devices"}[5m]))

# Error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))

# Availability (% of non-5xx responses)
1 - (
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count[5m]))
)
```

**Alert on SLO breach:**

```yaml
# Prometheus alerting rules
groups:
  - name: devicehub-slos
    rules:
      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99,
            rate(http_server_requests_seconds_bucket{uri=~"/api/.*"}[5m])
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "p99 latency > 500ms for 5 minutes"

      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count[5m]))
          > 0.001
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Error rate > 0.1% for 5 minutes"
```

**Error budget:**

```
SLO: 99.9% availability = 0.1% allowed downtime

Per month:
- 30 days × 24 hours × 60 minutes = 43,200 minutes
- 0.1% of that = 43.2 minutes of allowed downtime

Error budget remaining:
- If we've had 20 minutes of errors: 23.2 minutes left
- If we've had 50 minutes of errors: BUDGET EXCEEDED, freeze deployments!
```

### 🎤 Interview Sound Bite

> "p99 means 99% of requests are faster than this value—it captures the 'worst case' user experience that averages hide. For DeviceHub, I'd set SLOs like 'p99 latency < 500ms' and 'error rate < 0.1%'. These are monitored via Prometheus with histogram metrics, and we'd alert when the p99 exceeds the threshold for 5 minutes. The error budget concept helps balance reliability with deployment velocity—once we've used our 43 minutes of allowed downtime per month, we freeze risky changes."

---

## Q33: MDC RequestId Filter Implementation

### 🧒 Like I'm 8 (ELI8)

Imagine you're at a busy restaurant with 50 orders happening at once. How do you know which plate goes with which table?

**Order number!** Table 7 ordered pizza = "Order #42". Everyone writes #42 on everything related to that order. When pizza is ready: "Order #42!" Table 7 knows it's theirs.

**MDC RequestId** is the order number for each web request. Every log message gets tagged with it, so you can find ALL logs for one user's request.

### 👶 Junior Developer Level

**Without MDC:**

```log
10:45:01 INFO  Processing device
10:45:01 INFO  Validating request
10:45:01 INFO  Processing device      <- Which request is this?
10:45:01 INFO  Database query started
10:45:01 ERROR Device not found       <- Which of the 3 requests failed?
10:45:01 INFO  Database query started
```

**With MDC:**

```log
10:45:01 [req-abc123] INFO  Processing device
10:45:01 [req-xyz789] INFO  Validating request
10:45:01 [req-def456] INFO  Processing device
10:45:01 [req-abc123] INFO  Database query started
10:45:01 [req-def456] ERROR Device not found   <- Now we know!
10:45:01 [req-xyz789] INFO  Database query started
```

**Implementation:**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**Configure logback to include it:**

```xml
<!-- logback-spring.xml -->
<pattern>%d{HH:mm:ss.SSS} [%X{requestId}] %-5level %logger{36} - %msg%n</pattern>
```

### 🎓 Senior Developer Level

**Full implementation with response timing:**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String CORRELATION_ID_KEY = "correlationId";

    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        long startTime = System.nanoTime();

        // Request ID: unique per request
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = generateRequestId();
        }

        // Correlation ID: spans multiple services (for distributed tracing)
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = requestId;  // Use request ID if no correlation
        }

        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());

        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            log.debug("Request started");
            chain.doFilter(request, response);
        } finally {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            MDC.put("durationMs", String.valueOf(duration));
            MDC.put("status", String.valueOf(response.getStatus()));

            log.info("Request completed");

            meterRegistry.timer("http.requests.traced",
                "method", request.getMethod(),
                "path", request.getRequestURI(),
                "status", String.valueOf(response.getStatus())
            ).record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

            MDC.clear();
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
```

**Structured JSON logging:**

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>method</includeMdcKeyName>
            <includeMdcKeyName>path</includeMdcKeyName>
            <includeMdcKeyName>durationMs</includeMdcKeyName>
            <includeMdcKeyName>status</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

**Output:**

```json
{
  "timestamp": "2024-01-24T10:45:01.123Z",
  "level": "INFO",
  "logger": "com.devicehub.api.controller.DeviceController",
  "message": "Request completed",
  "requestId": "abc123def456",
  "correlationId": "abc123def456",
  "method": "GET",
  "path": "/api/devices/1",
  "durationMs": "45",
  "status": "200"
}
```

**Finding related logs in Kibana/Loki:**

```
requestId:"abc123def456"
```

Shows ALL logs for that one request, across all services!

### 🎤 Interview Sound Bite

> "I'd implement MDC via a filter that runs first in the chain. It generates or accepts an `X-Request-Id` header, puts it in MDC, and clears MDC in a finally block. Logback includes it in every log line: `[%X{requestId}]`. For distributed tracing, I'd add a correlation ID that propagates across services. With JSON logging, these become searchable fields—you can query 'show all logs for request abc123' across your entire platform."

---

## Q34: Contract Testing Elaboration

### 🧒 Like I'm 8 (ELI8)

You and your friend are building a LEGO city together. You're building houses, they're building roads.

**The contract:** "Roads must be 4 studs wide, and house doors must face the road."

Before you meet, you each TEST against this rule. You check your doors face out, they check roads are 4 wide. When you meet, everything fits!

**Contract testing** is checking that your code follows the agreed rules BEFORE you deploy.

### 👶 Junior Developer Level

**The problem without contract tests:**

```
Mobile Team: "Our app expects { "id": 1, "name": "iPhone" }"
API Team: "We changed it to { "deviceId": 1, "deviceName": "iPhone" }"

Result: Mobile app breaks in production! 💥
```

**Contract testing with Pact:**

**1. Consumer (Mobile) writes expectations:**

```java
@Pact(consumer = "MobileApp")
public V4Pact createDevicePact(PactDslWithProvider builder) {
    return builder
        .given("device with ID 1 exists")
        .uponReceiving("a request for device 1")
            .path("/api/devices/1")
            .method("GET")
        .willRespondWith()
            .status(200)
            .body(new PactDslJsonBody()
                .integerType("id", 1)
                .stringType("name", "iPhone")
                .stringType("brand", "Apple")
            )
        .toPact(V4Pact.class);
}

@Test
@PactTestFor(providerName = "DeviceHub", pactMethod = "createDevicePact")
void getDevice_returnsDevice(MockServer mockServer) {
    // Test against mock
    DeviceResponse device = client.getDevice(mockServer.getUrl(), 1L);
    assertThat(device.name()).isEqualTo("iPhone");
}
```

This generates a "contract" file (pact.json) uploaded to a broker.

**2. Provider (DeviceHub) verifies against contract:**

```java
@Provider("DeviceHub")
@PactBroker(url = "https://pact-broker.company.com")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class DevicePactProviderTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("device with ID 1 exists")
    void setupDevice() {
        deviceRepository.save(new Device(1L, "iPhone", "Apple", AVAILABLE));
    }
}
```

If DeviceHub changes the response format, this test FAILS before deployment!

### 🎓 Senior Developer Level

**Contract testing vs other testing:**

| Test Type       | What it tests       | When it runs  | Catches          |
| --------------- | ------------------- | ------------- | ---------------- |
| **Unit**        | Single component    | Always        | Logic bugs       |
| **Integration** | Multiple components | Always        | Integration bugs |
| **Contract**    | API compatibility   | Before merge  | Breaking changes |
| **E2E**         | Full system         | Before deploy | System bugs      |

**Pact Broker workflow:**

```
┌──────────────────────────────────────────────────────────────────┐
│                      Pact Broker (central)                       │
│                                                                  │
│  Stores all contracts                                            │
│  Tracks verification status                                      │
│  Shows compatibility matrix                                      │
│                                                                  │
│  ┌─────────────────┐        ┌─────────────────┐                 │
│  │ MobileApp v2.1  │───────►│ DeviceHub v3.2  │                 │
│  │ Contract        │  ✓     │ Verified        │                 │
│  └─────────────────┘        └─────────────────┘                 │
│                                                                  │
│  ┌─────────────────┐        ┌─────────────────┐                 │
│  │ WebApp v1.5     │───────►│ DeviceHub v3.2  │                 │
│  │ Contract        │  ✗     │ FAILED          │                 │
│  └─────────────────┘        └─────────────────┘                 │
│                                                                  │
│  Can-I-Deploy check:                                             │
│  - DeviceHub v3.2 → MobileApp v2.1: ✓                           │
│  - DeviceHub v3.2 → WebApp v1.5: ✗ (blocked!)                   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**CI/CD integration:**

```yaml
# .github/workflows/api-deploy.yml
jobs:
  test:
    steps:
      - run: ./mvnw test

  verify-contracts:
    steps:
      - run: ./mvnw pact:verify -Dpact.verifier.publishResults=true

  can-i-deploy:
    needs: verify-contracts
    steps:
      - name: Check if safe to deploy
        run: |
          pact-broker can-i-deploy \
            --pacticipant DeviceHub \
            --version ${{ github.sha }} \
            --to-environment production

  deploy:
    needs: can-i-deploy
    steps:
      - run: ./deploy.sh
```

**Spring Cloud Contract (alternative to Pact):**

```yaml
# src/test/resources/contracts/device/shouldReturnDevice.yml
description: should return device by id
request:
  method: GET
  url: /api/devices/1
response:
  status: 200
  headers:
    Content-Type: application/json
  body:
    id: 1
    name: "iPhone"
    brand: "Apple"
    state: "AVAILABLE"
```

```java
// Auto-generated test
@AutoConfigureStubRunner(
    ids = "com.devicehub:devicehub-api:+:stubs:8080",
    stubsMode = StubsMode.LOCAL
)
class MobileClientContractTest {

    @Test
    void shouldGetDevice() {
        // Tests against generated WireMock stub
        DeviceResponse device = client.getDevice(1L);
        assertThat(device.name()).isEqualTo("iPhone");
    }
}
```

**When to use contract tests:**

| Scenario                                   | Use Contract Tests?         |
| ------------------------------------------ | --------------------------- |
| Multiple consumers (web, mobile, partners) | ✅ Definitely                |
| Single consumer, same team                 | ⚠️ Maybe (E2E might suffice) |
| Third-party API dependency                 | ✅ Yes (consumer-side)       |
| Internal microservices                     | ✅ Yes                       |
| Monolith                                   | ❌ No (no API boundary)      |

### 🎤 Interview Sound Bite

> "Contract tests ensure API compatibility between services. With Pact, consumers write expectations which generate contract files. Providers verify against these contracts in CI—if a breaking change is introduced, the build fails before deployment. The Pact Broker tracks which versions are compatible, and the 'can-i-deploy' check gates production deployments. This is essential at scale with multiple consumers—you can't manually test every combination."

---

## Flyway vs Liquibase Trade-offs

### Comparison Table

| Feature                  | Flyway                      | Liquibase            |
| ------------------------ | --------------------------- | -------------------- |
| **Configuration format** | SQL files only              | XML, YAML, JSON, SQL |
| **Learning curve**       | ✅ Very simple               | Moderate             |
| **Rollback**             | ❌ Manual SQL (Pro for auto) | ✅ Built-in           |
| **IDE support**          | Basic                       | ✅ IntelliJ plugin    |
| **Diff/Generate**        | ❌ Pro only                  | ✅ Free               |
| **Preconditions**        | ❌ No                        | ✅ Yes                |
| **Multi-database**       | ✅ Good                      | ✅ Excellent          |
| **Price**                | Community free, Pro paid    | ✅ All free           |

### When to Choose Flyway

- Team is comfortable with raw SQL
- Simple migrations (create, alter)
- No complex rollback requirements
- Want minimal learning curve

### When to Choose Liquibase

- Need database-agnostic migrations (YAML/XML)
- Complex migration logic with preconditions
- Want free rollback generation
- Need to generate migrations from schema diff

### DeviceHub Recommendation

**Liquibase** because:
- Free rollback support
- Preconditions (e.g., "only run if column doesn't exist")
- YAML format is readable
- Better for enterprise environments

---

## Company Research: 1GLOBAL

### Company Overview

**1GLOBAL** is a global connectivity platform for IoT and eSIM solutions.

**Key Facts:**
- **Industry**: Telecommunications, IoT, eSIM
- **Scale**: 43 million connected devices across 200+ countries
- **Traffic**: 100K+ requests per second
- **Locations**: UK-based, serving 12+ countries
- **Focus**: Enterprise connectivity, eSIM management

### Technical Relevance to DeviceHub

| 1GLOBAL Need              | DeviceHub Parallel        | Interview Point                                                                                         |
| ------------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------- |
| eSIM lifecycle management | Device state management   | "The state machine pattern (AVAILABLE → IN_USE → INACTIVE) maps to eSIM activation/deactivation cycles" |
| Global scale              | H2 → PostgreSQL migration | "I'd migrate to PostgreSQL with read replicas, using keyset pagination for 43M devices"                 |
| High availability         | No redundancy currently   | "I'd add circuit breakers, multi-region deployment, and graceful degradation"                           |
| Audit requirements        | creationTime only         | "I'd add @CreatedBy/@LastModifiedBy with Spring Security integration for compliance"                    |
| Rate limiting             | None implemented          | "Essential for partner APIs—I'd implement Bucket4j with tiered limits per API key"                      |

### Questions They Might Ask

1. "How would you handle activating 10,000 eSIMs simultaneously?"
   - Batch processing with partitioned queues
   - Async processing with status callbacks
   - Rate limiting to external carrier APIs

2. "We have partners integrating via API. How do you ensure reliability?"
   - Contract testing with Pact
   - Rate limiting and quotas
   - Detailed error responses with request IDs

3. "How do you handle regulatory requirements across 12 countries?"
   - Data residency (regional databases)
   - Audit logging with immutable storage
   - GDPR-compliant data handling

### Your Questions to Ask 1GLOBAL

1. "What's your current architecture for handling the 100K req/sec—is it regional or centralized?"
2. "How do you manage schema evolution with 43 million device records?"
3. "What's your approach to eSIM profile caching and invalidation?"
4. "How do you handle carrier API failures and retries?"

---

## Company Research: Nord Security

### Company Overview

**Nord Security** is a cybersecurity company known for NordVPN, NordPass, and NordLocker.

**Key Facts:**
- **Industry**: Cybersecurity, VPN, Privacy
- **Users**: 15+ million
- **Products**: NordVPN, NordPass, NordLocker, NordLayer
- **Location**: Panama (legal), Lithuania (engineering)
- **Focus**: Privacy, encryption, secure infrastructure

### Technical Relevance to DeviceHub

| Nord Security Need     | DeviceHub Parallel  | Interview Point                                                                          |
| ---------------------- | ------------------- | ---------------------------------------------------------------------------------------- |
| Device/client tracking | Device management   | "Similar pattern—tracking VPN client devices with state management"                      |
| High concurrency       | Virtual threads     | "Virtual threads handle thousands of concurrent connections efficiently"                 |
| Security first         | No auth implemented | "I'd add OAuth2/JWT with token validation at edge for defense-in-depth"                  |
| Global infrastructure  | Single instance     | "I'd design for multi-region with eventual consistency"                                  |
| Audit and compliance   | Basic creationTime  | "For security products, I'd add comprehensive audit logging with tamper-evident storage" |

### Questions They Might Ask

1. "How would you secure an API that manages VPN client devices?"
   - mTLS for service-to-service
   - JWT with short expiry for clients
   - Rate limiting per user/IP

2. "How do you handle a malicious user spawning millions of fake devices?"
   - Rate limiting per user account
   - Device attestation (e.g., Google SafetyNet)
   - Anomaly detection and account suspension

3. "What's your approach to zero-trust architecture?"
   - Verify every request (no implicit trust)
   - Minimal permissions (least privilege)
   - Encryption everywhere (TLS, encrypted storage)

### Your Questions to Ask Nord Security

1. "How do you balance user privacy with the need for operational telemetry?"
2. "What's your approach to handling concurrent sessions across multiple VPN regions?"
3. "How do you manage device trust across different client platforms (iOS, Android, desktop)?"
4. "What's your incident response process for security vulnerabilities?"

---

## Mock Interview Roleplay Scripts

### Script 1: The Architecture Deep Dive

**Interviewer**: "Looking at your Device API, how would you scale it to handle 100K requests per second?"

**Your Response**: "Great question. The current implementation has several gaps for that scale. Let me walk through them:

First, **the database**. H2 is file-based, no replication. I'd migrate to PostgreSQL with read replicas—one primary for writes, multiple replicas for reads. The read-heavy endpoints like `GET /devices` would use `@Transactional(readOnly = true)` to route to replicas.

Second, **pagination**. The `findAll()` method loads everything into memory—instant OOM at 43 million records. I'd implement keyset pagination using the `creationTime` as a cursor, which maintains O(1) performance regardless of page depth.

Third, **caching**. I'd add a multi-layer cache: Caffeine for L1 local cache with 60-second TTL, Redis for L2 distributed cache with 5-minute TTL. Cache invalidation on write.

Fourth, **connection pooling**. HikariCP defaults to 10 connections. For 100K req/sec, I'd tune to `(cores * 2) + 1` connections, monitor `hikaricp_connections_pending`, and potentially add PgBouncer for connection multiplexing.

Finally, **rate limiting**. Bucket4j with Redis backend to protect against abuse, with tiered limits per client tier."

---

### Script 2: Handling Production Incidents

**Interviewer**: "It's 3 AM and you get paged. The device API is returning 500 errors. Walk me through your response."

**Your Response**: "First, I'd check the alerting context—which endpoint, error rate, when it started.

Then I'd look at logs, searching by the request IDs our MDC filter adds. I'd look for patterns—are all requests failing or just certain paths? Is it database-related, external service-related?

If it's database, I'd check HikariCP metrics—are connections exhausted? Is there a long-running query? I'd check `pg_stat_activity` for locks.

If it's code-related, I'd check recent deployments. Did we deploy in the last hour? If so, rollback is the fastest mitigation.

For immediate mitigation, I'd consider:
- Increasing replica count if it's load-related
- Enabling circuit breaker for an external dependency
- Failing fast with a graceful error message

After resolution, I'd write a postmortem: timeline, root cause, what we'll do to prevent it. For example, if it was a connection leak, I'd add integration tests that verify connections are released."

---

### Script 3: Code Quality Challenge

**Interviewer**: "I see you used `@Data` on your JPA entity. What's the problem with that?"

**Your Response**: "Good catch—that's actually a potential issue. `@Data` includes `@EqualsAndHashCode` which uses all fields by default.

For JPA entities, this causes problems because:
1. The `id` is null before persist, so `hashCode` changes after save—breaks `HashSet`.
2. Lazy-loaded collections would trigger N+1 queries when `equals` is called.
3. Mutable fields like `name` would change the hash if updated.

The recommended pattern is to override `equals` and `hashCode` manually:
- `equals` should check `id != null && id.equals(other.id)`
- `hashCode` should return `getClass().hashCode()` (constant per entity type)

This means all entities of the same type land in the same hash bucket, which is O(n) lookup, but that's acceptable for entity collections which are typically small.

Alternatively, if there's an immutable business key like `serialNumber`, I'd use that for both methods."

---

### Script 4: System Design Quick Fire

**Interviewer**: "Give me one-sentence answers. Rate limiting?"

**Your Response**: "Bucket4j with Redis backend, token bucket algorithm, tiered limits per client."

**Interviewer**: "Circuit breaker?"

**Your Response**: "Resilience4j with 50% failure threshold, 30-second open state, fallback returns cached or degraded data."

**Interviewer**: "Idempotency?"

**Your Response**: "Client sends `Idempotency-Key` header, server stores request hash and response in Redis for 24 hours, duplicate requests return cached response."

**Interviewer**: "Database migration at scale?"

**Your Response**: "Liquibase with 4-phase zero-downtime: add nullable column, deploy writer code, batch backfill off-peak, add constraint after verification."

**Interviewer**: "Observability?"

**Your Response**: "Prometheus metrics with p50/p95/p99 histograms, MDC for log correlation, OpenTelemetry for distributed tracing, alerts on SLO breach."

---

## Questions to Ask Interviewers

### Technical Questions

1. **Architecture**: "Can you describe your current architecture? Is it monolithic, microservices, or somewhere in between?"

2. **Scale challenges**: "What's been your biggest scaling challenge in the last year, and how did you approach it?"

3. **Tech debt**: "How do you balance new feature development with addressing technical debt?"

4. **On-call**: "What does on-call look like? What's the incident response process?"

5. **Testing**: "What's your testing strategy? Do you use contract testing between services?"

### Team and Culture

1. **Team structure**: "How is the engineering team organized? Will I be working with a specific product team?"

2. **Code review**: "What does the code review process look like?"

3. **Growth**: "What does career growth look like for senior engineers here?"

4. **Decision making**: "How are technical decisions made? Is there an architecture review process?"

### Product and Business

1. **Roadmap**: "What's on the roadmap for the next 6-12 months?"

2. **Customers**: "Who are your main customers, and what problems do they face that your product solves?"

3. **Success metrics**: "How does engineering success get measured?"

### Closing Questions

1. **Concerns**: "Is there anything about my background that gives you pause, that I can address now?"

2. **Next steps**: "What are the next steps in the interview process?"

3. **Timeline**: "When do you expect to make a decision?"

---

## 🔗 Cross-References

| Topic                  | Related Topics                   |
| ---------------------- | -------------------------------- |
| Q25 (PUT/PATCH)        | Q5 (Nulling fields in CORE)      |
| Q26 (OAuth2)           | Q13 (Auth in INFRASTRUCTURE)     |
| Q27 (Bucket4j)         | Q2 (DDoS in CORE)                |
| Q28 (Circuit Breaker)  | Q28 (Same - external services)   |
| Q29 (hashCode/equals)  | Q3 (Race conditions in CORE)     |
| Q30 (Virtual Threads)  | Q21 (HikariCP in INFRASTRUCTURE) |
| Q31 (MongoDB)          | Q6 (H2/SQL vs NoSQL in CORE)     |
| Q32 (p50/p95/p99)      | Q7 (SLIs/SLOs in CORE)           |
| Q33 (MDC)              | Q9 (MDC in CORE)                 |
| Q34 (Contract Testing) | Q11 (Test Strategy in CORE)      |

---

*See also:*
- **NOTES_FAQ_CORE.md** for Questions 1-12
- **NOTES_FAQ_INFRASTRUCTURE.md** for Questions 13-24
- **NOTES_CODE.md** for implementation examples
- **NOTES.md** for the original audit
