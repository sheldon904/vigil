package io.vigil.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vigil.common.model.SecurityEvent;
import io.vigil.common.security.HmacSigner;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Posts signed event batches to the ingest service. The signature is computed
 * over the exact bytes sent, the same contract the ingest filter verifies.
 */
@Component
public class IngestClient {

    private final SimulatorProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public IngestClient(SimulatorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public void send(List<SecurityEvent> events) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(events);
            String signature = HmacSigner.sign(properties.hmacSecret(), body);
            // send the exact bytes that were signed, so the server side HMAC matches
            restClient.post()
                    .uri(properties.ingestUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", signature)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize events", e);
        }
    }
}
