package com.seatwise.workshops.internal;

import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.WorkshopView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Entity to {@link WorkshopView}, with the status derived against one given "now". */
@Component
class WorkshopViews {

    private final LocationRepository locations;

    WorkshopViews(LocationRepository locations) {
        this.locations = locations;
    }

    WorkshopView toView(WorkshopEntity workshop, Instant now) {
        return toViews(List.of(workshop), now).getFirst();
    }

    List<WorkshopView> toViews(List<WorkshopEntity> workshops, Instant now) {
        if (workshops.isEmpty()) {
            return List.of();
        }
        // Three rows in total, so loading them all beats a join per workshop.
        Map<UUID, LocationView> byId = locations.findAll().stream()
                .collect(Collectors.toMap(LocationEntity::getId, LocationEntity::toView));
        return workshops.stream().map(w -> toView(w, byId, now)).toList();
    }

    private static WorkshopView toView(WorkshopEntity w, Map<UUID, LocationView> locations, Instant now) {
        return new WorkshopView(
                w.getId(),
                w.getCode(),
                w.getTitle(),
                w.getDescription(),
                w.getInstructor(),
                locations.getOrDefault(w.getLocationId(), new LocationView(w.getLocationId(), "Unknown location")),
                w.getStartsAt(),
                w.getEndsAt(),
                w.getCapacity(),
                w.getSeatsTaken(),
                w.getLifecycle(),
                WorkshopStatus.derive(
                        w.getLifecycle(), w.getStartsAt(), w.getEndsAt(), w.getCapacity(), w.getSeatsTaken(), now),
                w.getVersion(),
                w.getCreatedAt(),
                w.getCreatedBy(),
                w.getUpdatedAt(),
                w.getUpdatedBy(),
                w.getCancelledAt(),
                w.getCancelledBy(),
                w.getCancellationReason());
    }
}
