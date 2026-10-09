package com.seatwise.common.security;

import org.springframework.security.core.AuthenticationException;

/**
 * The token is valid but there is no active staff account behind it. It is an
 * {@link AuthenticationException} so the resource-server filter stops the
 * request, but the entry point answers 403 ACCOUNT_INACTIVE rather than 401:
 * signing in again would not help, so the UI should say so.
 */
public class AccountInactiveException extends AuthenticationException {

    public AccountInactiveException(String message) {
        super(message);
    }
}
