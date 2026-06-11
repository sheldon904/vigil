package io.vigil.detection.kafka;

import io.vigil.common.kafka.Topics;
import io.vigil.common.model.SecurityEvent;
import io.vigil.detection.engine.DetectionEngine;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes raw events from Kafka and feeds them to the detection engine.
 *
 * <p>KafkaListener turns this method into a managed consumer: Spring handles
 * polling, deserialization (configured in application.yml), offset commits,
 * and retries. Because the ingest service keys messages by source, all events
 * from one source arrive on one partition, in order.
 */
@Component
public class EventConsumer {

    private final DetectionEngine engine;

    public EventConsumer(DetectionEngine engine) {
        this.engine = engine;
    }

    @KafkaListener(topics = Topics.EVENTS_RAW)
    public void onEvent(SecurityEvent event) {
        engine.process(event);
    }
}
