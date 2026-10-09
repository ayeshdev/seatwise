package com.seatwise.workshops.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/workshops} and {@code PUT /api/v1/workshops/{id}}.
 * The shape checks live here; the rules that need the clock or the database
 * (start in the future, unique code, location exists, capacity not below the
 * seats taken) are checked by {@link WorkshopService}. {@code version} is
 * required on PUT: it is the version the Manager loaded.
 */
public record WorkshopRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 120) String instructor,
        @NotNull UUID locationId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Min(1) @Max(500) Integer capacity,
        Long version) {}
