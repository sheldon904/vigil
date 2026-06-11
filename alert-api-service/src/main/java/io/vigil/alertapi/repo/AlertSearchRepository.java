package io.vigil.alertapi.repo;

import io.vigil.alertapi.model.AlertDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Spring Data Elasticsearch repository for the alerts index. Used for
 * indexing here; querying happens through Kibana and ad hoc ES queries.
 */
public interface AlertSearchRepository extends ElasticsearchRepository<AlertDocument, String> {
}
