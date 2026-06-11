package io.vigil.ingest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vigil.common.model.EventType;
import io.vigil.common.model.SecurityEvent;
import io.vigil.ingest.kafka.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc test: exercises the controller and its JSON handling
 * without starting a server or connecting to Kafka. The publisher is mocked,
 * which keeps this a unit test of the web layer only.
 */
class EventControllerTest {

    private EventPublisher publisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        publisher = mock(EventPublisher.class);
        // findAndRegisterModules picks up the java.time module so Instant parses
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventController(publisher, objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void singleEventIsAcceptedAndPublishedWithGeneratedId() throws Exception {
        String body = """
                {"timestamp":"2026-06-11T12:00:00Z","source":"10.0.0.5",
                 "username":"alice","eventType":"AUTH_FAILURE"}
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));

        ArgumentCaptor<SecurityEvent> captor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(publisher).publish(captor.capture());
        SecurityEvent published = captor.getValue();
        assertThat(published.id()).isNotBlank();
        assertThat(published.source()).isEqualTo("10.0.0.5");
        assertThat(published.eventType()).isEqualTo(EventType.AUTH_FAILURE);
    }

    @Test
    void batchOfEventsIsAcceptedAndEachIsPublished() throws Exception {
        String body = """
                [{"timestamp":"2026-06-11T12:00:00Z","source":"10.0.0.5","eventType":"AUTH_FAILURE"},
                 {"timestamp":"2026-06-11T12:00:01Z","source":"10.0.0.6","eventType":"AUTH_SUCCESS"}]
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2));

        verify(publisher, times(2)).publish(any(SecurityEvent.class));
    }

    @Test
    void eventMissingSourceIsRejectedWith400() throws Exception {
        String body = """
                {"timestamp":"2026-06-11T12:00:00Z","eventType":"AUTH_FAILURE"}
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(publisher, never()).publish(any());
    }

    @Test
    void unknownEventTypeIsRejectedWith400() throws Exception {
        String body = """
                {"timestamp":"2026-06-11T12:00:00Z","source":"10.0.0.5","eventType":"NOT_A_TYPE"}
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(publisher, never()).publish(any());
    }
}
