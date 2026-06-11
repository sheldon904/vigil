package io.vigil.ingest.kafka;

import io.vigil.common.kafka.Topics;
import io.vigil.common.model.SecurityEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes accepted events to the events.raw topic. The event source is used
 * as the Kafka message key, so all events from one source land on the same
 * partition and the detection service sees them in order.
 */
@Component
public class EventPublisher {

    private final KafkaTemplate<String, SecurityEvent> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, SecurityEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(SecurityEvent event) {
        kafkaTemplate.send(Topics.EVENTS_RAW, event.source(), event);
    }
}
