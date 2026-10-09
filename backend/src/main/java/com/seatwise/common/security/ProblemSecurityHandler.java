package com.seatwise.common.security;

import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes problem+json for refusals made by the filter chain, which runs before
 * MVC and therefore before {@code ProblemDetailsAdvice}. Without this the
 * client would get an empty 401 body and no {@code code} to switch on.
 */
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public ProblemSecurityHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        if (ex instanceof AccountInactiveException) {
            write(request, response, ErrorCode.ACCOUNT_INACTIVE,
                    "Your account is not active. Ask an administrator if you need access.");
            return;
        }
        // RFC 6750: a 401 for a bearer-protected resource names the scheme.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, ErrorCode.UNAUTHENTICATED, "Please sign in to continue.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ErrorCode.FORBIDDEN, "Your role doesn't allow this action.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        ProblemDetail problem = Problems.of(code, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Boot's JsonMapper carries the ProblemDetail mixin, so properties
        // such as "code" are written at the top level like MVC does.
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
