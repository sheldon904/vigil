package io.vigil.ingest.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vigil.common.model.SecurityEvent;
import io.vigil.ingest.kafka.EventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion endpoint. Accepts one security event or a JSON array of them,
 * assigns ids where missing, and hands each event to the Kafka publisher.
 * Responds 202 Accepted: the request is queued, not yet fully processed,
 * which is the honest status code for an asynchronous pipeline.
 */
@RestController
public class EventController {

    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;

    public EventController(EventPublisher publisher, ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody JsonNode body)
            throws JsonProcessingException {
        List<SecurityEvent> events = parse(body);
        for (SecurityEvent event : events) {
            publisher.publish(event.withGeneratedIdIfMissing());
        }
        return ResponseEntity.accepted().body(Map.of("accepted", events.size()));
    }

    /** Accepts either a single event object or an array of events. */
    private List<SecurityEvent> parse(JsonNode body) throws JsonProcessingException {
        List<SecurityEvent> events = new ArrayList<>();
        if (body.isArray()) {
            for (JsonNode node : body) {
                events.add(toEvent(node));
            }
        } else {
            events.add(toEvent(body));
        }
        if (events.isEmpty()) {
            throw new IllegalArgumentException("request contained no events");
        }
        return events;
    }

    private SecurityEvent toEvent(JsonNode node) throws JsonProcessingException {
        SecurityEvent event = objectMapper.treeToValue(node, SecurityEvent.class);
        if (event.source() == null || event.source().isBlank()) {
            throw new IllegalArgumentException("event is missing required field: source");
        }
        if (event.eventType() == null) {
            throw new IllegalArgumentException("event is missing required field: eventType");
        }
        if (event.timestamp() == null) {
            throw new IllegalArgumentException("event is missing required field: timestamp");
        }
        return event;
    }
}
