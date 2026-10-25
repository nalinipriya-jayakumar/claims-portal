package com.redrivermutual.claims.adapter.in.web;

import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionRequest;
import com.redrivermutual.claims.domain.DateOfLossPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks a submission against everything the contract demands, in one pass, so the customer sees
 * every problem at once:
 * <ul>
 *   <li>the constraints generated from the OpenAPI schema (Bean Validation on the generated model);</li>
 *   <li>the two rules the schema states in prose: the date-of-loss window (A-03) and consent must be true.</li>
 * </ul>
 * Each constraint becomes the contract's error {@link ErrorCode}, then one error per field is kept.
 */
@Component
public class ClaimRequestValidator {

    private final Validator validator;
    private final DateOfLossPolicy dateOfLossPolicy;

    public ClaimRequestValidator(Validator validator, DateOfLossPolicy dateOfLossPolicy) {
        this.validator = validator;
        this.dateOfLossPolicy = dateOfLossPolicy;
    }

    /** @throws ClaimValidationException listing every problem, if there is any */
    public void validate(ClaimSubmissionRequest request) {
        List<FieldViolation> violations = violationsOf(request);
        if (!violations.isEmpty()) {
            throw new ClaimValidationException(violations);
        }
    }

    /** All problems with the request, one per field, ordered by field name. */
    public List<FieldViolation> violationsOf(ClaimSubmissionRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ConstraintViolation<ClaimSubmissionRequest> violation : validator.validate(request)) {
            String field = violation.getPropertyPath().toString();
            violations.add(violation(field, codeFor(violation)));
        }
        if (request.getDateOfLoss() != null) {
            dateOfLossPolicy.check(request.getDateOfLoss()).ifPresent(violation ->
                    violations.add(FieldViolation.forDateOfLoss(violation, dateOfLossPolicy.maxAgeDays())));
        }
        if (Boolean.FALSE.equals(request.getConsentAcknowledged())) {
            violations.add(violation("consentAcknowledged", ErrorCode.MUST_BE_TRUE));
        }
        return mostImportantPerField(violations);
    }

    private static FieldViolation violation(String field, ErrorCode code) {
        return new FieldViolation(field, code, FieldMessages.messageFor(field, code));
    }

    private static ErrorCode codeFor(ConstraintViolation<?> violation) {
        Annotation constraint = violation.getConstraintDescriptor().getAnnotation();
        return switch (constraint) {
            case NotNull ignored -> ErrorCode.REQUIRED;
            case Pattern ignored -> ErrorCode.INVALID_FORMAT;
            case Email ignored -> ErrorCode.INVALID_FORMAT;
            case Size size -> isShorterThanMin(violation.getInvalidValue(), size)
                    ? ErrorCode.TOO_SHORT
                    : ErrorCode.TOO_LONG;
            default -> ErrorCode.INVALID_FORMAT;
        };
    }

    private static boolean isShorterThanMin(Object value, Size size) {
        return value instanceof CharSequence text && text.length() < size.min();
    }

    /** A field can break several rules; the customer needs only the most fundamental one. */
    private static List<FieldViolation> mostImportantPerField(List<FieldViolation> violations) {
        Map<String, FieldViolation> byField = new TreeMap<>();
        Comparator<FieldViolation> byPriority = Comparator.comparing(FieldViolation::code);
        for (FieldViolation violation : violations) {
            byField.merge(violation.field(), violation,
                    (current, candidate) -> byPriority.compare(candidate, current) < 0 ? candidate : current);
        }
        return List.copyOf(byField.values());
    }
}
