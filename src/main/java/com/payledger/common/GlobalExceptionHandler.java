package com.payledger.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that turns exceptions into RFC 7807 {@link ProblemDetail} JSON responses
 * ({@code application/problem+json} with type, title, status, detail).
 * Extending {@link ResponseEntityExceptionHandler} gives correct answers for Spring MVC's own
 * errors (bad JSON, wrong method, unknown URL, bad path variable, missing header) for free.
 * <pre>
 *   400 validation / bad input      401 not authenticated / bad login
 *   403 not allowed (security)      404 not found (or not yours)
 *   409 conflict / in progress      422 idempotency key reused, refund too large
 *   500 unexpected (no internals leaked, errorId logged)
 * </pre>
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ProblemDetail handleUnprocessable(UnprocessableEntityException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unprocessable request", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    /** Failed dashboard login. One generic message for every reason, so nothing is revealed. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }

    /**
     * Anything unexpected: a 500 with no internals (no stack trace, SQL or class names) and an
     * {@code errorId} that also appears in the log, so support can find the real cause.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled error {}", errorId, ex);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "Something went wrong on our side. Quote errorId " + errorId + " if you contact support.");
        pd.setProperty("errorId", errorId);
        return pd;
    }

    /** A database constraint caught a race that the service-level checks could not see. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    /** Bean Validation failures on a {@code @Valid @RequestBody}: list every invalid field and why. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return validationProblem(errors);
    }

    /**
     * Method validation failures. When a controller method has constraints on plain parameters
     * (such as {@code @NotBlank} on the Idempotency-Key header), Spring validates the whole method
     * at once and reports body fields and parameters together through this exception.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(e -> errors.putIfAbsent(name, e.getDefaultMessage()));
            }
        }
        return validationProblem(errors);
    }

    private static ResponseEntity<Object> validationProblem(Map<String, String> errors) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Uses the header name (e.g. "Idempotency-Key") for header parameters, else the Java name. */
    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
