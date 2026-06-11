package io.vigil.detection;

import io.vigil.common.model.EventType;
import io.vigil.common.model.SecurityEvent;
import io.vigil.detection.config.DetectionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Shared builders for crafting event sequences in rule tests. */
public final class TestEvents {

    public static final Instant T0 = Instant.parse("2026-06-11T12:00:00Z");

    private TestEvents() {
    }

    /** Default tuning used across the rule tests: matches application.yml. */
    public static DetectionProperties defaultProperties() {
        return new DetectionProperties(
                Duration.ofSeconds(60),
                Duration.ofMinutes(10),
                new DetectionProperties.BruteForce(5, Duration.ofSeconds(60)),
                new DetectionProperties.PortScan(15, Duration.ofSeconds(30)),
                new DetectionProperties.ImpossibleTravel(Duration.ofMinutes(5)));
    }

    public static SecurityEvent authFailure(String source, String username, Instant at) {
        return event(source, username, EventType.AUTH_FAILURE, null, null, at);
    }

    public static SecurityEvent authSuccess(String source, String username, Instant at) {
        return event(source, username, EventType.AUTH_SUCCESS, null, null, at);
    }

    public static SecurityEvent connection(String source, int port, Instant at) {
        return event(source, null, EventType.NETWORK_CONNECTION, "10.9.9.9", port, at);
    }

    public static SecurityEvent event(String source, String username, EventType type,
            String destination, Integer port, Instant at) {
        return new SecurityEvent(UUID.randomUUID().toString(), at, source, username,
                type, destination, port, null, null);
    }
}
