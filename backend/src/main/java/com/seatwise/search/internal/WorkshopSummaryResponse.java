package com.seatwise.search.internal;

import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.WorkshopView;
import java.time.Instant;
import java.util.UUID;

/** The API's {@code WorkshopSummary} shape: one row of the workshop list. */
public record WorkshopSummaryResponse(
        UUID id,
        String code,
        String title,
        String instructor,
        LocationView location,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int seatsTaken,
        int seatsLeft,
        WorkshopStatus status) {

    static WorkshopSummaryResponse from(WorkshopView w) {
        return new WorkshopSummaryResponse(
                w.id(),
                w.code(),
                w.title(),
                w.instructor(),
                w.location(),
                w.startsAt(),
                w.endsAt(),
                w.capacity(),
                w.seatsTaken(),
                w.seatsLeft(),
                w.status());
    }
}
