package io.vigil.alertapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vigil.alertapi.model.AlertDocument;
import io.vigil.alertapi.repo.AlertRepository;
import io.vigil.alertapi.service.AlertQueryService;
import io.vigil.common.kafka.Topics;
import io.vigil.common.model.Alert;
import io.vigil.common.model.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.ResponseEntity;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test of the persistence path against real infrastructure:
 * a real Kafka broker, a real Postgres, and a real Elasticsearch, all started
 * by Testcontainers. An alert published to the alerts topic must end up
 * queryable in Postgres through the REST API and indexed in Elasticsearch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AlertPersistenceIT {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(DockerImageName
                    .parse("docker.elastic.co/elasticsearch/elasticsearch:8.17.0"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");

    @Autowired
    private AlertRepository repository;

    @Autowired
    private AlertQueryService queryService;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void alertOnTheTopicIsPersistedIndexedAndServedByTheApi() throws Exception {
        Alert alert = new Alert(
                "it-alert-1",
                Instant.parse("2026-06-11T12:00:00Z"),
                "BRUTE_FORCE",
                Severity.HIGH,
                "10.0.0.200",
                "5 failed logins from 10.0.0.200 within 60 seconds",
                List.of("e1", "e2", "e3", "e4", "e5"),
                Map.of("failureCount", "5"));

        // produce the alert as JSON the same way the detection service does
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(alert);
        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(Topics.ALERTS, alert.source(), json)).get();
        }

        // wait for the consumer to pick it up and write Postgres
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(repository.findById("it-alert-1")).isPresent());

        // system of record has every field; the query service maps the entity
        // back to the record inside a transaction, collections included
        Alert stored = queryService.findById("it-alert-1").orElseThrow();
        assertThat(stored.ruleId()).isEqualTo("BRUTE_FORCE");
        assertThat(stored.severity()).isEqualTo(Severity.HIGH);
        assertThat(stored.evidenceEventIds()).hasSize(5);

        // search index has the document
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            AlertDocument document = elasticsearchOperations.get("it-alert-1", AlertDocument.class);
            assertThat(document).isNotNull();
            assertThat(document.severity()).isEqualTo("HIGH");
        });

        // and the REST API serves it with filters applied
        ResponseEntity<String> response = restTemplate
                .getForEntity("/alerts?severity=HIGH&ruleId=BRUTE_FORCE", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("it-alert-1");

        ResponseEntity<String> stats = restTemplate.getForEntity("/stats", String.class);
        assertThat(stats.getBody()).contains("\"BRUTE_FORCE\":1");
    }
}
