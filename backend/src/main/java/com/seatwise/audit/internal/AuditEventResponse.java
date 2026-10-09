package com.seatwise.audit.internal;

import com.seatwise.accounts.StaffRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The API's {@code AuditEvent} shape (architecture section 9). {@code actor} is
 * null when the system acted; {@code changes} is an empty object when no field
 * applies (a password reset).
 */
public record AuditEventResponse(
        long id,
        Instant occurredAt,
        StaffRef actor,
        AuditEntityType entityType,
        UUID entityId,
        UUID workshopId,
        AuditAction action,
        String summary,
        Map<String, AuditChange> changes) {

    public AuditEventResponse {
        changes = Map.copyOf(changes);
    }
}
