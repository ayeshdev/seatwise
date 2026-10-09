package com.seatwise.workshops.internal;

import com.seatwise.accounts.StaffRef;
import com.seatwise.accounts.StaffSummary;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.WorkshopView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The API's {@code Workshop} shape (architecture section 9, "Payload shapes"). */
public record WorkshopResponse(
        UUID id,
        String code,
        String title,
        String description,
        String instructor,
        LocationView location,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int seatsTaken,
        int seatsLeft,
        long waitlistCount,
        WorkshopStatus status,
        long version,
        Instant createdAt,
        StaffRef createdBy,
        Instant updatedAt,
        StaffRef updatedBy) {

    static WorkshopResponse from(WorkshopView w, long waitlistCount, Map<UUID, StaffSummary> staff) {
        return new WorkshopResponse(
                w.id(),
                w.code(),
                w.title(),
                w.description(),
                w.instructor(),
                w.location(),
                w.startsAt(),
                w.endsAt(),
                w.capacity(),
                w.seatsTaken(),
                w.seatsLeft(),
                waitlistCount,
                w.status(),
                w.version(),
                w.createdAt(),
                StaffRef.resolve(w.createdBy(), staff),
                w.updatedAt(),
                StaffRef.resolve(w.updatedBy(), staff));
    }
}
