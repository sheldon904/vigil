package io.vigil.alertapi.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vigil.alertapi.service.AlertQueryService;
import io.vigil.common.model.Alert;
import io.vigil.common.model.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlertControllerTest {

    private final Alert alert = new Alert(
            "alert-1",
            Instant.parse("2026-06-11T12:00:00Z"),
            "BRUTE_FORCE",
            Severity.HIGH,
            "10.0.0.5",
            "5 failed logins from 10.0.0.5 within 60 seconds",
            List.of("e1"),
            Map.of("failureCount", "5"));

    private AlertQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(AlertQueryService.class);
        // register the java.time module so Instant fields serialize in tests
        var jsonConverter = new MappingJackson2HttpMessageConverter(
                new ObjectMapper().findAndRegisterModules());
        mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(queryService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(jsonConverter)
                .build();
    }

    private PageImpl<Alert> pageOf(Alert... alerts) {
        return new PageImpl<>(List.of(alerts), PageRequest.of(0, 20), alerts.length);
    }

    @Test
    void alertsEndpointReturnsAPageOfAlerts() throws Exception {
        when(queryService.findAlerts(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(pageOf(alert));

        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("alert-1"))
                .andExpect(jsonPath("$.content[0].ruleId").value("BRUTE_FORCE"));
    }

    @Test
    void severityFilterIsPassedThrough() throws Exception {
        when(queryService.findAlerts(eq(Severity.HIGH), isNull(), isNull(), isNull(), isNull(),
                any())).thenReturn(pageOf(alert));

        mockMvc.perform(get("/alerts").param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));
    }

    @Test
    void singleAlertIsReturnedById() throws Exception {
        when(queryService.findById("alert-1")).thenReturn(Optional.of(alert));

        mockMvc.perform(get("/alerts/alert-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("10.0.0.5"));
    }

    @Test
    void unknownAlertIdGives404() throws Exception {
        when(queryService.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/alerts/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statsEndpointReturnsCounts() throws Exception {
        when(queryService.stats()).thenReturn(Map.of(
                "total", 7L,
                "bySeverity", Map.of("HIGH", 5L, "MEDIUM", 2L),
                "byRule", Map.of("BRUTE_FORCE", 5L, "PORT_SCAN", 2L)));

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.bySeverity.HIGH").value(5))
                .andExpect(jsonPath("$.byRule.PORT_SCAN").value(2));
    }
}
