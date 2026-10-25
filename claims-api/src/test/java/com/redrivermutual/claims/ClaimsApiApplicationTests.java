package com.redrivermutual.claims;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static com.redrivermutual.claims.ClaimFixtures.WINNIPEG;
import static com.redrivermutual.claims.ClaimFixtures.validRequestJson;
import static com.redrivermutual.claims.ClaimFixtures.with;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application context: real wiring from controller through use case to PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClaimsApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeIsExposedForDockerHealthChecks() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void operationalMetricsAreRegistered() throws Exception {
        mockMvc.perform(get("/actuator/metrics/claims.submitted")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/outbox.backlog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }

    @Test
    void submittingAValidClaimEndToEndReturnsANewClaimNumber() throws Exception {
        String yesterday = LocalDate.now(WINNIPEG).minusDays(1).toString();
        String body = with(validRequestJson(), "dateOfLoss", yesterday).toString();

        mockMvc.perform(post("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber").value(matchesPattern("^CLM-\\d{4}-\\d{6}$")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}
