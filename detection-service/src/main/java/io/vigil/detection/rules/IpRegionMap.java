package io.vigil.detection.rules;

import java.util.Map;
import java.util.Optional;

/**
 * Maps source IPs to coarse regions by prefix. This is a deliberate demo
 * simplification: a real deployment would call a GeoIP database such as
 * MaxMind. The prefixes here match the address ranges the event simulator
 * uses, which is enough to demonstrate the impossible travel rule.
 */
final class IpRegionMap {

    private static final Map<String, String> PREFIX_TO_REGION = Map.of(
            "10.0.", "us-east",
            "10.1.", "us-west",
            "192.168.", "eu-west",
            "172.16.", "ap-south",
            "203.0.113.", "sa-east");

    private IpRegionMap() {
    }

    /** Returns the region for an IP, or empty if the prefix is not mapped. */
    static Optional<String> regionOf(String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        return PREFIX_TO_REGION.entrySet().stream()
                .filter(entry -> ip.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
