package io.vigil.alertapi.service;

import io.vigil.alertapi.repo.AlertRepository;
import io.vigil.alertapi.repo.AlertSearchRepository;
import io.vigil.common.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes each consumed alert to both stores. Postgres is the system of
 * record and is written first, inside a transaction. Elasticsearch indexing
 * happens after: if ES is briefly down we log and keep the alert, because
 * losing search convenience is acceptable, losing the record is not.
 */
@Service
public class AlertStore {

    private static final Logger log = LoggerFactory.getLogger(AlertStore.class);

    private final AlertRepository repository;
    private final AlertSearchRepository searchRepository;

    public AlertStore(AlertRepository repository, AlertSearchRepository searchRepository) {
        this.repository = repository;
        this.searchRepository = searchRepository;
    }

    @Transactional
    public void store(Alert alert) {
        // save is an upsert by id, so redelivered Kafka messages are idempotent
        repository.save(AlertMapper.toEntity(alert));
        try {
            searchRepository.save(AlertMapper.toDocument(alert));
        } catch (RuntimeException e) {
            log.error("alert {} persisted to Postgres but failed to index in Elasticsearch",
                    alert.id(), e);
        }
    }
}
