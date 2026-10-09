package com.seatwise.search.internal;

/**
 * Finding workshops (FR-FIND). Implementations: {@link JpaWorkshopSearch}
 * (PostgreSQL; the fallback and the reference), {@link MeiliWorkshopSearch}
 * (the index, hydrated from the catalogue) and {@link FallbackWorkshopSearch},
 * the primary one, which picks between them per request.
 */
public interface WorkshopSearch {

    WorkshopSearchResult search(WorkshopSearchCriteria criteria);
}
