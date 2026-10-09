package com.seatwise.accounts.internal;

import com.seatwise.common.security.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/staff-accounts}. The password is temporary:
 * Keycloak asks the new user to replace it at first sign-in.
 */
public record CreateStaffAccountRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull StaffRole role,
        @NotBlank @Size(min = 10, max = 128) String temporaryPassword) {

    // Keeps the password out of logs if a request is ever printed.
    @Override
    public String toString() {
        return "CreateStaffAccountRequest[email=" + email + ", fullName=" + fullName + ", role=" + role + "]";
    }
}
