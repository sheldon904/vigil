package io.vigil.detection.rules;

import io.vigil.common.model.Alert;
import io.vigil.common.model.EventType;
import io.vigil.common.model.SecurityEvent;
import io.vigil.common.model.Severity;
import io.vigil.detection.config.DetectionProperties;
import io.vigil.detection.engine.DetectionRule;
import io.vigil.detection.engine.EventWindowStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fires when one source produces a configured number of failed logins inside a
 * short window: the classic password guessing signature.
 */
@Component
public class BruteForceRule implements DetectionRule {

    public static final String RULE_ID = "BRUTE_FORCE";

    private final DetectionProperties.BruteForce config;

    public BruteForceRule(DetectionProperties properties) {
        this.config = properties.bruteForce();
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public Optional<Alert> evaluate(SecurityEvent event, EventWindowStore store) {
        // only a new failure can push the count over the threshold
        if (event.eventType() != EventType.AUTH_FAILURE) {
            return Optional.empty();
        }

        List<SecurityEvent> failures = store
                .recentBySource(event.source(), config.window(), event.timestamp())
                .stream()
                .filter(e -> e.eventType() == EventType.AUTH_FAILURE)
                .toList();

        if (failures.size() < config.threshold()) {
            return Optional.empty();
        }

        return Optional.of(new Alert(
                UUID.randomUUID().toString(),
                Instant.now(),
                RULE_ID,
                Severity.HIGH,
                event.source(),
                "%d failed logins from %s within %d seconds".formatted(
                        failures.size(), event.source(), config.window().toSeconds()),
                failures.stream().map(SecurityEvent::id).toList(),
                Map.of(
                        "failureCount", String.valueOf(failures.size()),
                        "windowSeconds", String.valueOf(config.window().toSeconds()),
                        "threshold", String.valueOf(config.threshold()))));
    }
}
