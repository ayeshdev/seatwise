package com.seatwise.workshops.internal;

import java.time.Instant;
import java.util.UUID;

/** The editable fields of a workshop after validation and normalization (code trimmed and upper-cased). */
public record WorkshopDetails(
        String code,
        String title,
        String description,
        String instructor,
        UUID locationId,
        Instant startsAt,
        Instant endsAt,
        int capacity) {}
