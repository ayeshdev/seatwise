package com.seatwise.accounts.internal;

import com.seatwise.common.security.StaffRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/staff-accounts/{id}}. Every field except
 * {@code version} is optional; a null field is left unchanged. {@code version}
 * is the one the client loaded, so a concurrent edit is refused instead of
 * silently overwritten.
 */
public record UpdateStaffAccountRequest(
        @Size(max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String fullName,
        StaffRole role,
        Boolean active,
        @NotNull Long version) {}
