# How Vigil works

A walkthrough of every part of the system for someone who knows programming but is new to Java and Spring. Each section explains what the piece does, which Java or Spring concept it uses, and what you should be able to answer about it in an interview.

## The big picture

Vigil is an event pipeline. Data flows one way:

1. A client posts JSON security events to the **ingest-service** over HTTP. Each request is signed with HMAC-SHA256 so the service only accepts events from holders of the shared secret.
2. The ingest service publishes each event to the Kafka topic `events.raw`.
3. The **detection-service** consumes that topic, remembers recent events per source in sliding time windows, and runs every detection rule against each new event. When a rule fires it publishes an `Alert` to the Kafka topic `alerts`.
4. The **alert-api-service** consumes `alerts`, writes each one to PostgreSQL (the system of record) and indexes it into Elasticsearch (for search and Kibana dashboards), and serves a read API.

The services never call each other directly. Kafka sits between them, which is the core microservices trade: you give up the simplicity of one process and gain independent deployment, independent scaling, and the ability for one service to be down without losing data (events wait in the topic).

## Java 21 features used

- **Records** (`SecurityEvent`, `Alert`, the config properties classes): a record is an immutable class where the compiler writes the constructor, accessors, `equals`, `hashCode` and `toString` for you. Immutability matters in a pipeline because an event passed between threads or stages cannot be modified by accident.
- **Enums** (`EventType`, `Severity`): closed sets of values. The detection rules switch off `EventType`, and Postgres stores `Severity` as a string (`@Enumerated(EnumType.STRING)`) so reordering enum constants can never corrupt stored data.
- **Streams** (`filter`, `map`, `distinct`, `toList`): declarative collection processing, used heavily in the rules ("of the recent events from this source, keep the auth failures, collect their ids").
- **Optional**: a rule returns `Optional<Alert>` instead of null. The caller must consciously handle "no alert", so there is no forgetting a null check.
- **Text blocks** (`"""`) in tests for readable inline JSON.

## The build: Maven multi-module with a committed wrapper

The root `pom.xml` is the **parent**: it lists the five modules and inherits from `spring-boot-starter-parent`, which pins the version of every Spring dependency and common plugin. Child modules declare dependencies without version numbers; the parent decides versions, so all services agree on them. `common` is a plain jar the other modules depend on.

The **Maven wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`) is committed so nobody needs Maven installed: the wrapper script downloads the exact Maven version pinned in `maven-wrapper.properties`. The same idea as `gradlew` or a lockfile: builds are reproducible across machines.

Interview answers to have ready: why multi-module (shared model without copy-paste, one versioned build), what the parent pom does (dependency and plugin management), why the wrapper (reproducibility, zero setup).

## Spring core: dependency injection and beans

A **bean** is an object whose lifecycle Spring manages. Annotating a class `@Component` (or `@Service`, `@RestController`, `@Configuration`) makes Spring create one instance at startup and hand it to whoever declares it as a constructor parameter. That is **dependency injection**: classes declare what they need and never construct their dependencies themselves.

Every service class in Vigil uses **constructor injection** (fields are `final`, set in the constructor). This is the recommended style because dependencies are explicit, the object is never in a half-initialized state, and tests can construct the class directly with fakes, no Spring needed. That is exactly how the rule unit tests work.

The clearest payoff is in `DetectionEngine`: its constructor takes `List<DetectionRule>`, and Spring injects every bean implementing that interface. Adding a new rule means adding one annotated class; the engine code does not change. This is the strategy pattern realized through DI, and it is the standard answer to "how would you make detection rules pluggable".

`@SpringBootApplication` on each main class combines `@Configuration`, `@EnableAutoConfiguration` and `@ComponentScan`: scan this package for beans, and configure infrastructure (web server, Kafka, JPA) automatically based on what is on the classpath and in `application.yml`.

## Configuration: application.yml and @ConfigurationProperties

Each service has an `application.yml` holding its port, Kafka settings, and tuning. Values use the `${ENV_VAR:default}` syntax: the default applies for local runs, and Docker Compose overrides via environment variables (for example `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` inside the compose network). This is externalized configuration: one artifact, many environments, no recompile.

Detection thresholds bind onto a typed record:

```java
@ConfigurationProperties(prefix = "vigil.detection")
public record DetectionProperties(Duration suppression, Duration retention, ...)
```

Spring parses `window: 60s` into a `Duration` and fails at startup if a value is missing or malformed, which beats discovering a typo at 3am. `@ConfigurationPropertiesScan` on the application class activates the binding.

## The ingest service: REST plus HMAC

`@RestController` marks a class whose methods handle HTTP requests and return values serialized straight to JSON (Jackson does the serialization). `@PostMapping("/events")` maps the route. The controller accepts either one event or an array by binding the body to a Jackson `JsonNode` and inspecting it, then validates required fields and hands each event to the Kafka publisher. It returns **202 Accepted**: honest semantics for an async pipeline, the event is queued, not fully processed.

The HMAC check lives in a **servlet filter** (`OncePerRequestFilter`), which runs before any controller. Why a filter and not controller code: the signature must be computed over the exact raw request bytes, and rejecting bad requests at the edge keeps unauthenticated payloads away from business logic entirely.

One subtlety worth telling an interviewer: a servlet request body is a stream that can be read once. The filter reads it fully to verify the signature, then wraps the request in a `CachedBodyRequestWrapper` that replays the cached bytes, so the controller can still deserialize the JSON. Verification uses `MessageDigest.isEqual`, a constant time comparison, so attackers cannot learn the signature byte by byte from response timing.

Errors are translated by a `@RestControllerAdvice` class: malformed JSON or missing fields become clean 400 responses instead of stack traces.

## Kafka: topics, producers, consumers

Kafka is a durable, ordered log split into **topics**, each split into **partitions**. Producers append; consumers read at their own pace, tracked by offsets. Two topics here: `events.raw` and `alerts`, both declared as `NewTopic` beans so the broker creates them at startup of a fresh stack.

Producing: `KafkaTemplate.send(topic, key, value)`. The **key** is the event source IP, and Kafka assigns all messages with the same key to the same partition, where order is guaranteed. That is why the detection windows see one source's events in order, and it is the canonical answer to "how do you keep ordering in Kafka": ordering exists per partition, so choose a key that groups what must stay ordered.

Consuming: a method annotated `@KafkaListener(topics = ...)` becomes a managed consumer. Spring polls, deserializes, calls the method, and commits offsets. Each service sets a distinct `group-id`; within a **consumer group**, Kafka spreads partitions across instances, so running two detection-service replicas would automatically split the partitions between them (horizontal scaling with no code change).

Serialization is JSON via Spring Kafka's `JsonSerializer`/`JsonDeserializer`, configured in yml. Two production-minded details: `ErrorHandlingDeserializer` wraps the JSON deserializer so one malformed message (a "poison pill") fails cleanly instead of crash-looping the consumer, and `spring.json.trusted.packages` restricts which classes may be deserialized, closing a known gadget-style vulnerability class. The producer side sets `acks: all` and `enable.idempotence: true` so retries cannot duplicate or reorder messages on the broker.

## The detection engine

`EventWindowStore` keeps a deque of recent events per source (and per username), evicting anything older than the configured retention. Windows are computed from **event timestamps**, not arrival time, so slightly delayed events still count in the right window.

Each rule implements:

```java
public interface DetectionRule {
    String ruleId();
    Optional<Alert> evaluate(SecurityEvent event, EventWindowStore store);
}
```

`BRUTE_FORCE` counts auth failures from one source inside its window; `PORT_SCAN` counts distinct destination ports; `IMPOSSIBLE_TRAVEL` looks for the same account succeeding from two regions (a static IP prefix map stands in for a real GeoIP database, a documented simplification).

The engine adds the event to the store, runs every rule, and **suppresses** repeat alerts: after a rule fires for a source, the same rule and source stay quiet for the suppression interval. Without this, the sixth, seventh, eighth failed login would each raise a duplicate alert, because the window still holds five or more failures. Alert fatigue is a real SOC problem; this is the minimal version of the fix.

State is in memory, which is the documented trade-off: simple and fast for one instance, lost on restart, not shared across replicas. Production alternatives: Redis for shared state, or Kafka Streams, which models windowed aggregation over partitioned data natively.

## The alert API service: two stores, one truth

Postgres and Elasticsearch answer different questions. Postgres is the **system of record**: transactional, exact, relational. Elasticsearch is a **search index**: analyzed text, fast filters and aggregations, feeds Kibana. The consumer writes Postgres first inside a transaction; ES indexing happens after, and an ES failure is logged but not fatal, because losing search convenience is acceptable and losing the record is not.

JPA (via Spring Data JPA and Hibernate) maps `AlertEntity` to the `alerts` table. Things to be able to explain:

- The entity is a separate class from the `Alert` record. Entities are mutable and identity-based because Hibernate tracks them; the record is a pure value crossing service boundaries. Keeping them apart lets schema and wire format evolve independently.
- `@ElementCollection` stores the evidence id list and the context map in side tables (`alert_evidence`, `alert_context`) without making them full entities.
- `AlertRepository` is just an interface extending `JpaRepository`; Spring Data generates the implementation, including SQL, at runtime.
- Dynamic filtering uses the **Specification** API: each request parameter that is present contributes one predicate, combined with `and`. This avoids writing 2^5 derived query methods for every filter combination.
- `/stats` uses JPQL `group by` queries with interface projections, so the database does the counting.
- `ddl-auto: update` lets Hibernate create the schema for the demo; the production answer is versioned migrations with Flyway or Liquibase.
- Spring's `@Transactional` wraps a method in a database transaction: commit on return, roll back on exception.

The ES side is a mirror image: `AlertDocument` annotated `@Document(indexName = "alerts")`, with `Keyword` fields for exact filtering and aggregation and `Text` for the analyzed summary, saved through a Spring Data `ElasticsearchRepository`.

Pagination: the controller takes a `Pageable` (`?page=0&size=20&sort=detectedAt,desc`) and returns `Page<Alert>`; Spring Data translates this to `limit/offset` SQL plus a count query.

## Health checks and startup ordering

Every service exposes `/actuator/health` through Spring Boot Actuator, which aggregates the state of its dependencies (Kafka, the datasource, ES). Docker Compose healthchecks curl that endpoint, and `depends_on: condition: service_healthy` gates startup: the alert API does not start until Kafka, Postgres and Elasticsearch are actually answering, not merely started. Kafka runs in **KRaft mode**, meaning the broker manages its own metadata by Raft consensus and no ZooKeeper is needed.

## Testing strategy

Three layers, and being able to articulate the boundaries matters more than the count:

1. **Unit tests** (surefire, `*Test`): no Spring context, no I/O. Rules are tested by constructing them directly and feeding crafted event sequences that do and do not trip them. The HMAC filter is tested with mock servlet requests (valid, missing, tampered, wrong secret). Controllers are tested with standalone MockMvc and a Mockito-mocked service, which exercises routing, binding and status codes without a server.
2. **Integration tests** (failsafe, `*IT`, run during `mvnw verify`): Testcontainers starts real Kafka, Postgres and Elasticsearch in Docker for the duration of the test. `@ServiceConnection` tells Spring Boot to point its connection settings at the container automatically. `DetectionPipelineIT` proves the Kafka publish-and-consume path end to end; `AlertPersistenceIT` proves an alert on the topic ends up in Postgres, in ES, and out through the REST API. Real infrastructure, no mocks of Kafka or the databases.
3. **The live stack**: `docker compose up` plus the simulator is the final proof that wiring, config and timing all work together.

Why both surefire and failsafe: unit tests must stay fast enough to run on every change; container tests are slower and run at `verify` and in CI.

## CI

`.github/workflows/ci.yml` checks out the repo, installs Temurin JDK 21 with a Maven dependency cache, and runs `./mvnw -ntp verify`. GitHub's Ubuntu runners ship a Docker daemon, which is what Testcontainers needs.

## Docker details worth knowing

The single `Dockerfile` is a **multi-stage build** parameterized by a `MODULE` build arg: stage one builds the fat jar with the JDK and the Maven wrapper, stage two copies just the jar onto a slim JRE image. Poms are copied before sources so the dependency download layer caches and survives source edits. The compose file passes a different `MODULE` per service, so three images come from one Dockerfile with no duplication.

## Interview checklist

You should be able to answer, in your own words:

- Why microservices with Kafka between them rather than one application or direct REST calls between services.
- How a request is authenticated (HMAC over raw bytes, filter placement, replayable body, constant time compare) and what 401 versus 400 versus 202 mean here.
- How Kafka keying gives per-source ordering, what a consumer group is, and how this design scales horizontally.
- How dependency injection assembles the rule engine and why constructor injection is preferred.
- How a sliding window detection works, why event time beats arrival time, why suppression is needed, and what breaks with multiple detection instances (and the fix).
- Why alerts live in both Postgres and Elasticsearch and which one is the source of truth.
- What Spring Data generates for you (JPA repositories, Specifications, ES repositories) and what `@Transactional` does.
- The difference between the unit tests and the Testcontainers tests, and why both exist.
- What externalized configuration buys you and how the same jar runs locally and in compose.
