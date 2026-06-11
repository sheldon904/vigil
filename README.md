# Vigil

A security telemetry ingestion and threat detection pipeline built as Spring Boot microservices, wired together with Kafka, PostgreSQL, Elasticsearch and Kibana. Events stream in over a signed REST API, detection rules evaluate them over sliding time windows, and alerts land in Postgres (system of record) and Elasticsearch (search and dashboards) within seconds.

## Architecture

```
  event-simulator (dev tool)
        |  POST /events  (HMAC-SHA256 signed)
        v
  [ ingest-service ]   Spring Boot REST API, port 8081
        |  validates HMAC, publishes to Kafka topic "events.raw"
        v
   ( Kafka topic: events.raw )
        |
        v
  [ detection-service ]   Spring Boot Kafka consumer, port 8082
        |  runs windowed DetectionRule set, publishes to Kafka topic "alerts"
        v
   ( Kafka topic: alerts )
        |
        v
  [ alert-api-service ]   Spring Boot Kafka consumer + REST API, port 8083
        |  persists alerts to Postgres, indexes them into Elasticsearch
        |  exposes GET /alerts, GET /alerts/{id}, GET /stats
        v
   Postgres (system of record)   +   Elasticsearch (search)   +   Kibana (dashboard)
```

## Quickstart

Prerequisites: Docker Desktop (or any Docker engine with Compose) and JDK 21 to run the simulator. The Maven wrapper is committed, no Maven install needed.

```bash
# 1. bring up the whole stack (first build takes a few minutes)
docker compose up -d --build

# 2. wait until every container reports healthy
docker compose ps

# 3. stream simulated events (benign noise plus three attack bursts)
./mvnw -q -pl tools/event-simulator -am package -DskipTests
java -jar tools/event-simulator/target/event-simulator-0.1.0-SNAPSHOT.jar

# 4. read the alerts
curl http://localhost:8083/alerts
curl http://localhost:8083/stats
```

Real output from a run of step 3 and 4 on this machine:

```
INFO io.vigil.simulator.SimulatorRunner : posting to http://localhost:8081/events (60 benign events plus 3 attack bursts)
INFO io.vigil.simulator.SimulatorRunner : injected brute force burst: 8 AUTH_FAILURE from 10.0.0.66 against admin
INFO io.vigil.simulator.SimulatorRunner : injected port scan burst: 20 distinct ports from 172.16.0.99
INFO io.vigil.simulator.SimulatorRunner : injected impossible travel: carol from 10.0.0.42 then 192.168.1.99
INFO io.vigil.simulator.SimulatorRunner : done: 90 events sent
```

```json
// GET /stats
{
  "total": 3,
  "bySeverity": { "MEDIUM": 1, "HIGH": 2 },
  "byRule": { "IMPOSSIBLE_TRAVEL": 1, "BRUTE_FORCE": 1, "PORT_SCAN": 1 }
}
```

## Services

| Service | Port | Role |
| --- | --- | --- |
| ingest-service | 8081 | `POST /events` accepts a single JSON event or a batch. Every request must carry an `X-Signature` header: the HMAC-SHA256 of the raw body using the shared secret. Invalid or missing signatures get 401. Valid events are published to `events.raw`, keyed by source. |
| detection-service | 8082 | Consumes `events.raw`, keeps per-source and per-username sliding windows in memory, runs every `DetectionRule` bean, publishes alerts to `alerts`. Duplicate alerts for the same rule and source are suppressed for a configurable interval. |
| alert-api-service | 8083 | Consumes `alerts`, writes Postgres first (system of record) then indexes into Elasticsearch. Serves `GET /alerts` (filters: `severity`, `source`, `ruleId`, `from`, `to`; paging: `page`, `size`), `GET /alerts/{id}`, `GET /stats`. |

The **event-simulator** under `tools/` is a development tool, not one of the services. It synthesizes benign traffic, injects attack bursts, signs each request with the shared HMAC secret, and posts to the ingest API.

## Detection rules

| Rule | Severity | Fires when |
| --- | --- | --- |
| `BRUTE_FORCE` | HIGH | 5 or more `AUTH_FAILURE` events from one source within 60 seconds |
| `PORT_SCAN` | MEDIUM | one source touches 15 or more distinct destination ports within 30 seconds |
| `IMPOSSIBLE_TRAVEL` | HIGH | one account has `AUTH_SUCCESS` from two source IPs mapped to different regions within 5 minutes (static IP prefix to region map, a documented demo simplification) |

Thresholds and windows are configuration values in `detection-service/src/main/resources/application.yml` under `vigil.detection`, not hardcoded constants.

Windowing is in memory inside the detection service: simple and fast for a single instance, but state is lost on restart and cannot be shared across instances. The production alternatives are Redis for shared window state or Kafka Streams windowed aggregations.

## Configuration

| Variable | Default | Used by |
| --- | --- | --- |
| `VIGIL_HMAC_SECRET` | `dev-secret-change-me` | ingest-service, event-simulator |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` (compose sets `kafka:9092`) | all services |
| `POSTGRES_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `jdbc:postgresql://localhost:5432/vigil` / `vigil` / `vigil` | alert-api-service |
| `ELASTICSEARCH_URIS` | `http://localhost:9200` | alert-api-service |
| `INGEST_URL` | `http://localhost:8081/events` | event-simulator |

Host port mappings from compose: ingest 8081, detection 8082, alert API 8083, Kafka external listener 9094, Postgres 5435 (5435 to avoid clashing with local Postgres instances), Elasticsearch 9200, Kibana 5601.

## Kibana

Kibana runs at <http://localhost:5601>. Create the data view once (or use the one created during development):

```bash
curl -X POST http://localhost:5601/api/data_views/data_view \
  -H "kbn-xsrf: true" -H "Content-Type: application/json" \
  -d '{"data_view":{"title":"alerts","name":"Vigil Alerts","timeFieldName":"detectedAt"}}'
```

Then open Analytics, Discover, pick "Vigil Alerts", and filter or aggregate on `ruleId`, `severity` and `source`.

## Testing

```bash
# unit tests only (fast, no Docker needed)
./mvnw test

# unit tests plus Testcontainers integration tests (needs Docker)
./mvnw verify
```

Unit tests cover the HMAC signer, the signature filter (valid, missing, tampered, wrong secret), the ingest controller, every detection rule with event sequences that do and do not trip it, alert suppression, mapping, and the read API.

Two integration tests run against real infrastructure started by Testcontainers:

- `DetectionPipelineIT` (detection-service): publishes a brute force burst to `events.raw` on a real Kafka broker and asserts a `BRUTE_FORCE` alert appears on the `alerts` topic.
- `AlertPersistenceIT` (alert-api-service): publishes an alert to `alerts` with real Kafka, Postgres and Elasticsearch containers, then asserts it is persisted, indexed, and served by `GET /alerts` and `GET /stats`.

CI runs `./mvnw verify` on every push via GitHub Actions (`.github/workflows/ci.yml`).

## Fit for the ReliaQuest Associate Software Engineer role

| Requirement | Where it lives in Vigil |
| --- | --- |
| Java, Spring Boot, microservices | three Spring Boot services in a Maven multi-module build |
| REST APIs and integrations | ingest POST API, alert read API, Kafka integration between services |
| Kafka | events.raw and alerts topics, producers and consumers |
| Elasticsearch and Kibana | alert indexing and a Kibana data view over the alerts index |
| Unit testing and CI | JUnit 5 and Testcontainers tests, GitHub Actions |
| Cloud and containers | Docker Compose for the full stack |

## Project structure

```
vigil/
  pom.xml                      parent: dependency management, module list
  mvnw, mvnw.cmd, .mvn/        committed Maven wrapper
  common/                      shared models, topic constants, HMAC utility
  ingest-service/              signed REST ingestion, Kafka producer
  detection-service/           rules engine over sliding windows
  alert-api-service/           persistence, indexing, read API
  tools/event-simulator/       dev tool: signed synthetic event stream
  Dockerfile                   parameterized multi-stage build for the services
  docker-compose.yml           kafka, postgres, elasticsearch, kibana, services
  .github/workflows/ci.yml     build and test on push
  HOW-IT-WORKS.md              concept walkthrough for the whole system
```

See [HOW-IT-WORKS.md](HOW-IT-WORKS.md) for a plain-language walkthrough of every piece: the Spring concepts used, why each exists, and the production trade-offs.
