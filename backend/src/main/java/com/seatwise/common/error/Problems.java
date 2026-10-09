package com.seatwise.common.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds the one problem shape the API uses everywhere: the MVC advice and the
 * security filter chain (which runs before MVC) share it so a 401 looks like a
 * 409 to the client.
 */
public final class Problems {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        return of(code, code.status(), detail);
    }

    /** For fallbacks where the HTTP status comes from Spring rather than from the code. */
    public static ProblemDetail of(ErrorCode code, HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code.title());
        problem.setProperty(CODE, code.name());
        return problem;
    }
}
