package com.seatwise.audit.internal;

/**
 * The kind of change, stored by name in {@code audit_event.action}. The names are
 * part of the API contract (architecture section 9, {@code AuditEvent.action}).
 */
public enum AuditAction {
    CREATED,
    UPDATED,
    CANCELLED,
    ROLE_CHANGED,
    RENAMED,
    DEACTIVATED,
    REACTIVATED,
    PASSWORD_RESET,
    REGISTERED,
    WAITLISTED,
    PROMOTED
}
