package com.seatwise.audit.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One {@code audit_event} row. {@code id} is null until the row is inserted;
 * {@code actorId} is null when the system acted; {@code workshopId} is set for
 * WORKSHOP and REGISTRATION events.
 */
record AuditEventRow(
        Long id,
        Instant occurredAt,
        UUID actorId,
        AuditEntityType entityType,
        UUID entityId,
        UUID workshopId,
        AuditAction action,
        String summary,
        Map<String, AuditChange> changes) {

    AuditEventRow {
        changes = Map.copyOf(changes);
    }
}
