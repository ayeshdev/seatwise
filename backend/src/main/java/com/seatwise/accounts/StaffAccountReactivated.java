package com.seatwise.accounts;

import java.time.Instant;
import java.util.UUID;

/** A deactivated staff account was made active again. {@code actorId} is null only for system changes. */
public record StaffAccountReactivated(UUID staffId, UUID actorId, Instant occurredAt) {}
