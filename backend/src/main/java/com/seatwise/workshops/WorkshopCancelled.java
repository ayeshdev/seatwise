package com.seatwise.workshops;

import java.time.Instant;
import java.util.UUID;

/**
 * A Manager cancelled a workshop: no new bookings; existing registrations are kept as they are (A-6).
 * {@code code} and {@code title} name the workshop in the audit trail.
 */
public record WorkshopCancelled(
        UUID workshopId, String code, String title, String reason, UUID actorId, Instant occurredAt) {}
