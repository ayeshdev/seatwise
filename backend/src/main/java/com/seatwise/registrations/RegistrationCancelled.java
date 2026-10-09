package com.seatwise.registrations;

import java.time.Instant;
import java.util.UUID;

/**
 * A registration was cancelled. {@code previousStatus} says whether a seat was
 * freed (ACTIVE) or a waitlist place was given up (WAITLISTED). The row is kept.
 */
public record RegistrationCancelled(
        UUID registrationId,
        UUID workshopId,
        RegistrationStatus previousStatus,
        String reason,
        UUID actorId,
        Instant occurredAt) {}
