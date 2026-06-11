package io.vigil.alertapi.repo;

import io.vigil.alertapi.model.AlertEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data JPA repository: the interface is all we write, the
 * implementation (SQL included) is generated at runtime.
 * JpaSpecificationExecutor adds findAll(Specification, Pageable) for the
 * dynamic filter combinations the alerts endpoint supports.
 */
public interface AlertRepository
        extends JpaRepository<AlertEntity, String>, JpaSpecificationExecutor<AlertEntity> {

    /** Projection for group-by counts: key plus count, no entity loading. */
    interface KeyCount {
        Object getKey();

        long getCount();
    }

    @Query("select a.severity as key, count(a) as count from AlertEntity a group by a.severity")
    List<KeyCount> countBySeverity();

    @Query("select a.ruleId as key, count(a) as count from AlertEntity a group by a.ruleId")
    List<KeyCount> countByRule();
}
