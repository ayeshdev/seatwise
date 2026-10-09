package com.seatwise.accounts.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/staff-accounts/{id}/password-reset}. */
public record PasswordResetRequest(@NotBlank @Size(min = 10, max = 128) String temporaryPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[temporaryPassword=***]";
    }
}
