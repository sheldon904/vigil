package io.vigil.simulator;

import io.vigil.common.model.EventType;
import io.vigil.common.model.SecurityEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Synthesizes events. Benign traffic is randomized background noise; the
 * attack builders produce sequences crafted to trip exactly one rule each.
 */
public class EventFactory {

    private static final List<String> BENIGN_SOURCES = List.of(
            "10.0.0.11", "10.0.0.12", "10.0.0.13", "10.1.4.20", "192.168.1.30");
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave");
    private static final List<Integer> COMMON_PORTS = List.of(80, 443, 53, 22, 8080);
    private static final List<String> DESTINATIONS = List.of(
            "app-server-1", "db-server-1", "file-share", "mail-gateway");

    private final Random random;

    public EventFactory(long seed) {
        this.random = new Random(seed);
    }

    /** One random benign event: logins succeed, connections hit common ports. */
    public SecurityEvent benign() {
        String source = pick(BENIGN_SOURCES);
        return switch (random.nextInt(5)) {
            case 0 -> event(source, pick(USERS), EventType.AUTH_SUCCESS, null, null);
            case 1 -> event(source, null, EventType.NETWORK_CONNECTION,
                    pick(DESTINATIONS), pick(COMMON_PORTS));
            case 2 -> event(source, pick(USERS), EventType.PROCESS_START,
                    "app-server-1", null);
            case 3 -> event(source, pick(USERS), EventType.FILE_ACCESS, "file-share", null);
            // a lone failed login now and then is normal, far below the threshold
            default -> event(source, pick(USERS), EventType.AUTH_FAILURE, null, null);
        };
    }

    /** Brute force burst: many failed logins, one source, one username. */
    public List<SecurityEvent> bruteForceBurst(String source, String username, int count) {
        List<SecurityEvent> burst = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            burst.add(event(source, username, EventType.AUTH_FAILURE, null, null));
        }
        return burst;
    }

    /** Port scan burst: one source probing many distinct ports. */
    public List<SecurityEvent> portScanBurst(String source, int distinctPorts) {
        List<SecurityEvent> burst = new ArrayList<>();
        for (int i = 0; i < distinctPorts; i++) {
            burst.add(event(source, null, EventType.NETWORK_CONNECTION,
                    "db-server-1", 1000 + i));
        }
        return burst;
    }

    /** Impossible travel: the same account logs in from two regions. */
    public List<SecurityEvent> impossibleTravelPair(String username) {
        return List.of(
                event("10.0.0.42", username, EventType.AUTH_SUCCESS, null, null),
                event("192.168.1.99", username, EventType.AUTH_SUCCESS, null, null));
    }

    private SecurityEvent event(String source, String username, EventType type,
            String destination, Integer port) {
        return new SecurityEvent(UUID.randomUUID().toString(), Instant.now(), source, username,
                type, destination, port, null, null);
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
