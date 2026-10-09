package com.seatwise.workshops;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A catalogue query with every filter already resolved to stored values:
 * calendar dates are turned into instants by the caller (who knows the centre
 * timezone), while derived statuses are turned into predicates by the
 * catalogue (which owns the status rules), evaluated against one "now".
 *
 * @param startsFrom inclusive lower bound on {@code startsAt}, or null
 * @param startsBefore exclusive upper bound on {@code startsAt}, or null
 * @param statuses any of these (OR); empty means every status
 * @param locationId only this location, or null
 * @param hasSeats only workshops that can still be booked and have a seat left
 * @param text case-insensitive "contains" on code, title, instructor and location name, or null
 * @param page zero-based
 * @param size page size, 1..100
 */
public record WorkshopQuery(
        Instant startsFrom,
        Instant startsBefore,
        Set<WorkshopStatus> statuses,
        UUID locationId,
        boolean hasSeats,
        String text,
        int page,
        int size,
        WorkshopSort sort) {

    public WorkshopQuery {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        sort = sort == null ? WorkshopSort.STARTS_AT_ASC : sort;
    }

    /** Result order; ties are always broken by id so paging is stable. */
    public enum WorkshopSort {
        STARTS_AT_ASC,
        STARTS_AT_DESC,
        TITLE_ASC,
        TITLE_DESC
    }
}
