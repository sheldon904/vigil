package io.vigil.alertapi.service;

import io.vigil.alertapi.model.AlertDocument;
import io.vigil.alertapi.model.AlertEntity;
import io.vigil.common.model.Alert;
import java.util.List;
import java.util.Map;

/** Translates between the wire record, the JPA entity, and the ES document. */
public final class AlertMapper {

    private AlertMapper() {
    }

    public static AlertEntity toEntity(Alert alert) {
        return new AlertEntity(alert.id(), alert.detectedAt(), alert.ruleId(), alert.severity(),
                alert.source(), alert.summary(), alert.evidenceEventIds(), alert.context());
    }

    public static AlertDocument toDocument(Alert alert) {
        return new AlertDocument(alert.id(), alert.detectedAt(), alert.ruleId(),
                alert.severity().name(), alert.source(), alert.summary(),
                alert.evidenceEventIds(), alert.context());
    }

    public static Alert toRecord(AlertEntity entity) {
        return new Alert(entity.getId(), entity.getDetectedAt(), entity.getRuleId(),
                entity.getSeverity(), entity.getSource(), entity.getSummary(),
                List.copyOf(entity.getEvidenceEventIds()), Map.copyOf(entity.getContext()));
    }
}
