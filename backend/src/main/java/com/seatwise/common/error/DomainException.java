package com.seatwise.common.error;

import java.util.Map;

/**
 * A business-rule failure with a stable {@link ErrorCode}. Services throw it
 * without knowing about HTTP; {@link ProblemDetailsAdvice} turns it into an
 * RFC 9457 response. {@code detail} is user-facing text, so it must never
 * contain SQL, stack traces or other internals.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> properties;

    public DomainException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    /** {@code properties} are added to the problem body, e.g. the current version on a stale edit. */
    public DomainException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return getMessage();
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
