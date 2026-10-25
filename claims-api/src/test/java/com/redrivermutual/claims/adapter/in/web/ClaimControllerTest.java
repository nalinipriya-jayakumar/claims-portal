package com.redrivermutual.claims.adapter.in.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.ClaimNumber;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.redrivermutual.claims.ClaimFixtures.FIXED_CLOCK;
import static com.redrivermutual.claims.ClaimFixtures.NOW;
import static com.redrivermutual.claims.ClaimFixtures.validDetails;
import static com.redrivermutual.claims.ClaimFixtures.validRequestJson;
import static com.redrivermutual.claims.ClaimFixtures.with;
import static com.redrivermutual.claims.ClaimFixtures.without;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of POST /api/v1/claims: status codes, problem+json shape and headers. */
@WebMvcTest(ClaimController.class)
@Import({ClaimRequestValidator.class, ClaimControllerTest.FixedRules.class})
class ClaimControllerTest {

    private static final String URL = "/api/v1/claims";
    private static final String UUID_PATTERN = "^[0-9a-f-]{36}$";

    @TestConfiguration
    static class FixedRules {
        @Bean
        DateOfLossPolicy dateOfLossPolicy() {
            return new DateOfLossPolicy(FIXED_CLOCK, 365);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitClaimUseCase submitClaimUseCase;

    @Test
    void validSubmissionReturns201WithClaimNumberAndConfirmationMessage() throws Exception {
        given(submitClaimUseCase.submit(any()))
                .willReturn(Claim.submit(ClaimNumber.of(2026, 123), validDetails(), NOW));

        submit(validRequestJson())
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.claimNumber").value("CLM-2026-000123"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").value("2026-10-18T15:00:00Z"))
                .andExpect(jsonPath("$.message").value(containsString("confirmation email")));
    }

    @Test
    void requestIsTrimmedAndNormalizedBeforeReachingTheUseCase() throws Exception {
        given(submitClaimUseCase.submit(any()))
                .willReturn(Claim.submit(ClaimNumber.of(2026, 1), validDetails(), NOW));
        ObjectNode json = with(validRequestJson(), "customer.firstName", "  Jane  ");

        submit(json).andExpect(status().isCreated());

        ArgumentCaptor<ClaimDetails> details = ArgumentCaptor.forClass(ClaimDetails.class);
        then(submitClaimUseCase).should().submit(details.capture());
        assertThat(details.getValue().customer().firstName()).isEqualTo("Jane");
        assertThat(details.getValue().customer().email()).isEqualTo("jane.oneil@example.com");
        assertThat(details.getValue().propertyAddress().postalCode()).isEqualTo("R3C 1A1");
    }

    @Test
    void invalidFieldsReturn400ProblemWithFieldErrorsAndNothingIsSubmitted() throws Exception {
        ObjectNode json = with(validRequestJson(), "dateOfLoss", "2026-10-19");
        with(json, "propertyAddress.postalCode", "12345");

        submit(json)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Invalid claim submission"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("2 fields need attention."))
                .andExpect(jsonPath("$.instance").value(URL))
                .andExpect(jsonPath("$.correlationId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.errors[0].field").value("dateOfLoss"))
                .andExpect(jsonPath("$.errors[0].code").value("DATE_IN_FUTURE"))
                .andExpect(jsonPath("$.errors[1].field").value("propertyAddress.postalCode"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_FORMAT"));

        then(submitClaimUseCase).should(never()).submit(any());
    }

    @Test
    void missingFieldIsReportedAsRequired() throws Exception {
        submit(without(validRequestJson(), "policyNumber"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("policyNumber"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        submit(with(validRequestJson(), "customer.isAdmin", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customer.isAdmin"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void valueOutsideAnEnumIsNotAllowed() throws Exception {
        submit(with(validRequestJson(), "lossType", "EARTHQUAKE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lossType"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_ALLOWED_VALUE"));
    }

    @Test
    void unparseableDateIsAFormatError() throws Exception {
        submit(with(validRequestJson(), "dateOfLoss", "15/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dateOfLoss"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void malformedJsonIsAMalformedRequestProblem() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"customer\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/malformed-request"))
                .andExpect(jsonPath("$.errors[0].field").value("$"));
    }

    @Test
    void errorResponsesNeverEchoSubmittedValues() throws Exception {
        String body = submit(with(validRequestJson(), "propertyAddress.postalCode", "SECRET-777"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String enumBody = submit(with(validRequestJson(), "lossType", "SECRET_TYPE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SECRET");
        assertThat(enumBody).doesNotContain("SECRET");
    }

    @Test
    void nonJsonContentTypeReturns415Problem() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void oversizedBodyReturns413Problem() throws Exception {
        ObjectNode json = with(validRequestJson(), "description", "a".repeat(70_000));

        submit(json)
                .andExpect(status().is(413))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/payload-too-large"));

        then(submitClaimUseCase).should(never()).submit(any());
    }

    @Test
    void unexpectedFailureReturnsGeneric500WithoutInternals() throws Exception {
        given(submitClaimUseCase.submit(any()))
                .willThrow(new IllegalStateException("database password=hunter2 for jane@example.com"));

        String body = submit(validRequestJson())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("/problems/internal-error"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("hunter2", "jane@", "IllegalStateException", "at com.");
    }

    @Test
    void wellFormedRequestIdIsEchoedAndUsedAsCorrelationId() throws Exception {
        submit(without(validRequestJson(), "policyNumber"), "abc-123")
                .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void malformedRequestIdIsReplacedSoItCannotForgeLogLines() throws Exception {
        submit(without(validRequestJson(), "policyNumber"), "evil\nINFO fake log line")
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)))
                .andExpect(header().string(CorrelationIdFilter.HEADER, not(containsString("evil"))));
    }

    private ResultActions submit(ObjectNode json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json.toString()));
    }

    private ResultActions submit(ObjectNode json, String requestId) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CorrelationIdFilter.HEADER, requestId)
                .content(json.toString()));
    }
}
