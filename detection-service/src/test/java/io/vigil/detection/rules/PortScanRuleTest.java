package io.vigil.detection.rules;

import static io.vigil.detection.TestEvents.T0;
import static io.vigil.detection.TestEvents.connection;
import static io.vigil.detection.TestEvents.defaultProperties;
import static org.assertj.core.api.Assertions.assertThat;

import io.vigil.common.model.Alert;
import io.vigil.common.model.SecurityEvent;
import io.vigil.common.model.Severity;
import io.vigil.detection.engine.EventWindowStore;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortScanRuleTest {

    private EventWindowStore store;
    private PortScanRule rule;

    @BeforeEach
    void setUp() {
        store = new EventWindowStore(defaultProperties());
        rule = new PortScanRule(defaultProperties());
    }

    private Optional<Alert> feed(SecurityEvent event) {
        store.add(event);
        return rule.evaluate(event, store);
    }

    @Test
    void fifteenDistinctPortsInsideTheWindowFire() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 15; i++) {
            result = feed(connection("172.16.0.9", 1000 + i, T0.plusSeconds(i)));
        }

        assertThat(result).isPresent();
        Alert alert = result.get();
        assertThat(alert.ruleId()).isEqualTo(PortScanRule.RULE_ID);
        assertThat(alert.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(alert.source()).isEqualTo("172.16.0.9");
        assertThat(alert.context()).containsEntry("distinctPortCount", "15");
        assertThat(alert.evidenceEventIds()).hasSize(15);
    }

    @Test
    void fourteenDistinctPortsDoNotFire() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 14; i++) {
            result = feed(connection("172.16.0.9", 1000 + i, T0.plusSeconds(i)));
        }

        assertThat(result).isEmpty();
    }

    @Test
    void manyConnectionsToTheSamePortDoNotFire() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 30; i++) {
            result = feed(connection("172.16.0.9", 443, T0.plusSeconds(i)));
        }

        assertThat(result).isEmpty();
    }

    @Test
    void portsSpreadBeyondTheWindowDoNotFire() {
        Optional<Alert> result = Optional.empty();
        // one port every 10 seconds: a 30s window only ever holds 4 of them
        for (int i = 0; i < 15; i++) {
            result = feed(connection("172.16.0.9", 1000 + i, T0.plusSeconds(i * 10L)));
        }

        assertThat(result).isEmpty();
    }

    @Test
    void portsFromDifferentSourcesAreNotCombined() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 15; i++) {
            result = feed(connection("172.16.0." + i, 1000 + i, T0.plusSeconds(i)));
        }

        assertThat(result).isEmpty();
    }
}
