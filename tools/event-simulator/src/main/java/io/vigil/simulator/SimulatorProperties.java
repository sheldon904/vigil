package io.vigil.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simulator tuning, bound from vigil.simulator.* in application.yml.
 *
 * @param ingestUrl    full URL of the ingest endpoint
 * @param hmacSecret   shared secret, must match the ingest service
 * @param benignEvents how many background noise events to interleave
 */
@ConfigurationProperties(prefix = "vigil.simulator")
public record SimulatorProperties(String ingestUrl, String hmacSecret, int benignEvents) {
}
