package io.vigil.detection.engine;

import static io.vigil.detection.TestEvents.T0;
import static io.vigil.detection.TestEvents.authFailure;
import static io.vigil.detection.TestEvents.defaultProperties;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.vigil.common.model.Alert;
import io.vigil.detection.kafka.AlertPublisher;
import io.vigil.detection.rules.BruteForceRule;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DetectionEngineTest {

    private AlertPublisher publisher;
    private DetectionEngine engine;

    @BeforeEach
    void setUp() {
        publisher = mock(AlertPublisher.class);
        engine = new DetectionEngine(
                new EventWindowStore(defaultProperties()),
                List.of(new BruteForceRule(defaultProperties())),
                publisher,
                defaultProperties());
    }

    @Test
    void continuingAttackIsSuppressedAfterTheFirstAlert() {
        // ten failures in quick succession: rule conditions hold from the fifth
        // onward, but only one alert may be published inside the suppression gap
        for (int i = 0; i < 10; i++) {
            engine.process(authFailure("10.0.0.5", "alice", T0.plusSeconds(i)));
        }

        verify(publisher, times(1)).publish(any(Alert.class));
    }

    @Test
    void distinctSourcesAlertIndependently() {
        for (int i = 0; i < 5; i++) {
            engine.process(authFailure("10.0.0.5", "alice", T0.plusSeconds(i)));
        }
        for (int i = 0; i < 5; i++) {
            engine.process(authFailure("172.16.3.3", "bob", T0.plusSeconds(i)));
        }

        verify(publisher, times(2)).publish(any(Alert.class));
    }
}
