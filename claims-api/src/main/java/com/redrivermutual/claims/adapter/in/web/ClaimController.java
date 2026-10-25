package com.redrivermutual.claims.adapter.in.web;

import com.redrivermutual.claims.adapter.in.web.api.ClaimsApi;
import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionRequest;
import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionResponse;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmissionResult;
import com.redrivermutual.claims.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Implements {@link ClaimsApi}, which is generated from {@code openapi/claims-api.yaml}: the path,
 * method, media types, parameters and model types all come from the contract, so the controller
 * cannot drift from it without failing to compile.
 *
 * <p>Schema constraints have already been checked by the time this runs (the generated interface
 * declares {@code @Valid}); this adds the rules the schema describes in prose.
 */
@RestController
class ClaimController implements ClaimsApi {

    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final SubmitClaimUseCase submitClaimUseCase;
    private final ClaimRequestValidator requestValidator;

    ClaimController(SubmitClaimUseCase submitClaimUseCase, ClaimRequestValidator requestValidator) {
        this.submitClaimUseCase = submitClaimUseCase;
        this.requestValidator = requestValidator;
    }

    /** {@code xRequestId} is handled by {@link CorrelationIdFilter} before the request gets here. */
    @Override
    public ResponseEntity<ClaimSubmissionResponse> submitClaim(ClaimSubmissionRequest request,
                                                               UUID idempotencyKey,
                                                               String xRequestId) {
        requestValidator.validate(request);
        SubmissionResult result = submitClaimUseCase.submit(
                new SubmitClaimCommand(ClaimApiMapper.toDetails(request), idempotencyKey));

        // A replay returns the original answer, marked so clients and logs can tell it apart.
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(ClaimApiMapper.toResponse(result.claim()));
    }
}
