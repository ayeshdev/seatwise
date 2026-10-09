package com.seatwise.common.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Authentication could not be decided because something Seatwise depends on
 * (the database) failed. Answered with 503, not 401/403: the caller did
 * nothing wrong and should simply retry.
 *
 * <p>Deliberately not an {@code AuthenticationServiceException}: the
 * resource-server filter rethrows that type instead of passing it to the entry
 * point, which would turn the outage into an unformatted 500.
 */
public class AuthenticationUnavailableException extends AuthenticationException {

    public AuthenticationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
