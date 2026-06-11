package io.vigil.detection.rules;

import static io.vigil.detection.TestEvents.T0;
import static io.vigil.detection.TestEvents.authFailure;
import static io.vigil.detection.TestEvents.authSuccess;
import static io.vigil.detection.TestEvents.defaultProperties;
import static org.assertj.core.api.Assertions.assertThat;

import io.vigil.common.model.Alert;
import io.vigil.common.model.SecurityEvent;
import io.vigil.common.model.Severity;
import io.vigil.detection.engine.EventWindowStore;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BruteForceRuleTest {

    private EventWindowStore store;
    private BruteForceRule rule;

    @BeforeEach
    void setUp() {
        store = new EventWindowStore(defaultProperties());
        rule = new BruteForceRule(defaultProperties());
    }

    private Optional<Alert> feed(SecurityEvent event) {
        store.add(event);
        return rule.evaluate(event, store);
    }

    @Test
    void fiveFailuresInsideTheWindowFire() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 5; i++) {
            result = feed(authFailure("10.0.0.5", "alice", T0.plusSeconds(i * 5)));
        }

        assertThat(result).isPresent();
        Alert alert = result.get();
        assertThat(alert.ruleId()).isEqualTo(BruteForceRule.RULE_ID);
        assertThat(alert.severity()).isEqualTo(Severity.HIGH);
        assertThat(alert.source()).isEqualTo("10.0.0.5");
        assertThat(alert.evidenceEventIds()).hasSize(5);
        assertThat(alert.context()).containsEntry("failureCount", "5");
    }

    @Test
    void fourFailuresDoNotFire() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 4; i++) {
            result = feed(authFailure("10.0.0.5", "alice", T0.plusSeconds(i * 5)));
        }

        assertThat(result).isEmpty();
    }

    @Test
    void fiveFailuresSpreadBeyondTheWindowDoNotFire() {
        Optional<Alert> result = Optional.empty();
        // one failure every 20 seconds: only the last 4 fit in any 60s window
        for (int i = 0; i < 5; i++) {
            result = feed(authFailure("10.0.0.5", "alice", T0.plusSeconds(i * 20)));
        }

        assertThat(result).isEmpty();
    }

    @Test
    void failuresFromDifferentSourcesAreNotCombined() {
        Optional<Alert> result = Optional.empty();
        for (int i = 0; i < 5; i++) {
            result = feed(authFailure("10.0.0." + i, "alice", T0.plusSeconds(i)));
        }

        assertThat(result).isEmpty();
    }

    @Test
    void successfulLoginsDoNotCountTowardTheThreshold() {
        for (int i = 0; i < 4; i++) {
            feed(authFailure("10.0.0.5", "alice", T0.plusSeconds(i)));
        }
        Optional<Alert> result = feed(authSuccess("10.0.0.5", "alice", T0.plusSeconds(5)));

        assertThat(result).isEmpty();
    }
}
