package com.seatwise.registrations.internal;

/** Response of a cancellation: the cancelled row, plus whoever got the seat from the waitlist (or null). */
public record CancelResult(RegistrationResponse cancelled, RegistrationResponse promoted) {}
