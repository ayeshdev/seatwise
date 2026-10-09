package com.seatwise.workshops;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only snapshot of a workshop for other modules. {@code status} was
 * derived with the clock at the moment the view was built; staff ids are raw
 * UUIDs, resolved through {@code StaffDirectory} by whoever needs names.
 */
public record WorkshopView(
        UUID id,
        String code,
        String title,
        String description,
        String instructor,
        LocationView location,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int seatsTaken,
        WorkshopLifecycle lifecycle,
        WorkshopStatus status,
        long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        Instant cancelledAt,
        UUID cancelledBy,
        String cancellationReason) {

    public int seatsLeft() {
        return Math.max(0, capacity - seatsTaken);
    }
}
