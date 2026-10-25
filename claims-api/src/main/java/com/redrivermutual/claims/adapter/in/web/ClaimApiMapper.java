package com.redrivermutual.claims.adapter.in.web;

import com.redrivermutual.claims.adapter.in.web.model.ClaimStatus;
import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionRequest;
import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionResponse;
import com.redrivermutual.claims.domain.Claim;
import com.redrivermutual.claims.domain.ClaimDetails;
import com.redrivermutual.claims.domain.Customer;
import com.redrivermutual.claims.domain.LossType;
import com.redrivermutual.claims.domain.PropertyAddress;
import com.redrivermutual.claims.domain.Province;

import java.time.ZoneOffset;

/**
 * Translates between the models generated from the OpenAPI contract and the domain. The domain
 * never depends on generated code, so a contract change is absorbed here and nowhere else.
 */
final class ClaimApiMapper {

    /** The claim is accepted, not yet emailed: the wording reflects that delivery is asynchronous. */
    static final String ACCEPTED_MESSAGE =
            "Your claim has been received. A confirmation email will be sent to you shortly.";

    private ClaimApiMapper() {
    }

    /** Only call after the request has passed validation. */
    static ClaimDetails toDetails(ClaimSubmissionRequest request) {
        var customer = request.getCustomer();
        var address = request.getPropertyAddress();
        return new ClaimDetails(
                new Customer(customer.getFirstName(), customer.getLastName(), customer.getEmail(), customer.getPhone()),
                request.getPolicyNumber(),
                request.getDateOfLoss(),
                LossType.valueOf(request.getLossType().getValue()),
                new PropertyAddress(address.getStreet(), address.getCity(),
                        Province.valueOf(address.getProvince().getValue()), address.getPostalCode()),
                request.getDescription());
    }

    static ClaimSubmissionResponse toResponse(Claim claim) {
        return new ClaimSubmissionResponse(
                        claim.claimNumber().value(),
                        ClaimStatus.fromValue(claim.status().name()),
                        claim.submittedAt().atOffset(ZoneOffset.UTC))
                .message(ACCEPTED_MESSAGE);
    }
}
