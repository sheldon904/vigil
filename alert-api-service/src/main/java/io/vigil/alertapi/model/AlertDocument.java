package io.vigil.alertapi.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * The same alert shaped for Elasticsearch. Keyword fields support exact
 * filtering and aggregations in Kibana; the summary is analyzed text so it is
 * full text searchable.
 */
@Document(indexName = "alerts")
public record AlertDocument(
        @Id String id,
        @Field(type = FieldType.Date, format = DateFormat.date_time) Instant detectedAt,
        @Field(type = FieldType.Keyword) String ruleId,
        @Field(type = FieldType.Keyword) String severity,
        @Field(type = FieldType.Keyword) String source,
        @Field(type = FieldType.Text) String summary,
        @Field(type = FieldType.Keyword) List<String> evidenceEventIds,
        @Field(type = FieldType.Flattened) Map<String, String> context) {
}
