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
 * Fires when the same account logs in successfully from two different regions
 * within a window too short for a human to travel between them, which suggests
 * stolen credentials in use. The alert's source is the username, because the
 * account is the entity under attack.
 */
@Component
public class ImpossibleTravelRule implements DetectionRule {

    public static final String RULE_ID = "IMPOSSIBLE_TRAVEL";

    private final DetectionProperties.ImpossibleTravel config;

    public ImpossibleTravelRule(DetectionProperties properties) {
        this.config = properties.impossibleTravel();
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public Optional<Alert> evaluate(SecurityEvent event, EventWindowStore store) {
        if (event.eventType() != EventType.AUTH_SUCCESS
                || event.username() == null || event.username().isBlank()) {
            return Optional.empty();
        }

        Optional<String> currentRegion = IpRegionMap.regionOf(event.source());
        if (currentRegion.isEmpty()) {
            return Optional.empty();
        }

        List<SecurityEvent> successes = store
                .recentByUsername(event.username(), config.window(), event.timestamp())
                .stream()
                .filter(e -> e.eventType() == EventType.AUTH_SUCCESS)
                .toList();

        Optional<SecurityEvent> conflicting = successes.stream()
                .filter(e -> !e.source().equals(event.source()))
                .filter(e -> IpRegionMap.regionOf(e.source())
                        .map(region -> !region.equals(currentRegion.get()))
                        .orElse(false))
                .findFirst();

        return conflicting.map(other -> new Alert(
                UUID.randomUUID().toString(),
                Instant.now(),
                RULE_ID,
                Severity.HIGH,
                event.username(),
                "account %s logged in from %s (%s) and %s (%s) within %d minutes".formatted(
                        event.username(),
                        other.source(), IpRegionMap.regionOf(other.source()).orElse("unknown"),
                        event.source(), currentRegion.get(),
                        config.window().toMinutes()),
                List.of(other.id(), event.id()),
                Map.of(
                        "username", event.username(),
                        "firstIp", other.source(),
                        "secondIp", event.source(),
                        "windowMinutes", String.valueOf(config.window().toMinutes()))));
    }
}
