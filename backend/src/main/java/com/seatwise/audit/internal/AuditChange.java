package com.seatwise.audit.internal;

/**
 * One changed field of an audit event, as stored in {@code audit_event.changes}
 * and returned by the API. Values are JSON scalars: strings (ISO-8601 for
 * instants, enum names, UUIDs), numbers or booleans; {@code null} means "not set".
 */
public record AuditChange(Object from, Object to) {}
