package com.seatwise.workshops.internal;

import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/workshops/{id}/cancel}; the body itself is optional. */
public record CancelWorkshopRequest(@Size(max = 500) String reason) {}
