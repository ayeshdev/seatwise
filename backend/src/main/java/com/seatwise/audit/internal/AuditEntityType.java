package com.seatwise.audit.internal;

/**
 * What an audit event is about. Stored by name in {@code audit_event.entity_type}
 * (the CHECK lists the same values). Admins see STAFF_ACCOUNT events only;
 * Managers and Staff see WORKSHOP and REGISTRATION events only.
 */
public enum AuditEntityType {
    STAFF_ACCOUNT,
    WORKSHOP,
    REGISTRATION
}
