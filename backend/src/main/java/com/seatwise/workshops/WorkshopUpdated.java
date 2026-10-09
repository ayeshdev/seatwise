package com.seatwise.workshops;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A Manager edited a workshop. {@code changes} holds only the fields that
 * changed, keyed by API field name ({@code title}, {@code capacity},
 * {@code locationId}, ...), so the audit trail can record from/to directly.
 * Never published for a save that changed nothing.
 */
public record WorkshopUpdated(UUID workshopId, Map<String, FieldChange> changes, UUID actorId, Instant occurredAt) {

    public WorkshopUpdated {
        changes = Map.copyOf(changes);
    }
}
