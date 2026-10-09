package com.seatwise.search.internal;

/**
 * Finding workshops (FR-FIND). Implementations: {@link JpaWorkshopSearch}
 * (PostgreSQL; the fallback and the reference) and, later, a Meilisearch one
 * behind the same interface that hydrates seat counts from the catalogue.
 */
public interface WorkshopSearch {

    WorkshopSearchResult search(WorkshopSearchCriteria criteria);
}
