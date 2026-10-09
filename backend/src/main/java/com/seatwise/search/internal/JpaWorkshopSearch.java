package com.seatwise.search.internal;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.web.PageResponse;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopQuery;
import com.seatwise.workshops.WorkshopView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Workshop search straight from PostgreSQL (architecture section 7a,
 * "Degraded mode"). The search module owns no tables, so it never touches the
 * workshops module's entities: it turns the staff-facing criteria into a
 * {@link WorkshopQuery} (local calendar days become UTC instants here, where
 * the centre timezone is known) and the workshops module's
 * {@link WorkshopCatalogue#search} applies the status predicates it owns.
 */
@Component
class JpaWorkshopSearch implements WorkshopSearch {

    private final WorkshopCatalogue catalogue;
    private final ZoneId centreZone;

    JpaWorkshopSearch(WorkshopCatalogue catalogue, SeatwiseProperties properties) {
        this.catalogue = catalogue;
        this.centreZone = Objects.requireNonNullElse(properties.centreTimezone(), ZoneId.of("Europe/London"));
    }

    @Override
    public WorkshopSearchResult search(WorkshopSearchCriteria criteria) {
        WorkshopQuery query = new WorkshopQuery(
                startOfDay(criteria.from()),
                // "to" is inclusive: everything before the next local midnight.
                criteria.to() == null ? null : startOfDay(criteria.to().plusDays(1)),
                criteria.statuses(),
                criteria.locationId(),
                criteria.hasSeats(),
                criteria.q(),
                criteria.page(),
                criteria.size(),
                criteria.sort());
        PageResponse<WorkshopView> page = catalogue.search(query);
        return new WorkshopSearchResult(
                page.items().stream().map(WorkshopSummaryResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalItems(),
                WorkshopSearchResult.FALLBACK);
    }

    // atStartOfDay(zone) also copes with a DST gap at midnight.
    private Instant startOfDay(LocalDate day) {
        return day == null ? null : day.atStartOfDay(centreZone).toInstant();
    }
}
