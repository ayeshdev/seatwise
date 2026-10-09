package com.seatwise.accounts.internal;

import com.seatwise.common.security.StaffRole;
import java.time.Instant;
import java.util.UUID;

/** A staff account as the Admin screens see it; {@code version} goes back on the next PATCH. */
public record StaffAccountResponse(
        UUID id,
        String email,
        String fullName,
        StaffRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static StaffAccountResponse from(StaffAccountEntity e) {
        return new StaffAccountResponse(
                e.getId(),
                e.getEmail(),
                e.getFullName(),
                e.getRole(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }
}
