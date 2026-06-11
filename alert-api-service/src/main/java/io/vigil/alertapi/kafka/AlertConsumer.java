package io.vigil.alertapi.kafka;

import io.vigil.alertapi.service.AlertStore;
import io.vigil.common.kafka.Topics;
import io.vigil.common.model.Alert;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes alerts from Kafka and hands them to the store. */
@Component
public class AlertConsumer {

    private final AlertStore store;

    public AlertConsumer(AlertStore store) {
        this.store = store;
    }

    @KafkaListener(topics = Topics.ALERTS)
    public void onAlert(Alert alert) {
        store.store(alert);
    }
}
