package com.seatwise.audit.internal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The filters of {@code GET /api/v1/audit-events}, all optional except paging.
 * {@code from} and {@code to} are days in the centre's timezone, both inclusive.
 * Which entity types a caller may ask for depends on their role; that is
 * enforced by {@link AuditQueryService}, not here.
 */
public record AuditQuery(
        AuditEntityType entityType,
        UUID entityId,
        UUID workshopId,
        LocalDate from,
        LocalDate to,
        int page,
        int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
}
