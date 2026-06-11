package io.vigil.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vigil.common.kafka.Topics;
import io.vigil.common.model.Alert;
import io.vigil.common.model.EventType;
import io.vigil.common.model.SecurityEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test of the Kafka publish-and-consume path against a real
 * broker. Testcontainers starts a throwaway Kafka in Docker; ServiceConnection
 * points the application's producers and consumers at it. We publish a brute
 * force burst to events.raw and assert a BRUTE_FORCE alert comes out on the
 * alerts topic.
 */
@SpringBootTest
@Testcontainers
class DetectionPipelineIT {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Autowired
    private KafkaTemplate<String, SecurityEvent> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void bruteForceBurstOnEventsRawProducesAnAlertOnAlertsTopic() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 6; i++) {
            SecurityEvent event = new SecurityEvent(UUID.randomUUID().toString(),
                    now.plusSeconds(i), "10.0.0.200", "admin", EventType.AUTH_FAILURE,
                    null, null, null, null);
            kafkaTemplate.send(Topics.EVENTS_RAW, event.source(), event).get();
        }

        List<Alert> alerts = consumeAlerts(Duration.ofSeconds(30));

        assertThat(alerts).isNotEmpty();
        Alert alert = alerts.get(0);
        assertThat(alert.ruleId()).isEqualTo("BRUTE_FORCE");
        assertThat(alert.source()).isEqualTo("10.0.0.200");
        assertThat(alert.evidenceEventIds()).hasSizeGreaterThanOrEqualTo(5);
    }

    private List<Alert> consumeAlerts(Duration timeout) throws Exception {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-alert-reader",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<Alert> alerts = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(Topics.ALERTS));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (alerts.isEmpty() && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    try {
                        alerts.add(objectMapper.readValue(record.value(), Alert.class));
                    } catch (Exception e) {
                        throw new IllegalStateException("unparseable alert: " + record.value(), e);
                    }
                });
            }
        }
        return alerts;
    }
}
