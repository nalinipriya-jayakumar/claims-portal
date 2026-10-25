package com.redrivermutual.claims.adapter.in.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionRequest;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.redrivermutual.claims.ClaimFixtures.FIXED_CLOCK;
import static com.redrivermutual.claims.ClaimFixtures.JSON;
import static com.redrivermutual.claims.ClaimFixtures.validRequestJson;
import static com.redrivermutual.claims.ClaimFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Every validation rule and the error code it produces, without starting Spring. */
class ClaimRequestValidatorTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private final ClaimRequestValidator validator =
            new ClaimRequestValidator(FACTORY.getValidator(), new DateOfLossPolicy(FIXED_CLOCK, 365));

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void validRequestPasses() {
        assertThatNoException().isThrownBy(() -> validator.validate(request(validRequestJson())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Zoë", "Mary-Jane", "D'Arcy", "Jean Luc", "Ægir"})
    void namesWithAccentsHyphensApostrophesAndSpacesAreValid(String name) {
        assertThatNoException().isThrownBy(() ->
                validator.validate(request(with(validRequestJson(), "customer.firstName", name))));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                arguments("customer", null, ErrorCode.REQUIRED),
                arguments("customer.firstName", "", ErrorCode.REQUIRED),
                arguments("customer.firstName", "   ", ErrorCode.REQUIRED),
                arguments("customer.firstName", "J4ne", ErrorCode.INVALID_FORMAT),
                arguments("customer.firstName", "-Jane", ErrorCode.INVALID_FORMAT),
                arguments("customer.lastName", "a".repeat(51), ErrorCode.TOO_LONG),
                arguments("customer.email", null, ErrorCode.REQUIRED),
                arguments("customer.email", "jane@example", ErrorCode.INVALID_FORMAT),
                arguments("customer.email", "not-an-email", ErrorCode.INVALID_FORMAT),
                arguments("customer.phone", "123", ErrorCode.INVALID_FORMAT),
                arguments("customer.phone", "1045550142", ErrorCode.INVALID_FORMAT),
                arguments("policyNumber", "", ErrorCode.REQUIRED),
                arguments("policyNumber", "HO-123", ErrorCode.INVALID_FORMAT),
                arguments("dateOfLoss", null, ErrorCode.REQUIRED),
                arguments("dateOfLoss", "2026-10-19", ErrorCode.DATE_IN_FUTURE),
                arguments("dateOfLoss", "2025-10-17", ErrorCode.DATE_TOO_OLD),
                arguments("lossType", null, ErrorCode.REQUIRED),
                arguments("propertyAddress", null, ErrorCode.REQUIRED),
                arguments("propertyAddress.street", "", ErrorCode.REQUIRED),
                arguments("propertyAddress.city", "a".repeat(61), ErrorCode.TOO_LONG),
                arguments("propertyAddress.province", null, ErrorCode.REQUIRED),
                arguments("propertyAddress.postalCode", "12345", ErrorCode.INVALID_FORMAT),
                arguments("description", "Too short", ErrorCode.TOO_SHORT),
                arguments("description", "a".repeat(2001), ErrorCode.TOO_LONG),
                arguments("consentAcknowledged", false, ErrorCode.MUST_BE_TRUE),
                arguments("consentAcknowledged", null, ErrorCode.REQUIRED));
    }

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @MethodSource("invalidFields")
    void invalidFieldIsReportedWithItsCode(String field, Object value, ErrorCode expectedCode) {
        ClaimValidationException ex = catchThrowableOfType(ClaimValidationException.class,
                () -> validator.validate(request(with(validRequestJson(), field, value))));

        assertThat(ex.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo(field);
            assertThat(violation.code()).isEqualTo(expectedCode);
            assertThat(violation.message()).isNotBlank();
        });
    }

    @Test
    void allProblemsAreReportedTogetherIncludingTheDateRule() {
        ObjectNode json = validRequestJson();
        with(json, "customer.email", "bad");
        with(json, "dateOfLoss", "2030-01-01");
        with(json, "propertyAddress.postalCode", "bad");

        ClaimValidationException ex = catchThrowableOfType(ClaimValidationException.class,
                () -> validator.validate(request(json)));

        assertThat(ex.violations())
                .extracting(FieldViolation::field)
                .containsExactly("customer.email", "dateOfLoss", "propertyAddress.postalCode");
    }

    @Test
    void messagesNeverEchoTheSubmittedValue() {
        ClaimValidationException ex = catchThrowableOfType(ClaimValidationException.class,
                () -> validator.validate(request(with(validRequestJson(), "customer.email", "secret-value@nowhere"))));

        assertThat(ex.violations()).allSatisfy(v -> assertThat(v.message()).doesNotContain("secret-value"));
    }

    private static ClaimSubmissionRequest request(ObjectNode json) {
        try {
            return JSON.treeToValue(json, ClaimSubmissionRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
