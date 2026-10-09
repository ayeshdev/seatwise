package com.seatwise.registrations;

/**
 * Stored by name in {@code registration.status} (the CHECK lists the same
 * values). ACTIVE holds a seat; WAITLISTED waits for one; CANCELLED is history.
 */
public enum RegistrationStatus {
    ACTIVE,
    WAITLISTED,
    CANCELLED
}
