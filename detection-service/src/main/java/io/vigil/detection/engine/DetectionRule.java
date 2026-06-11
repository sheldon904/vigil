package io.vigil.detection.engine;

import io.vigil.common.model.Alert;
import io.vigil.common.model.SecurityEvent;
import java.util.Optional;

/**
 * One pluggable detection rule. Implementations are Spring components; the
 * engine receives all of them as an injected List, so adding a rule is just
 * adding a class, with no engine changes.
 */
public interface DetectionRule {

    /** Stable identifier carried on every alert this rule emits. */
    String ruleId();

    /**
     * Evaluates the rule against the incoming event, with access to the recent
     * event windows. Returns an alert if the rule fires, empty otherwise.
     */
    Optional<Alert> evaluate(SecurityEvent event, EventWindowStore store);
}
