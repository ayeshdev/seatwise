package com.seatwise.common.error;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into an RFC 9457 {@code application/problem+json} body
 * with a stable {@code code} property. Extends Spring's handler so the
 * framework's own MVC exceptions (405, 415, type mismatches...) get the same
 * shape instead of falling through to the HTML/whitelabel error path.
 *
 * <p>Nothing internal is ever echoed back: no exception messages from the
 * persistence layer, no SQL, no stack traces. Those go to the log only.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    private record ConstraintMapping(ErrorCode code, String detail) {}

    // Constraint names are part of the schema contract (see the Flyway scripts):
    // the database is the last line of defence for these rules, and this map
    // turns its refusal into the same answer the service-level check would give.
    private static final Map<String, ConstraintMapping> CONSTRAINTS = Map.of(
            "uq_staff_account_email",
            new ConstraintMapping(ErrorCode.EMAIL_IN_USE, "Another staff account already uses that email address."),
            "uq_workshop_code",
            new ConstraintMapping(ErrorCode.VALIDATION_FAILED, "That workshop code is already used."),
            "uq_registration_live_attendee",
            new ConstraintMapping(
                    ErrorCode.DUPLICATE_REGISTRATION,
                    "This person already has a booking or waitlist place on this workshop."),
            "ck_workshop_seats_within_capacity",
            new ConstraintMapping(
                    ErrorCode.CAPACITY_BELOW_TAKEN, "Capacity can't be lower than the number of seats already taken."));

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        ProblemDetail problem = Problems.of(ex.code(), ex.detail());
        ex.properties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.code().status()).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        Optional<String> constraint = violatedConstraint(ex);
        ConstraintMapping mapping = constraint.map(CONSTRAINTS::get).orElse(null);
        if (mapping == null) {
            log.warn("Unmapped data integrity violation (constraint: {})", constraint.orElse("unknown"), ex);
            return problem(ErrorCode.CONFLICT, "That change conflicts with data that already exists.");
        }
        // A 400 code (duplicate workshop code) keeps its own status rather than 409.
        return problem(mapping.code(), mapping.detail());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(
                ErrorCode.STALE_VERSION,
                "Someone else changed this record after you opened it. Reload to see the latest version.");
    }

    // Method security throws AuthorizationDeniedException (a subclass) from inside
    // the controller call, so MVC sees it before the security filter chain does.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN, "Your role doesn't allow this action.");
    }

    // Normally the filter chain answers 401 before MVC runs; this covers the rare
    // case of method security finding no authentication at all.
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problem(ErrorCode.UNAUTHENTICATED, "Please sign in to continue.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.add(fieldError(e)));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errors.add(globalError(e)));
        return validationProblem(errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors().forEach(e -> errors.add(fieldError(e)));
                bodyErrors.getGlobalErrors().forEach(e -> errors.add(globalError(e)));
            } else {
                String name = parameterName(result.getMethodParameter());
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    errors.add(new FieldErrorItem(name, error.getDefaultMessage()));
                }
            }
        }
        return validationProblem(errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The parser message can quote internals (class names), so it is not echoed.
        ProblemDetail problem = Problems.of(
                ErrorCode.VALIDATION_FAILED, "The request body is missing or isn't valid JSON for this request.");
        problem.setProperty(Problems.ERRORS, List.of());
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .headers(headers)
                .body(Problems.of(ErrorCode.NOT_FOUND, "There is nothing at this address."));
    }

    // Every other framework exception still flows through here: make sure the
    // body carries a code so clients never have to special-case a missing one.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(statusCode);
        if (problem.getProperties() == null || !problem.getProperties().containsKey(Problems.CODE)) {
            ErrorCode code = fallbackCode(statusCode);
            problem.setProperty(Problems.CODE, code.name());
            if (statusCode.is5xxServerError()) {
                problem.setDetail("An unexpected error occurred. Please try again.");
            }
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static ErrorCode fallbackCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.REQUEST_NOT_SUPPORTED;
        };
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail) {
        return ResponseEntity.status(code.status()).body(Problems.of(code, detail));
    }

    private static ResponseEntity<Object> validationProblem(List<FieldErrorItem> errors, HttpHeaders headers) {
        ProblemDetail problem = Problems.of(ErrorCode.VALIDATION_FAILED, "Please check the highlighted fields.");
        problem.setProperty(Problems.ERRORS, errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    private static FieldErrorItem fieldError(FieldError error) {
        return new FieldErrorItem(error.getField(), error.getDefaultMessage());
    }

    private static FieldErrorItem globalError(ObjectError error) {
        return new FieldErrorItem(error.getObjectName(), error.getDefaultMessage());
    }

    // Report the name the client actually sent (?size=...), not the Java parameter name.
    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.name().isEmpty()) {
            return pathVariable.name();
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    /**
     * Walks the cause chain for the PostgreSQL error and returns the violated
     * constraint name. Works for JPA flushes and plain JDBC alike, because the
     * driver exception is always somewhere underneath Spring's translation.
     * Public so a service can turn a known constraint into a domain error while
     * it still has the context for a precise message.
     */
    public static Optional<String> violatedConstraint(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < 20) {
            if (current instanceof PSQLException psql) {
                ServerErrorMessage server = psql.getServerErrorMessage();
                if (server != null && server.getConstraint() != null) {
                    return Optional.of(server.getConstraint());
                }
            }
            if (current instanceof SQLException sql && sql.getNextException() != null
                    && sql.getNextException() != current.getCause()) {
                Optional<String> fromNext = violatedConstraint(sql.getNextException());
                if (fromNext.isPresent()) {
                    return fromNext;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return Optional.empty();
    }
}
