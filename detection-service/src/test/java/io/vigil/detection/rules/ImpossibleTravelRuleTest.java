package io.vigil.detection.rules;

import static io.vigil.detection.TestEvents.T0;
import static io.vigil.detection.TestEvents.authSuccess;
import static io.vigil.detection.TestEvents.defaultProperties;
import static org.assertj.core.api.Assertions.assertThat;

import io.vigil.common.model.Alert;
import io.vigil.common.model.SecurityEvent;
import io.vigil.common.model.Severity;
import io.vigil.detection.engine.EventWindowStore;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImpossibleTravelRuleTest {

    private EventWindowStore store;
    private ImpossibleTravelRule rule;

    @BeforeEach
    void setUp() {
        store = new EventWindowStore(defaultProperties());
        rule = new ImpossibleTravelRule(defaultProperties());
    }

    private Optional<Alert> feed(SecurityEvent event) {
        store.add(event);
        return rule.evaluate(event, store);
    }

    @Test
    void sameUserInTwoRegionsWithinTheWindowFires() {
        feed(authSuccess("10.0.0.5", "alice", T0));                      // us-east
        Optional<Alert> result =
                feed(authSuccess("192.168.1.20", "alice", T0.plusSeconds(120))); // eu-west

        assertThat(result).isPresent();
        Alert alert = result.get();
        assertThat(alert.ruleId()).isEqualTo(ImpossibleTravelRule.RULE_ID);
        assertThat(alert.severity()).isEqualTo(Severity.HIGH);
        assertThat(alert.source()).isEqualTo("alice");
        assertThat(alert.context()).containsEntry("firstIp", "10.0.0.5");
        assertThat(alert.context()).containsEntry("secondIp", "192.168.1.20");
    }

    @Test
    void sameUserInTheSameRegionDoesNotFire() {
        feed(authSuccess("10.0.0.5", "alice", T0));                      // us-east
        Optional<Alert> result =
                feed(authSuccess("10.0.0.77", "alice", T0.plusSeconds(120)));   // also us-east

        assertThat(result).isEmpty();
    }

    @Test
    void differentUsersInDifferentRegionsDoNotFire() {
        feed(authSuccess("10.0.0.5", "alice", T0));
        Optional<Alert> result =
                feed(authSuccess("192.168.1.20", "bob", T0.plusSeconds(120)));

        assertThat(result).isEmpty();
    }

    @Test
    void loginsFurtherApartThanTheWindowDoNotFire() {
        feed(authSuccess("10.0.0.5", "alice", T0));
        Optional<Alert> result =
                feed(authSuccess("192.168.1.20", "alice", T0.plusSeconds(600))); // 10 minutes later

        assertThat(result).isEmpty();
    }

    @Test
    void unmappedIpRegionsDoNotFire() {
        feed(authSuccess("8.8.8.8", "alice", T0));
        Optional<Alert> result =
                feed(authSuccess("192.168.1.20", "alice", T0.plusSeconds(120)));

        assertThat(result).isEmpty();
    }

    @Test
    void authFailuresDoNotTriggerTheRule() {
        feed(authSuccess("10.0.0.5", "alice", T0));
        SecurityEvent failure = io.vigil.detection.TestEvents
                .authFailure("192.168.1.20", "alice", T0.plusSeconds(120));
        store.add(failure);

        assertThat(rule.evaluate(failure, store)).isEmpty();
    }
}
