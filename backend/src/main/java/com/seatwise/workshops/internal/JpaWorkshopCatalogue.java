package com.seatwise.workshops.internal;

import com.seatwise.common.web.PageResponse;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopQuery;
import com.seatwise.workshops.WorkshopView;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class JpaWorkshopCatalogue implements WorkshopCatalogue {

    private final WorkshopRepository repository;
    private final LocationRepository locations;
    private final WorkshopViews views;
    private final Clock clock;

    JpaWorkshopCatalogue(
            WorkshopRepository repository, LocationRepository locations, WorkshopViews views, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.views = views;
        this.clock = clock;
    }

    @Override
    public Optional<WorkshopView> find(UUID id) {
        return repository.findById(id).map(w -> views.toView(w, clock.instant()));
    }

    @Override
    public List<WorkshopView> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return views.toViews(repository.findAllById(ids), clock.instant());
    }

    @Override
    public PageResponse<WorkshopView> search(WorkshopQuery query) {
        // One "now" for both the status predicates and the derived status of
        // each row, so a row can't match OPEN and then be shown as IN_PROGRESS.
        Instant now = clock.instant();
        Page<WorkshopEntity> page = repository.findAll(
                WorkshopSpecifications.matching(query, now),
                PageRequest.of(query.page(), query.size(), WorkshopSpecifications.sort(query.sort())));
        List<WorkshopView> items = views.toViews(page.getContent(), now);
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    public Optional<LocationView> findLocation(UUID id) {
        return locations.findById(id).map(LocationEntity::toView);
    }
}
