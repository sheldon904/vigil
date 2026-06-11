package io.vigil.alertapi.web;

import io.vigil.alertapi.service.AlertQueryService;
import io.vigil.common.model.Alert;
import io.vigil.common.model.Severity;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read API over persisted alerts. All filters are optional and combine:
 * GET /alerts?severity=HIGH&ruleId=BRUTE_FORCE&from=2026-06-11T00:00:00Z
 */
@RestController
public class AlertController {

    private final AlertQueryService queryService;

    public AlertController(AlertQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/alerts")
    public Page<Alert> alerts(
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return queryService.findAlerts(severity, source, ruleId, from, to, pageable);
    }

    @GetMapping("/alerts/{id}")
    public Alert alert(@PathVariable String id) {
        return queryService.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no alert with id " + id));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(queryService.stats());
    }
}
