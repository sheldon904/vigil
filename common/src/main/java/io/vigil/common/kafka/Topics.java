package io.vigil.common.kafka;

/**
 * Kafka topic names shared by all services. Centralizing them here means a
 * producer and its consumers can never drift apart on a topic name.
 */
public final class Topics {

    /** Raw security events accepted by the ingest service. */
    public static final String EVENTS_RAW = "events.raw";

    /** Alerts emitted by the detection service. */
    public static final String ALERTS = "alerts";

    private Topics() {
        // constants holder, never instantiated
    }
}
