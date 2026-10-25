package com.redrivermutual.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Same context as {@link ClaimConfirmationFlowIntegrationTest}, so Spring reuses one application. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MailpitTestConfiguration.class})
class NotificationServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void emailOutcomeAndDeadLetterMetricsAreRegistered() throws Exception {
        mockMvc.perform(get("/actuator/metrics/notification.emails").param("tag", "outcome:sent"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/notification.dead_lettered")).andExpect(status().isOk());
    }

    @Test
    void readinessIncludesBrokerAndDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
