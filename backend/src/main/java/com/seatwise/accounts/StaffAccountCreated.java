package com.seatwise.accounts;

import com.seatwise.common.security.StaffRole;
import java.time.Instant;
import java.util.UUID;

/**
 * A staff account was created. {@code actorId} is the Admin who created it,
 * or {@code null} when the system did (first-Admin bootstrap, demo seeding).
 */
public record StaffAccountCreated(
        UUID staffId, String email, String fullName, StaffRole role, UUID actorId, Instant occurredAt) {}
