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
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Fires when one source touches many distinct destination ports inside a short
 * window: reconnaissance behavior, someone probing for open services.
 */
@Component
public class PortScanRule implements DetectionRule {

    public static final String RULE_ID = "PORT_SCAN";

    private final DetectionProperties.PortScan config;

    public PortScanRule(DetectionProperties properties) {
        this.config = properties.portScan();
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public Optional<Alert> evaluate(SecurityEvent event, EventWindowStore store) {
        if (event.eventType() != EventType.NETWORK_CONNECTION || event.destinationPort() == null) {
            return Optional.empty();
        }

        List<SecurityEvent> connections = store
                .recentBySource(event.source(), config.window(), event.timestamp())
                .stream()
                .filter(e -> e.eventType() == EventType.NETWORK_CONNECTION)
                .filter(e -> e.destinationPort() != null)
                .toList();

        List<Integer> distinctPorts = connections.stream()
                .map(SecurityEvent::destinationPort)
                .distinct()
                .sorted()
                .toList();

        if (distinctPorts.size() < config.distinctPorts()) {
            return Optional.empty();
        }

        String portSample = distinctPorts.stream()
                .limit(10)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return Optional.of(new Alert(
                UUID.randomUUID().toString(),
                Instant.now(),
                RULE_ID,
                Severity.MEDIUM,
                event.source(),
                "%s touched %d distinct ports within %d seconds".formatted(
                        event.source(), distinctPorts.size(), config.window().toSeconds()),
                connections.stream().map(SecurityEvent::id).toList(),
                Map.of(
                        "distinctPortCount", String.valueOf(distinctPorts.size()),
                        "windowSeconds", String.valueOf(config.window().toSeconds()),
                        "threshold", String.valueOf(config.distinctPorts()),
                        "portSample", portSample)));
    }
}
