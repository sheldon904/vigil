package io.vigil.detection.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All detection thresholds and window sizes, bound from vigil.detection.* in
 * application.yml. Keeping these in configuration rather than constants means
 * they can be tuned per environment without recompiling.
 *
 * <p>Spring converts strings like "60s" or "5m" into Duration automatically.
 *
 * @param suppression      minimum gap between two alerts for the same rule and source
 * @param retention        how long the window store keeps events in memory
 * @param bruteForce       brute force rule tuning
 * @param portScan         port scan rule tuning
 * @param impossibleTravel impossible travel rule tuning
 */
@ConfigurationProperties(prefix = "vigil.detection")
public record DetectionProperties(
        Duration suppression,
        Duration retention,
        BruteForce bruteForce,
        PortScan portScan,
        ImpossibleTravel impossibleTravel) {

    public record BruteForce(int threshold, Duration window) {
    }

    public record PortScan(int distinctPorts, Duration window) {
    }

    public record ImpossibleTravel(Duration window) {
    }
}
