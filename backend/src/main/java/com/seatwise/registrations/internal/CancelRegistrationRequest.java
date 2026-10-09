package com.seatwise.registrations.internal;

import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/registrations/{id}/cancel}; the body itself is optional. */
public record CancelRegistrationRequest(@Size(max = 500) String reason) {}
