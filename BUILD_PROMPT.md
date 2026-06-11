# Build Vigil: a Spring Boot threat-detection event pipeline

You are an expert Java and Spring Boot engineer. Build the project described below from scratch in this repository, in phases, verifying each phase actually runs before moving on. Teach me the Java and Spring Boot concepts as you go, because I am new to Java and must be able to defend every part of this in a technical interview.

## Who I am and why this exists

I am William White, a software engineer (Florida State University, Computer Science, 2025) applying for an Associate Software Engineer role at ReliaQuest, a security company whose platform automates threat detection and response. The role requires Java, Spring Boot, and a microservices architecture, and lists Kafka, Elasticsearch, and Kibana as preferred technologies. My production experience is in TypeScript and Python, so I am building one genuine, interview-defensible Java project that covers their core stack. I am new to Java and Spring Boot. As you build, explain what each piece does. Do not produce code I cannot explain.

## The goal

Build a small but real security-telemetry ingestion and detection pipeline as a set of Spring Boot microservices, wired together with Kafka, PostgreSQL, and Elasticsearch, runnable end to end with one `docker compose up`. It must genuinely run and genuinely detect simple attack patterns from a stream of events. No mocked stand-ins for the real infrastructure.

## Hard constraints

- Java 21 (LTS). Spring Boot 3, latest stable. Maven, multi-module, with the Maven wrapper (`mvnw`) committed so I do not need Maven installed globally.
- Real Apache Kafka (KRaft mode, single broker is fine), real PostgreSQL, real Elasticsearch 8, real Kibana 8. All via Docker Compose.
- Three Spring Boot services plus a shared `common` module and a dev-only event simulator (described below).
- It must actually boot and work. Definition of done is a clean `docker compose up`, then running the simulator makes real alerts appear through the REST API and in Elasticsearch and Kibana within seconds.
- JUnit 5 tests. Each detection rule gets focused unit tests. Add at least two Testcontainers integration tests covering the Kafka publish-and-consume path and the persistence path.
- A GitHub Actions workflow that runs the tests on push.
- Clean, idiomatic Spring code. Comment at the density a competent Spring developer would use, no more.
- No em dashes anywhere in code, comments, docs, or commit messages. Use commas, colons, or parentheses instead.
- Do not invent benchmark numbers or fake metrics in the README. Real run output only.
- I am on Windows with Docker Desktop. Keep all tooling cross-platform or PowerShell friendly. Prefer a small Java event simulator over shell scripts so it runs anywhere.

## Architecture

```
  event-simulator (dev tool)
        |  POST /events  (HMAC-SHA256 signed)
        v
  [ ingest-service ]   Spring Boot REST API
        |  validates HMAC, publishes to Kafka topic "events.raw"
        v
   ( Kafka topic: events.raw )
        |
        v
  [ detection-service ]   Spring Boot, Kafka consumer
        |  runs windowed DetectionRule set, publishes to Kafka topic "alerts"
        v
   ( Kafka topic: alerts )
        |
        v
  [ alert-api-service ]   Spring Boot, Kafka consumer + REST API
        |  persists alerts to Postgres, indexes them into Elasticsearch
        |  exposes GET /alerts, GET /alerts/{id}, GET /stats
        v
   Postgres (system of record)   +   Elasticsearch (search/aggregation)   +   Kibana (dashboard)
```

Two Kafka topics: `events.raw` and `alerts`.

### Services

1. **ingest-service**: REST API. `POST /events` accepts a single security event or a batch as JSON. Every request must carry an `X-Signature` header that is an HMAC-SHA256 of the raw request body using a shared secret from configuration. Reject with 401 if the signature is missing or invalid. On success, publish each event to the `events.raw` Kafka topic and return 202. Also expose a health endpoint.
2. **detection-service**: No REST surface needed beyond health. Consumes `events.raw`. Maintains short time windows per source and evaluates a set of detection rules. When a rule fires, publish an `Alert` to the `alerts` topic. Keep windowing in memory for this project (a per-source sliding window using timestamps); document this choice and note Redis or Kafka Streams as the production alternative.
3. **alert-api-service**: Consumes `alerts`. Persists each alert to PostgreSQL (system of record) and indexes it into Elasticsearch (for search and aggregation). Exposes a read REST API: `GET /alerts` with filters (severity, source, ruleId, from, to) and pagination, `GET /alerts/{id}`, and `GET /stats` returning counts grouped by severity and by rule.

### Shared module

- **common**: shared model classes (`SecurityEvent`, `Alert`, the severity enum, event-type enum), the Kafka topic name constants, and the HMAC signer/verifier utility used by both the simulator and the ingest-service. No business logic that belongs to a single service.

### Dev tool

- **event-simulator**: a small standalone Java program (a Spring Boot `CommandLineRunner` is fine) that synthesizes a realistic stream of benign security events and injects two clear attack patterns into it: a brute-force burst (many failed logins from one source IP against one username in a few seconds) and a port-scan burst (one source IP touching many distinct destination ports quickly). It signs each request with the shared HMAC secret and posts to the ingest-service. This is a developer tool, not one of the three services, and the README must describe it that way.

## Data model

```
SecurityEvent {
  String id;                  // UUID, generated if absent
  Instant timestamp;          // event time
  String source;              // source IP or host
  String username;            // nullable
  EventType eventType;        // AUTH_SUCCESS, AUTH_FAILURE, NETWORK_CONNECTION, PROCESS_START, ...
  String destination;         // target host or resource, nullable
  Integer destinationPort;    // nullable
  String rawMessage;          // original log line, nullable
  Map<String,String> metadata;// open key/value bag, nullable
}

Alert {
  String id;                  // UUID
  Instant detectedAt;
  String ruleId;              // e.g. BRUTE_FORCE, PORT_SCAN
  Severity severity;          // LOW, MEDIUM, HIGH, CRITICAL
  String source;              // the offending source
  String summary;             // human-readable one line
  List<String> evidenceEventIds;
  Map<String,String> context; // rule-specific details (counts, window, ports, etc.)
}
```

## Detection rules

Define a `DetectionRule` interface so rules are pluggable and each is unit tested in isolation. Implement at minimum:

1. **Brute force** (`BRUTE_FORCE`, HIGH): 5 or more `AUTH_FAILURE` events from the same source within a 60 second window. Evidence is the failing event ids; context includes the count and window.
2. **Port scan** (`PORT_SCAN`, MEDIUM): the same source produces `NETWORK_CONNECTION` events to 15 or more distinct destination ports within a 30 second window.

Stretch rules if time allows, each behind the same interface and each unit tested:

3. **Impossible travel** (`IMPOSSIBLE_TRAVEL`, HIGH): the same username has `AUTH_SUCCESS` from two source IPs that map to different regions within 5 minutes. A small static IP-to-region map is acceptable for the demo; document that this is a simplification.
4. **Volume spike** (`VOLUME_SPIKE`, LOW): a source's event rate in the current short window exceeds a configured multiple of its recent baseline.

Make thresholds and window sizes configuration values in `application.yml`, not hardcoded constants, so they can be tuned and so I can explain externalized configuration.

## Project structure

```
vigil/
  pom.xml                      // parent, dependency management, module list
  mvnw, mvnw.cmd, .mvn/        // committed Maven wrapper
  common/
  ingest-service/
  detection-service/
  alert-api-service/
  tools/event-simulator/
  docker-compose.yml           // kafka, postgres, elasticsearch, kibana, and the 3 services
  .github/workflows/ci.yml
  .gitignore
  README.md
  HOW-IT-WORKS.md
```

## Teach me as you go

Because I must defend this in interviews, after each phase explain in plain language: which Spring Boot concepts you used and why (dependency injection and beans, `@RestController` and request mapping, `@KafkaListener` and producers, Spring Data JPA repositories, Spring Data Elasticsearch, `application.yml` and profiles, configuration properties, exception handling). Tell me what I should be able to answer about each. Collect a distilled version of all of this in `HOW-IT-WORKS.md`, written for someone who knows programming but is new to Java and Spring.

## Build in phases, verify each one before moving on

- **Phase 0**: Confirm prerequisites. Print the installed JDK version (must be 21) and confirm Docker is running. Scaffold the parent `pom.xml`, all modules, and the committed Maven wrapper. Verify an empty `./mvnw -q -version` and a clean build succeed.
- **Phase 1**: Build the `common` module: the model classes, enums, topic constants, and the HMAC signer/verifier with a unit test proving sign-then-verify round-trips and that tampering fails.
- **Phase 2**: Build `ingest-service`: the `POST /events` endpoint with HMAC verification and the Kafka producer. Unit test the signature filter (valid, missing, tampered).
- **Phase 3**: Build `detection-service`: the `events.raw` consumer, the `DetectionRule` interface, and the brute-force and port-scan rules with windowing, publishing to `alerts`. Unit test each rule with crafted event sequences that do and do not trip it.
- **Phase 4**: Build `alert-api-service`: the `alerts` consumer, Postgres persistence via Spring Data JPA, Elasticsearch indexing, and the read endpoints (`GET /alerts` with filters and paging, `GET /alerts/{id}`, `GET /stats`).
- **Phase 5**: Write `docker-compose.yml` for the full stack (Kafka in KRaft mode, Postgres, Elasticsearch, Kibana, and the three services). Bring it up. Fix wiring, health checks, and startup ordering until every container is healthy.
- **Phase 6**: Build the `event-simulator`. Run it against the live stack and confirm brute-force and port-scan alerts appear through `GET /alerts` and in Elasticsearch within a few seconds. Show me the actual output.
- **Phase 7**: Add the Testcontainers integration tests, the GitHub Actions CI workflow, the README (with the architecture diagram, a quickstart, and the job-requirement mapping table below), the `HOW-IT-WORKS.md`, and a Kibana data view for the alerts index. Run `./mvnw verify` and a final `docker compose up` and show both clean.

At each phase: run the build and tests and show me the real output. After each green phase, make a focused git commit with a clear message. Initialize git at Phase 0. Do not push or create a GitHub remote; I will do that myself.

## README must include a job-mapping table

Include a table that maps features to the ReliaQuest requirements, so a reviewer sees the fit immediately:

| Requirement | Where it lives in Vigil |
| --- | --- |
| Java, Spring Boot, microservices | three Spring Boot services in a Maven multi-module build |
| REST APIs and integrations | ingest POST API, alert read API, Kafka integration between services |
| Kafka | events.raw and alerts topics, producers and consumers |
| Elasticsearch and Kibana | alert indexing and a Kibana dashboard |
| Unit testing and CI | JUnit and Testcontainers tests, GitHub Actions |
| Cloud and containers | Docker Compose for the full stack |

## Definition of done

- `docker compose up` brings the whole stack healthy.
- Running the simulator produces brute-force and port-scan alerts visible through `GET /alerts` and in Kibana within a few seconds.
- `./mvnw verify` is green. The CI workflow is green.
- README explains the system, shows the architecture diagram, gives a copy-paste quickstart, and includes the requirement-mapping table.
- `HOW-IT-WORKS.md` gives me enough to explain every part of the system in an interview.
- No em dashes anywhere. No fabricated metrics.

## Start

First confirm JDK 21 and Docker are available on this machine and tell me the exact versions. Then begin Phase 0. Ask me a question only if you are genuinely blocked; otherwise proceed with sensible defaults and keep moving.
