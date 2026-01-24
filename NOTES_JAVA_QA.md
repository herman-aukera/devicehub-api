# DeviceHub API - Top 50 Java & Spring Interview Questions

**Purpose**: Rapid-fire technical trivia defense.
**Usage**: Review this 1 hour before the interview. These are "knowledge check" questions often asked to filter candidates.

---

## ☕ Core Java (21+)

### 1. `==` vs `.equals()`?
- **`==`**: Compares memory addresses (references). Returns true only if both point to the EXACT same object instance.
- **`.equals()`**: Method defined in `Object`. Default implementation is `==`, but usually overridden (like in `String`, `Integer`) to compare **content/values**.
- **Gotcha**: `new String("a") == new String("a")` is false. `"a".equals("a")` is true.

### 2. Why is String immutable?
- **Security**: Strings store secrets (db connection, passwords). If mutable, a reference could change the value after validation.
- **Thread Safety**: Immutable objects are automatically thread-safe.
- **String Pool**: Java saves memory by reusing identical string literals. If mutable, changing one would change all.
- **HashCode Caching**: String hash codes are cached. If mutable, hash would need re-calculation every time.

### 3. `final`, `finally`, `finalize()`?
- **`final`**: Keyword. Variable = constant. Method = can't override. Class = can't extend.
- **`finally`**: Block after `try-catch`. Always executes (cleanup resources). Only skipped if JVM exits (`System.exit`).
- **`finalize()`**: Method called by GC before reclaiming memory. **Deprecated since Java 9**. Use `AutoCloseable` instead.

### 4. Checked vs Unchecked Exceptions?
- **Checked (`Exception`)**: Compile-time enforcement. Representative of external failures (Network, File). Must `try-catch` or `throws`.
- **Unchecked (`RuntimeException`)**: Runtime. Representative of programming errors (NullPointer, IndexOutOfBounds). No enforcement.

### 5. `HashMap` vs `ConcurrentHashMap`?
- **`HashMap`**: Not thread-safe. Fast. Allows 1 null key.
- **`ConcurrentHashMap`**: Thread-safe. Uses bucket-level locking (CAS + synchronized) instead of locking whole map. No null keys/values allowed.

### 6. `ArrayList` vs `LinkedList`?
- **`ArrayList`**: Backed by array. Fast random access `get(i)` is O(1). Slow insert/delete in middle O(n) (shifting).
- **`LinkedList`**: Doubly-linked list. Slow access `get(i)` is O(n). Fast insert/delete O(1) if you have the node.
- **Reality**: Always use `ArrayList`. CPU cache locality makes it faster in 99% of cases.

### 7. Interface vs Abstract Class?
- **Interface**: "Can do" contract. Multiple inheritance. All methods public. Variables are `static final`.
- **Abstract Class**: "Is a" relationship. Single inheritance. Can have state (fields) and non-public methods.
- **Modern Java**: Interfaces can now have `default` and `private` methods, blurring the line.

### 8. What is the contract between `hashCode` and `equals`?
1. If `a.equals(b)` is true, `a.hashCode()` MUST equal `b.hashCode()`.
2. If hashCodes are equal, objects MIGHT be equal (collision).
3. Used by HashMap: Hash finds the bucket, equals finds the object in that bucket.
- **Breakage**: If you override equals but not hashCode, HashMap won't find your object.

### 9. Java Memory Model (Stack vs Heap)?
- **Stack**: Stores method frames, primitives, and **references** to objects. Thread-local. Short-lived.
- **Heap**: Stores **Actual Objects**. Shared by all threads. Garage collected.
- **Metaspace**: Stores class metadata (static variables, method code).

### 10. Record vs Class (Java 14+)?
- **`record`**: Immutable data carrier. Auto-generates `constructor`, `accessors` (name(), not getName()), `equals`, `hashCode`, `toString`. Cannot extend classes. Final by default.
- **Use case**: DTOs, map keys.

---

## 🧵 Concurrency & Threads

### 11. `synchronized` vs `ReentrantLock`?
- **`synchronized`**: Implicit monitor lock. Block-scoped. Unfair. Handled by JVM.
- **`ReentrantLock`**: Explicit object. Can `tryLock()` (timeout). Fair locking option. Must `unlock()` in `finally`.
- **Recommendation**: Synchronized is simpler; use ReentrantLock for complex needs (fairness, timeouts).

### 12. `volatile` keyword?
- Ensures **visibility**. Writes to volatile flush to main memory immediately; reads skip CPU cache.
- Prevents instruction reordering.
- **Does NOT** guarantee atomicity (e.g., `count++` is not safe with just volatile).

### 13. `ThreadLocal`?
- Variable that provides a separate copy for each thread.
- **Use case**: Database transactions, Request context (MDC uses this).
- **Danger**: Memory leaks in thread pools if not cleaned up (`.remove()`).

### 14. `CompletableFuture`?
- Asynchronous programming (Promises).
- Non-blocking.
- Chainable (`.thenApply`, `.thenCompose`).
- Uses `ForkJoinPool.commonPool()` by default.

### 15. Virtual Threads (Java 21)?
- Lightweight threads managed by JVM, not OS.
- Map M virtual threads to N OS threads (Carrier threads).
- **Benefit**: "Thread-per-request" style becomes scalable again ($1M$ threads possible).
- **Blocking**: When VT blocks (I/O), JVM unmounts it; OS thread processes others.

### 16. What is a Deadlock?
- Two threads waiting for resources held by each other forever.
- **Detection**: Thread dump.
- **Prevention**: Acquire locks in consistent order. Use `tryLock` with timeout.

### 17. `Atomic` classes (`AtomicInteger`)?
- Thread-safe wrapper.
- Uses **CAS** (Compare-And-Swap) hardware instruction. Lock-free optimization.
- Better performance than `synchronized` for simple counters.

### 18. `ExecutorService` types?
- `FixedThreadPool(n)`: Fixed number of threads. Queue is unbounded.
- `CachedThreadPool()`: Creates threads as needed. Kills idle. Dangerous (unbounded threads).
- `SingleThreadExecutor()`: One thread (sequential tasks).
- `VirtualThreadPerTaskExecutor()`: New in Java 21!

### 19. Race Condition?
- System behavior depends on sequence/timing of uncontrollable events.
- **Check-Then-Act**: `if (!map.containsKey(key)) map.put(key, val)`. Not atomic!
- **Fix**: `map.putIfAbsent(key, val)`.

### 20. CountDownLatch vs CyclicBarrier?
- **Latch**: Waits for N events (countdown). One-time use.
- **Barrier**: Waits for N threads to meet at a point. Reusable.

---

## 🍃 Spring Framework Core

### 21. What is IoC / Dependency Injection?
- **IoC (Inversion of Control)**: Framework calls you vs you calling library. Object lifecycle managed by container.
- **DI**: Objects define dependencies (constructor args); container provides them. Removes hardcoded `new Service()`.

### 22. Bean Scopes?
- **Singleton** (Default): One instance per context. Stateless.
- **Prototype**: New instance every request.
- **Request**: New instance per HTTP request.
- **Session**: New instance per HTTP session.

### 23. `@Autowired` vs Constructor Injection?
- **Field Injection** (`@Autowired private Svc s`): Evil. Hard to test (reflection needed). Hides dependencies.
- **Constructor Injection** (`final Svc s; public C(Svc s)`): Recommended. Immutable. Easy to test (pass mocks). Explicit dependencies.

### 24. `@Component` vs `@Bean`?
- **`@Component`**: Class-level. "Scan this class". Auto-detection.
- **`@Bean`**: Method-level (in `@Configuration`). "Take return value and manage it". Use for external libraries (e.g., configuring `ObjectMapper`).

### 25. Spring Profile?
- Logic to segregate configuration parts.
- `application-dev.properties` vs `application-prod.properties`.
- Activate: `-Dspring.profiles.active=prod`.

### 26. `@Transactional`?
- Declarative transaction management.
- Uses AOP proxy.
- **Default**: Rollback on RuntimeException, Commit on CheckedException.
- **Propagation**: `REQUIRED` (Join existing or create new), `REQUIRES_NEW` (Suspend existing, create new).

### 27. Filter vs Interceptor?
- **Filter**: Servlet standard (Tomcat). Runs before Spring DispatcherServlet. Good for security, compression, logging bodies.
- **Interceptor**: Spring MVC. Has access to Handler (Controller). Good for authorization checks, adding model attributes.

### 28. What is AOP?
- **Aspect Oriented Programming**. Separates cross-cutting concerns (Logging, Tx, Security) from business logic.
- **Advice**: Code to run (Before, After).
- **Pointcut**: Where to run it (`execution(* com.service.*(..))`).

### 29. Circular Dependencies?
- A needs B, B needs A.
- **Fix**: Refactor (extract C). Use `@Lazy`. Use Setter injection (bad).

### 30. ApplicationContext vs BeanFactory?
- **BeanFactory**: Lazy loading. Basic DI.
- **ApplicationContext**: Eager loading. Enterprise features (Events, I18n, AOP). Always use this.

---

## 🚀 Spring Boot

### 31. Why Spring Boot?
- **Auto-configuration**: Looks at classpath ("Oh, H2 is here? I'll configure a datasource").
- **Starter POMs**: `spring-boot-starter-web` bundles Tomcat, Jackson, MVC.
- **Embedded Server**: `java -jar` runs Tomcat inside. No separate WebServer install.

### 32. `@SpringBootApplication`?
- Meta-annotation. Combines:
  1. `@Configuration`
  2. `@EnableAutoConfiguration`
  3. `@ComponentScan`

### 33. Spring Boot Actuator?
- Production-ready features.
- Endpoints: `/health`, `/metrics`, `/info`, `/env`, `/threaddump`.
- Security risk if exposed publicly!

### 34. `application.properties` vs `.yml`?
- **Properties**: Standard Java. Flat.
- **YAML**: Hierarchical. Better readability.
- Both work. Don't mix them.

### 35. CommandLineRunner / ApplicationRunner?
- Interfaces with `run()` method.
- Executed AFTER context is loaded, BEFORE app starts accepting traffic.
- Use for cache warming, initial data loading.

### 36. How to reload config without restart?
- `@RefreshScope` (Spring Cloud).
- Actuator `/refresh` endpoint.

---

## 💾 Spring Data JPA / Hibernate

### 37. JPA vs Hibernate vs Spring Data?
- **JPA**: Standard Specification (Interface).
- **Hibernate**: Implementation (Vendor).
- **Spring Data JPA**: Abstraction layer (Repositories) top of JPA. Reduces boilerplate.

### 38. N+1 Problem?
- Fetching List of N Parents. Then loop and fetch Child for each parent. = 1 + N queries.
- **Fix**: `JOIN FETCH` (JPQL), `@EntityGraph`, or Batch Fetching.

### 39. Lazy vs Eager Loading?
- **Lazy**: Fetch association only when accessed. (Default for `@OneToMany`).
- **Eager**: Fetch immediately with parent. (Default for `@ManyToOne`).
- **Best Practice**: Always Lazy. Use Fetch Join when needed.

### 40. `OpenSessionInView` (OSIV)?
- Keeps DB session open during View rendering (Controller/Template).
- **Pro**: Prevents `LazyInitializationException` in View.
- **Con**: Long DB connection hold time. Performance killer.
- **Advice**: Disable `spring.jpa.open-in-view=false`.

### 41. First Level vs Second Level Cache?
- **L1**: Session/Transaction scope. Default. Mandatory.
- **L2**: SessionFactory/App scope. Optional (Redis/EhCache). Shared across transactions.

### 42. Optimistic vs Pessimistic Locking?
- **Optimistic**: `@Version` column. Throws exception on save conflict. High concurrency.
- **Pessimistic**: `SELECT ... FOR UPDATE` (DB row lock). Serialized access. Lower concurrency.

### 43. `@Query` vs Method Names?
- **Method Name**: `findByNameAndStatus(..)` - nice for simple stuff.
- **`@Query`**: JPQL/SQL. Use for complex joins or optimization. Prevents generated queries from getting too crazy.

---

## 🏗️ System Design (Java Context)

### 44. Stateful vs Stateless?
- **Stateless**: Server keeps no client info. Scale horizontally easily. (JWT).
- **Stateful**: Server keeps session (Sticky Session needed). Harder to scale.

### 45. CAP Theorem?
- Choose 2: **Consistency**, **Availability**, **Partition Tolerance**.
- Distributed system implies **P**. So choose **C** (CP - mongo/postgres) or **A** (AP - cassandra/dynamo).

### 46. Database Normalization?
- **Normalization**: Reduce redundancy (SQL). Good for writes/consistency.
- **Denormalization**: Duplicate data (NoSQL/Warehousing). Good for read performance.

### 47. Circuit Breaker Pattern?
- Prevent cascading failures.
- States: **Closed** (Flowing), **Open** (Broken - Fail Fast), **Half-Open** (Testing recovery).
- Tools: Resilience4j.

### 48. Distributed Transactions (Saga)?
- Monolith = ACID transaction.
- Microservices = 2PC (Two Phase Commit - slow) or **Saga** (Sequence of local txn + compensation events).

### 49. Caching Strategies?
- **Write-Through**: Write DB + Cache same time. Consistent. Slow write.
- **Write-Back**: Write Cache, async to DB. Fast. Risk of data loss.
- **Cache-Aside**: App checks cache, if miss, read DB & put cache. standard.

### 50. Microservices vs Monolith?
- **Monolith**: Simple deploy, easy debug, no network latency. Hard to scale parts. One tech stack.
- **Microservices**: Independent scaling/deploy, polyglot. Complex ops, eventual consistency, network latency.
- **Advice**: Start Monolith. Modulith. Microservices only when org structure or scale requires it.
