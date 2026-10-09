package com.seatwise.registrations.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/workshops/{id}/registrations}. Name and email are
 * trimmed on construction, so stray spaces typed at the desk don't fail the
 * email check or slip past the duplicate rule.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 120) String attendeeName,
        @NotBlank @Email @Size(max = 254) String attendeeEmail,
        Boolean joinWaitlistIfFull) {

    public RegisterRequest {
        attendeeName = attendeeName == null ? null : attendeeName.trim();
        attendeeEmail = attendeeEmail == null ? null : attendeeEmail.trim();
    }

    boolean waitlistIfFull() {
        return Boolean.TRUE.equals(joinWaitlistIfFull);
    }
}
