package io.vigil.alertapi.model;

import io.vigil.common.model.Severity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA mapping of an alert for PostgreSQL, the system of record.
 *
 * <p>This is a separate class from the common Alert record on purpose: JPA
 * entities are mutable, identity-based and framework-managed, while the record
 * is a pure value that travels between services. Keeping them apart means the
 * wire format and the database schema can evolve independently.
 */
@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_detected_at", columnList = "detectedAt"),
        @Index(name = "idx_alerts_rule_id", columnList = "ruleId"),
        @Index(name = "idx_alerts_severity", columnList = "severity"),
        @Index(name = "idx_alerts_source", columnList = "source")})
public class AlertEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private Instant detectedAt;

    @Column(nullable = false)
    private String ruleId;

    // stored as text, not ordinal, so reordering the enum cannot corrupt data
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false, length = 1024)
    private String summary;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "alert_evidence", joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "event_id")
    private List<String> evidenceEventIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "alert_context", joinColumns = @JoinColumn(name = "alert_id"))
    @MapKeyColumn(name = "context_key")
    @Column(name = "context_value", length = 1024)
    private Map<String, String> context = new HashMap<>();

    protected AlertEntity() {
        // required by JPA, which instantiates entities reflectively
    }

    public AlertEntity(String id, Instant detectedAt, String ruleId, Severity severity,
            String source, String summary, List<String> evidenceEventIds,
            Map<String, String> context) {
        this.id = id;
        this.detectedAt = detectedAt;
        this.ruleId = ruleId;
        this.severity = severity;
        this.source = source;
        this.summary = summary;
        this.evidenceEventIds = evidenceEventIds == null ? new ArrayList<>()
                : new ArrayList<>(evidenceEventIds);
        this.context = context == null ? new HashMap<>() : new HashMap<>(context);
    }

    public String getId() {
        return id;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public String getRuleId() {
        return ruleId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getEvidenceEventIds() {
        return evidenceEventIds;
    }

    public Map<String, String> getContext() {
        return context;
    }
}
