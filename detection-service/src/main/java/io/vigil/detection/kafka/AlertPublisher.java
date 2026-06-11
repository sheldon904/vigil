package io.vigil.detection.kafka;

import io.vigil.common.kafka.Topics;
import io.vigil.common.model.Alert;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes alerts to the alerts topic, keyed by the offending source so
 * alerts about one attacker stay ordered.
 */
@Component
public class AlertPublisher {

    private final KafkaTemplate<String, Alert> kafkaTemplate;

    public AlertPublisher(KafkaTemplate<String, Alert> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(Alert alert) {
        kafkaTemplate.send(Topics.ALERTS, alert.source(), alert);
    }
}
