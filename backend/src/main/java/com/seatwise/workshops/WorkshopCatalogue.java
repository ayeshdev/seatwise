package com.seatwise.workshops;

import com.seatwise.common.web.PageResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The workshops module's read API for other modules (registrations, search).
 * It never exposes the entity: callers get immutable {@link WorkshopView}s
 * read from the committed database state, with the status derived at read time.
 */
public interface WorkshopCatalogue {

    Optional<WorkshopView> find(UUID id);

    /** Batch lookup, e.g. to hydrate a page of search hits. Unknown ids are simply missing. */
    List<WorkshopView> findAll(Collection<UUID> ids);

    /** The PostgreSQL implementation of workshop search (filters, paging, sort). */
    PageResponse<WorkshopView> search(WorkshopQuery query);

    /** Any location by id, active or not, e.g. to name the old location of an edit in the audit trail. */
    Optional<LocationView> findLocation(UUID id);
}
