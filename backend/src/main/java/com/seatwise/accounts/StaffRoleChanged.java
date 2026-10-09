package com.seatwise.accounts;

import com.seatwise.common.security.StaffRole;
import java.time.Instant;
import java.util.UUID;

/**
 * A staff member's role changed; it applies on their next request because the
 * role is read from the database, not the token. {@code actorId} is null only
 * for system changes.
 */
public record StaffRoleChanged(UUID staffId, StaffRole from, StaffRole to, UUID actorId, Instant occurredAt) {}
