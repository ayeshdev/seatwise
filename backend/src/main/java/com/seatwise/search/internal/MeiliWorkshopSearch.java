package com.seatwise.search.internal;

import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.MatchingStrategy;
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.meilisearch.sdk.model.Searchable;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.WorkshopView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Workshop search through the Meilisearch index (architecture section 7a,
 * "Query translation" and "Freshness and correctness"). The index decides
 * which workshops match and in what order; the page is then reloaded from
 * PostgreSQL by id, so seat counts, lifecycle and the derived status shown are
 * exact even when the index lags.
 *
 * <p>Split in two steps so {@link FallbackWorkshopSearch} can put its timeout
 * around the Meilisearch call only, not around the PostgreSQL hydration.
 */
class MeiliWorkshopSearch implements WorkshopSearch {

    private static final String SCHEDULED = "lifecycle = \"SCHEDULED\"";

    private final MeilisearchClients clients;
    private final WorkshopCatalogue catalogue;
    private final Clock clock;
    private final ZoneId centreZone;

    MeiliWorkshopSearch(MeilisearchClients clients, WorkshopCatalogue catalogue, Clock clock, ZoneId centreZone) {
        this.clients = clients;
        this.catalogue = catalogue;
        this.clock = clock;
        this.centreZone = centreZone;
    }

    /** The ids of one page of hits, in hit order, and the total number of hits. */
    record IndexHits(List<UUID> ids, long totalHits) {

        IndexHits {
            ids = List.copyOf(ids);
        }
    }

    @Override
    public WorkshopSearchResult search(WorkshopSearchCriteria criteria) {
        return hydrate(criteria, queryIndex(criteria));
    }

    /** One Meilisearch search; nothing else. */
    IndexHits queryIndex(WorkshopSearchCriteria criteria) {
        String filter = filter(criteria, clock.instant(), centreZone);
        SearchRequest request = SearchRequest.builder()
                .q(criteria.q())
                .filter(filter == null ? null : new String[] {filter})
                // Every word must match (typos allowed), like the fallback's
                // "contains": a code like POT-0412 must not fall back to
                // matching every POT-* workshop on the "pot" alone.
                .matchingStrategy(MatchingStrategy.ALL)
                // With the default ranking rules, sort comes after the
                // relevance rules: it decides the order when there's no q and
                // only breaks ties when there is one ("relevance first").
                .sort(new String[] {sort(criteria.sort())})
                .page(criteria.page() + 1)
                .hitsPerPage(criteria.size())
                .attributesToRetrieve(new String[] {WorkshopDocuments.PRIMARY_KEY})
                .build();
        Searchable result = clients.runtime().index(WorkshopDocuments.INDEX).search(request);
        List<UUID> ids = result.getHits().stream()
                .map(hit -> UUID.fromString(String.valueOf(hit.get(WorkshopDocuments.PRIMARY_KEY))))
                .toList();
        long total = switch (result) {
            case SearchResultPaginated paginated -> paginated.getTotalHits();
            case SearchResult offsetBased -> offsetBased.getEstimatedTotalHits();
            default -> ids.size();
        };
        return new IndexHits(ids, total);
    }

    /**
     * Reloads the page from PostgreSQL, keeping the index's order. Ids the
     * catalogue no longer knows are dropped (and taken off the total).
     */
    WorkshopSearchResult hydrate(WorkshopSearchCriteria criteria, IndexHits hits) {
        Map<UUID, WorkshopView> byId = catalogue.findAll(hits.ids()).stream()
                .collect(Collectors.toMap(WorkshopView::id, Function.identity()));
        List<WorkshopSummaryResponse> items = hits.ids().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(WorkshopSummaryResponse::from)
                .toList();
        long dropped = hits.ids().size() - items.size();
        return new WorkshopSearchResult(items, criteria.page(), criteria.size(),
                Math.max(0, hits.totalHits() - dropped), WorkshopSearchResult.INDEX);
    }

    /**
     * The criteria as one Meilisearch filter expression, or null for none. The
     * derived statuses use the same rules as {@link WorkshopStatus#derive},
     * evaluated against {@code now}; calendar days are local midnights in the
     * centre timezone.
     */
    static String filter(WorkshopSearchCriteria criteria, Instant now, ZoneId zone) {
        long nowSeconds = now.getEpochSecond();
        List<String> all = new ArrayList<>();
        if (criteria.from() != null) {
            all.add("startsAt >= " + startOfDay(criteria.from(), zone));
        }
        if (criteria.to() != null) {
            // "to" is inclusive: everything before the next local midnight.
            all.add("startsAt < " + startOfDay(criteria.to().plusDays(1), zone));
        }
        if (!criteria.statuses().isEmpty()) {
            // Enum order, so the same criteria always build the same string.
            all.add(criteria.statuses().stream()
                    .sorted()
                    .map(status -> "(" + status(status, nowSeconds) + ")")
                    .collect(Collectors.joining(" OR ", "(", ")")));
        }
        if (criteria.locationId() != null) {
            all.add("locationId = \"" + criteria.locationId() + "\"");
        }
        if (criteria.hasSeats()) {
            // Same as OPEN: bookable right now with a seat left.
            all.add("(" + status(WorkshopStatus.OPEN, nowSeconds) + ")");
        }
        return all.isEmpty() ? null : String.join(" AND ", all);
    }

    private static String status(WorkshopStatus status, long now) {
        return switch (status) {
            case OPEN -> SCHEDULED + " AND startsAt > " + now + " AND seatsLeft > 0";
            case FULL -> SCHEDULED + " AND startsAt > " + now + " AND seatsLeft = 0";
            case IN_PROGRESS -> SCHEDULED + " AND startsAt <= " + now + " AND endsAt > " + now;
            case COMPLETED -> SCHEDULED + " AND endsAt <= " + now;
            case CANCELLED -> "lifecycle = \"CANCELLED\"";
        };
    }

    private static String sort(WorkshopSort sort) {
        return switch (sort) {
            case STARTS_AT_ASC -> "startsAt:asc";
            case STARTS_AT_DESC -> "startsAt:desc";
            case TITLE_ASC -> "title:asc";
            case TITLE_DESC -> "title:desc";
        };
    }

    // atStartOfDay(zone) also copes with a DST gap at midnight.
    private static long startOfDay(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toEpochSecond();
    }
}
