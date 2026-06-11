package io.vigil.detection.engine;

import io.vigil.common.model.SecurityEvent;
import io.vigil.detection.config.DetectionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding windows of recent events, indexed by source and by
 * username. Rules query "what happened from this source or for this user in
 * the last N seconds" and the store evicts anything older than the retention.
 *
 * <p>Windows are based on event timestamps (event time), not arrival time, so
 * replayed or slightly delayed events still land in the right window.
 *
 * <p>This is deliberately in-memory: simple, fast, and good enough for one
 * detection instance. The trade-off is that state is lost on restart and
 * cannot be shared across instances. In production this state would live in
 * Redis or be modeled as a Kafka Streams windowed aggregation.
 */
@Component
public class EventWindowStore {

    private final Map<String, Deque<SecurityEvent>> bySource = new ConcurrentHashMap<>();
    private final Map<String, Deque<SecurityEvent>> byUsername = new ConcurrentHashMap<>();
    private final Duration retention;

    public EventWindowStore(DetectionProperties properties) {
        this.retention = properties.retention();
    }

    public void add(SecurityEvent event) {
        append(bySource, event.source(), event);
        if (event.username() != null && !event.username().isBlank()) {
            append(byUsername, event.username(), event);
        }
    }

    /** Events from this source with timestamp in [asOf - window, asOf]. */
    public List<SecurityEvent> recentBySource(String source, Duration window, Instant asOf) {
        return snapshot(bySource.get(source), window, asOf);
    }

    /** Events for this username with timestamp in [asOf - window, asOf]. */
    public List<SecurityEvent> recentByUsername(String username, Duration window, Instant asOf) {
        return snapshot(byUsername.get(username), window, asOf);
    }

    private void append(Map<String, Deque<SecurityEvent>> index, String key, SecurityEvent event) {
        Deque<SecurityEvent> deque = index.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(event);
            // evict by retention from the head; events arrive roughly in order
            Instant cutoff = event.timestamp().minus(retention);
            while (!deque.isEmpty() && deque.peekFirst().timestamp().isBefore(cutoff)) {
                deque.removeFirst();
            }
        }
    }

    private List<SecurityEvent> snapshot(Deque<SecurityEvent> deque, Duration window, Instant asOf) {
        if (deque == null) {
            return List.of();
        }
        Instant from = asOf.minus(window);
        List<SecurityEvent> result = new ArrayList<>();
        synchronized (deque) {
            for (SecurityEvent event : deque) {
                if (!event.timestamp().isBefore(from) && !event.timestamp().isAfter(asOf)) {
                    result.add(event);
                }
            }
        }
        return result;
    }
}
