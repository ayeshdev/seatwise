package com.seatwise.workshops;

import java.time.Instant;
import java.util.UUID;

/** A Manager scheduled a new workshop. Carries the initial values so the audit trail can show them. */
public record WorkshopScheduled(
        UUID workshopId,
        String code,
        String title,
        String instructor,
        UUID locationId,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        UUID actorId,
        Instant occurredAt) {}
