package com.redrivermutual.claims.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.redrivermutual.claims.adapter.in.web.model.ClaimSubmissionRequest;
import com.redrivermutual.claims.adapter.in.web.model.FieldError;
import com.redrivermutual.claims.domain.InvalidDateOfLossException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/**
 * Turns every failure into an RFC 9457 problem with a correlation ID. Responses never contain
 * submitted values, exception messages or stack traces (NFR-06, NFR-07).
 *
 * <p>Standard Spring MVC errors such as 415 Unsupported Media Type come from the parent class;
 * {@link #handleExceptionInternal} adds the correlation ID to those as well.
 *
 * <p>Field errors are written with {@link FieldError}, the model generated from the contract, so
 * an error code the contract does not define cannot be sent.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID = "correlationId";

    private final ClaimRequestValidator requestValidator;

    GlobalExceptionHandler(ClaimRequestValidator requestValidator) {
        this.requestValidator = requestValidator;
    }

    @ExceptionHandler(ClaimValidationException.class)
    ProblemDetail handleValidation(ClaimValidationException ex) {
        return validationProblem(ex.violations());
    }

    @ExceptionHandler(InvalidDateOfLossException.class)
    ProblemDetail handleInvalidDateOfLoss(InvalidDateOfLossException ex) {
        return validationProblem(List.of(FieldViolation.forDateOfLoss(ex.violation(), ex.maxAgeDays())));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling request", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(ProblemTypes.INTERNAL_ERROR);
        problem.setTitle("Something went wrong");
        problem.setDetail("Please try again. If the problem continues, contact us and quote the reference below.");
        problem.setProperty(CORRELATION_ID, CorrelationIdFilter.currentCorrelationId());
        return problem;
    }

    /**
     * The generated {@code ClaimsApi} declares {@code @Valid}, so Spring checks the schema constraints
     * before the controller runs. Re-check the whole request here so the response also includes the
     * rules stated in prose (date window, consent) and every field is reported at once.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        if (ex.getBindingResult().getTarget() instanceof ClaimSubmissionRequest submission) {
            List<FieldViolation> violations = requestValidator.violationsOf(submission);
            if (!violations.isEmpty()) {
                return handleExceptionInternal(ex, validationProblem(violations), headers, HttpStatus.BAD_REQUEST, request);
            }
        }
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /** Unreadable JSON: report the field Jackson stopped at, without the value it rejected. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = switch (ex.getCause()) {
            case UnrecognizedPropertyException e -> fieldProblem(e, ErrorCode.UNKNOWN_FIELD,
                    "This field is not recognised.");
            case InvalidFormatException e when e.getTargetType() != null && e.getTargetType().isEnum() ->
                    fieldProblem(e, ErrorCode.NOT_ALLOWED_VALUE, "Select one of the listed options.");
            // Generated contract enums reject unknown values in their @JsonCreator.
            case ValueInstantiationException e when e.getType() != null && e.getType().isEnumType() ->
                    fieldProblem(e, ErrorCode.NOT_ALLOWED_VALUE, "Select one of the listed options.");
            case MismatchedInputException e -> fieldProblem(e, ErrorCode.INVALID_FORMAT,
                    "This value has the wrong format.");
            case JsonProcessingException ignored -> malformedProblem(ErrorCode.INVALID_FORMAT,
                    "Request body is not valid JSON.");
            case null, default -> malformedProblem(ErrorCode.REQUIRED, "Request body is required.");
        };
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty(CORRELATION_ID, CorrelationIdFilter.currentCorrelationId());
        }
        return response;
    }

    private static ProblemDetail validationProblem(List<FieldViolation> violations) {
        ProblemDetail problem = problem(ProblemTypes.VALIDATION_ERROR, "Invalid claim submission", violations);
        problem.setDetail(violations.size() == 1
                ? "1 field needs attention."
                : violations.size() + " fields need attention.");
        problem.setProperty(CORRELATION_ID, CorrelationIdFilter.currentCorrelationId());
        return problem;
    }

    private static ProblemDetail fieldProblem(JsonMappingException ex, ErrorCode code, String message) {
        return problem(ProblemTypes.VALIDATION_ERROR, "Invalid claim submission",
                List.of(new FieldViolation(jsonPath(ex), code, message)));
    }

    private static ProblemDetail malformedProblem(ErrorCode code, String message) {
        return problem(ProblemTypes.MALFORMED_REQUEST, "Request body could not be read",
                List.of(new FieldViolation("$", code, message)));
    }

    private static ProblemDetail problem(URI type, String title, List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(type);
        problem.setTitle(title);
        problem.setProperty("errors", errors.stream().map(GlobalExceptionHandler::toContract).toList());
        return problem;
    }

    private static FieldError toContract(FieldViolation violation) {
        return new FieldError(violation.field(), FieldError.CodeEnum.fromValue(violation.code().name()),
                violation.message());
    }

    /** Dot-separated path such as {@code customer.email}; {@code $} for the whole body. */
    private static String jsonPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : ex.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "$" : path.toString();
    }
}
