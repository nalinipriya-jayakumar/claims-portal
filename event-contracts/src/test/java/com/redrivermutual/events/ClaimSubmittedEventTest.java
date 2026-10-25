package com.redrivermutual.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the wire format both services depend on. A failure here means a breaking contract change. */
class ClaimSubmittedEventTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private static final ClaimSubmittedEvent EVENT = ClaimSubmittedEvent.v1(
            UUID.fromString("7f9c2a4e-0000-4000-8000-000000000001"),
            Instant.parse("2026-10-17T14:05:22Z"),
            "c1a2",
            new ClaimSubmittedEvent.Data("CLM-2026-000123", "Jane", "jane@example.com", "WATER",
                    LocalDate.of(2026, 10, 15)));

    @Test
    void serializesToTheDocumentedV1Shape() throws Exception {
        String json = JSON.writeValueAsString(EVENT);

        assertThat(JSON.readTree(json)).isEqualTo(JSON.readTree("""
                {
                  "eventId": "7f9c2a4e-0000-4000-8000-000000000001",
                  "eventType": "ClaimSubmitted",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-17T14:05:22Z",
                  "correlationId": "c1a2",
                  "data": {
                    "claimNumber": "CLM-2026-000123",
                    "customerFirstName": "Jane",
                    "customerEmail": "jane@example.com",
                    "lossType": "WATER",
                    "dateOfLoss": "2026-10-15"
                  }
                }
                """));
    }

    @Test
    void roundTripsWithoutLoss() throws Exception {
        String json = JSON.writeValueAsString(EVENT);

        assertThat(JSON.readValue(json, ClaimSubmittedEvent.class)).isEqualTo(EVENT);
    }

    @Test
    void toStringDoesNotExposeCustomerDetails() {
        assertThat(EVENT.toString()).contains("CLM-2026-000123").doesNotContain("jane@example.com", "Jane");
    }
}
