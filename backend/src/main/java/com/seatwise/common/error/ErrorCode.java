package com.seatwise.common.error;

import org.springframework.http.HttpStatus;

/**
 * The stable, machine-readable error catalogue (architecture section 9). The
 * enum name is sent as the {@code code} property of every problem response, so
 * the frontend can switch on it; the title is plain language the UI may show
 * as-is. Renaming a constant is a breaking API change.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some details need fixing"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Please sign in"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to do that"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "This account is not active"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "We couldn't find that"),
    WORKSHOP_FULL(HttpStatus.CONFLICT, "This workshop is full"),
    WORKSHOP_NOT_OPEN(HttpStatus.CONFLICT, "This workshop isn't open for bookings"),
    DUPLICATE_REGISTRATION(HttpStatus.CONFLICT, "This person is already booked on this workshop"),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "This booking was already cancelled"),
    CAPACITY_BELOW_TAKEN(HttpStatus.CONFLICT, "Capacity can't be lower than the seats already taken"),
    STALE_VERSION(HttpStatus.CONFLICT, "Someone else changed this while you were editing"),
    EMAIL_IN_USE(HttpStatus.CONFLICT, "That email address is already in use"),
    LAST_ADMIN(HttpStatus.CONFLICT, "There must always be at least one active Admin"),
    SELF_MODIFICATION(HttpStatus.CONFLICT, "You can't change your own role or deactivate yourself"),
    IDENTITY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The sign-in service is unavailable right now"),

    // Fallbacks so that every problem response carries a code, even for
    // failures that don't map to a business rule.
    CONFLICT(HttpStatus.CONFLICT, "That change conflicts with existing data"),
    REQUEST_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "This request isn't supported"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Seatwise is temporarily unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
