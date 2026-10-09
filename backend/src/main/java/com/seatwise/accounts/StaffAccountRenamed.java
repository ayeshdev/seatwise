package com.seatwise.accounts;

import java.time.Instant;
import java.util.UUID;

/** A staff member's display name changed. {@code actorId} is null only for system changes. */
public record StaffAccountRenamed(
        UUID staffId, String fromFullName, String toFullName, UUID actorId, Instant occurredAt) {}
