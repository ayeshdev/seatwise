package com.seatwise.accounts;

import java.time.Instant;
import java.util.UUID;

/**
 * A staff account was deactivated: its holder is refused from the next request
 * on and can no longer sign in. {@code actorId} is null only for system changes.
 */
public record StaffAccountDeactivated(UUID staffId, UUID actorId, Instant occurredAt) {}
