package io.vigil.common.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * An alert produced when a detection rule fires. Published on the alerts
 * Kafka topic by the detection service and persisted by the alert API service.
 *
 * @param id               unique id, a UUID
 * @param detectedAt       when the rule fired
 * @param ruleId           which rule fired, for example BRUTE_FORCE or PORT_SCAN
 * @param severity         how urgent the alert is
 * @param source           the offending source IP or host
 * @param summary          human readable one line description
 * @param evidenceEventIds ids of the events that triggered the rule
 * @param context          rule specific details such as counts, window length, ports
 */
public record Alert(
        String id,
        Instant detectedAt,
        String ruleId,
        Severity severity,
        String source,
        String summary,
        List<String> evidenceEventIds,
        Map<String, String> context) {
}
