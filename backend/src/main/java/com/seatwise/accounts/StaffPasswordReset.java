package com.seatwise.accounts;

import java.time.Instant;
import java.util.UUID;

/**
 * An Admin set a new temporary password. The password itself is deliberately
 * not part of the event, so it can never reach the audit trail or a log.
 */
public record StaffPasswordReset(UUID staffId, UUID actorId, Instant occurredAt) {}
