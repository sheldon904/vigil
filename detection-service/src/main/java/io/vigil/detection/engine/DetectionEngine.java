package io.vigil.detection.engine;

import io.vigil.common.model.Alert;
import io.vigil.common.model.SecurityEvent;
import io.vigil.detection.config.DetectionProperties;
import io.vigil.detection.kafka.AlertPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs every detection rule against each incoming event and publishes the
 * alerts that fire.
 *
 * <p>Spring injects all DetectionRule beans as a List, so the rule set is
 * whatever components exist on the classpath: open for extension, closed for
 * modification.
 *
 * <p>Suppression: once a rule has fired for a given source, the same rule and
 * source stay quiet for the configured suppression interval. Without this, the
 * sixth, seventh and eighth failed login would each raise their own duplicate
 * alert while the window still holds five or more failures.
 */
@Component
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);

    private final EventWindowStore store;
    private final List<DetectionRule> rules;
    private final AlertPublisher alertPublisher;
    private final Duration suppression;
    private final Map<String, Instant> lastFired = new ConcurrentHashMap<>();

    public DetectionEngine(EventWindowStore store, List<DetectionRule> rules,
            AlertPublisher alertPublisher, DetectionProperties properties) {
        this.store = store;
        this.rules = rules;
        this.alertPublisher = alertPublisher;
        this.suppression = properties.suppression();
        log.info("detection engine loaded {} rules: {}",
                rules.size(), rules.stream().map(DetectionRule::ruleId).toList());
    }

    public void process(SecurityEvent event) {
        store.add(event);
        for (DetectionRule rule : rules) {
            rule.evaluate(event, store).ifPresent(this::publishUnlessSuppressed);
        }
    }

    private void publishUnlessSuppressed(Alert alert) {
        String key = alert.ruleId() + "|" + alert.source();
        Instant previous = lastFired.get(key);
        if (previous != null
                && Duration.between(previous, alert.detectedAt()).compareTo(suppression) < 0) {
            return;
        }
        lastFired.put(key, alert.detectedAt());
        log.info("rule {} fired for {}: {}", alert.ruleId(), alert.source(), alert.summary());
        alertPublisher.publish(alert);
    }
}
