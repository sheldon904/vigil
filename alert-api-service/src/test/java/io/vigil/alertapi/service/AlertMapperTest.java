package io.vigil.alertapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.vigil.alertapi.model.AlertDocument;
import io.vigil.alertapi.model.AlertEntity;
import io.vigil.common.model.Alert;
import io.vigil.common.model.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertMapperTest {

    private final Alert alert = new Alert(
            "alert-1",
            Instant.parse("2026-06-11T12:00:00Z"),
            "BRUTE_FORCE",
            Severity.HIGH,
            "10.0.0.5",
            "5 failed logins from 10.0.0.5 within 60 seconds",
            List.of("e1", "e2", "e3", "e4", "e5"),
            Map.of("failureCount", "5", "windowSeconds", "60"));

    @Test
    void recordToEntityAndBackPreservesEveryField() {
        AlertEntity entity = AlertMapper.toEntity(alert);
        Alert roundTripped = AlertMapper.toRecord(entity);

        assertThat(roundTripped.id()).isEqualTo(alert.id());
        assertThat(roundTripped.detectedAt()).isEqualTo(alert.detectedAt());
        assertThat(roundTripped.ruleId()).isEqualTo(alert.ruleId());
        assertThat(roundTripped.severity()).isEqualTo(alert.severity());
        assertThat(roundTripped.source()).isEqualTo(alert.source());
        assertThat(roundTripped.summary()).isEqualTo(alert.summary());
        assertThat(roundTripped.evidenceEventIds())
                .containsExactlyElementsOf(alert.evidenceEventIds());
        assertThat(roundTripped.context()).containsAllEntriesOf(alert.context());
    }

    @Test
    void recordToDocumentKeepsIdAndSeverityName() {
        AlertDocument document = AlertMapper.toDocument(alert);

        assertThat(document.id()).isEqualTo("alert-1");
        assertThat(document.severity()).isEqualTo("HIGH");
        assertThat(document.ruleId()).isEqualTo("BRUTE_FORCE");
        assertThat(document.evidenceEventIds()).hasSize(5);
    }

    @Test
    void nullCollectionsBecomeEmptyCollectionsOnTheEntity() {
        Alert sparse = new Alert("alert-2", Instant.now(), "PORT_SCAN", Severity.MEDIUM,
                "172.16.0.9", "scan", null, null);

        AlertEntity entity = AlertMapper.toEntity(sparse);

        assertThat(entity.getEvidenceEventIds()).isEmpty();
        assertThat(entity.getContext()).isEmpty();
    }
}
