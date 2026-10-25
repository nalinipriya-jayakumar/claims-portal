package com.redrivermutual.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redrivermutual.claims.adapter.in.web.TrimmingStringModule;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.Customer;
import com.redrivermutual.claims.domain.LossType;
import com.redrivermutual.claims.domain.PropertyAddress;
import com.redrivermutual.claims.domain.Province;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Shared test data. The fixed clock is 10:00 on 18 October 2026 in Winnipeg. */
public final class ClaimFixtures {

    public static final ZoneId WINNIPEG = ZoneId.of("America/Winnipeg");
    public static final Instant NOW = Instant.parse("2026-10-18T15:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, WINNIPEG);
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    /** Parses like the API does: trims strings and treats blank ones as absent. */
    public static final ObjectMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .addModule(new TrimmingStringModule())
            .build();

    private static final String VALID_REQUEST = """
            {
              "customer": {
                "firstName": "Jane",
                "lastName": "O'Neil",
                "email": "Jane.ONeil@Example.com",
                "phone": "2045550142"
              },
              "policyNumber": "HO-12345678",
              "dateOfLoss": "2026-10-15",
              "lossType": "WATER",
              "propertyAddress": {
                "street": "123 Main Street",
                "city": "Winnipeg",
                "province": "MB",
                "postalCode": "r3c1a1"
              },
              "description": "Pipe burst in the basement overnight and flooded the laundry room.",
              "consentAcknowledged": true
            }
            """;

    private ClaimFixtures() {
    }

    public static ClaimDetails validDetails() {
        return new ClaimDetails(
                new Customer("Jane", "O'Neil", "jane.oneil@example.com", "2045550142"),
                "HO-12345678",
                LocalDate.of(2026, 10, 15),
                LossType.WATER,
                new PropertyAddress("123 Main Street", "Winnipeg", Province.MB, "R3C 1A1"),
                "Pipe burst in the basement overnight and flooded the laundry room.");
    }

    /** A fresh, mutable copy of a valid request body. */
    public static ObjectNode validRequestJson() {
        try {
            return (ObjectNode) JSON.readTree(VALID_REQUEST);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sets a value at a dot-separated path such as {@code customer.email}; {@code null} sets JSON null. */
    public static ObjectNode with(ObjectNode json, String path, Object value) {
        String[] parts = path.split("\\.");
        ObjectNode parent = json;
        for (int i = 0; i < parts.length - 1; i++) {
            parent = (ObjectNode) parent.get(parts[i]);
        }
        JsonNode node = JSON.valueToTree(value);
        parent.set(parts[parts.length - 1], node);
        return json;
    }

    public static ObjectNode without(ObjectNode json, String path) {
        String[] parts = path.split("\\.");
        ObjectNode parent = json;
        for (int i = 0; i < parts.length - 1; i++) {
            parent = (ObjectNode) parent.get(parts[i]);
        }
        parent.remove(parts[parts.length - 1]);
        return json;
    }
}
