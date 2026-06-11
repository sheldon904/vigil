package io.vigil.alertapi.service;

import io.vigil.alertapi.model.AlertEntity;
import io.vigil.alertapi.repo.AlertRepository;
import io.vigil.common.model.Alert;
import io.vigil.common.model.Severity;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the alert API. Filters combine dynamically through the JPA
 * Criteria API (Specifications): each non-null request parameter contributes
 * one predicate, and Spring Data turns the combination into SQL.
 */
@Service
@Transactional(readOnly = true)
public class AlertQueryService {

    private final AlertRepository repository;

    public AlertQueryService(AlertRepository repository) {
        this.repository = repository;
    }

    public Page<Alert> findAlerts(Severity severity, String source, String ruleId,
            Instant from, Instant to, Pageable pageable) {
        Specification<AlertEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (ruleId != null && !ruleId.isBlank()) {
                predicates.add(cb.equal(root.get("ruleId"), ruleId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("detectedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(AlertMapper::toRecord);
    }

    public Optional<Alert> findById(String id) {
        return repository.findById(id).map(AlertMapper::toRecord);
    }

    public Map<String, Object> stats() {
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        repository.countBySeverity()
                .forEach(row -> bySeverity.put(String.valueOf(row.getKey()), row.getCount()));
        Map<String, Long> byRule = new LinkedHashMap<>();
        repository.countByRule()
                .forEach(row -> byRule.put(String.valueOf(row.getKey()), row.getCount()));
        return Map.of(
                "total", repository.count(),
                "bySeverity", bySeverity,
                "byRule", byRule);
    }
}
