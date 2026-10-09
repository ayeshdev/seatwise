package com.seatwise.registrations;

import java.time.Instant;
import java.util.UUID;

/**
 * The first person on a workshop's waitlist got a seat (WAITLISTED to ACTIVE).
 * {@code freedByRegistrationId} is the cancelled registration whose seat passed
 * to them, or null when the seat came from a capacity increase. {@code actorId}
 * is the staff member whose action triggered the promotion. {@code attendeeName}
 * names the person in the audit trail.
 */
public record WaitlistPromoted(
        UUID registrationId,
        UUID workshopId,
        String attendeeName,
        UUID freedByRegistrationId,
        UUID actorId,
        Instant occurredAt) {}
