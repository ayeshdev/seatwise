package com.seatwise.workshops.internal;

import com.seatwise.workshops.WorkshopLifecycle;
import com.seatwise.workshops.WorkshopQuery;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * The search filters as SQL predicates (architecture section 7a, "Query
 * translation"). The status predicates are the SQL twin of
 * {@link WorkshopStatus#derive}; both live in this module so they can't drift.
 */
final class WorkshopSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private WorkshopSpecifications() {}

    static Specification<WorkshopEntity> matching(WorkshopQuery query, Instant now) {
        return (root, criteria, cb) -> {
            List<Predicate> all = new ArrayList<>();
            Expression<Instant> startsAt = root.get("startsAt");
            if (query.startsFrom() != null) {
                all.add(cb.greaterThanOrEqualTo(startsAt, query.startsFrom()));
            }
            if (query.startsBefore() != null) {
                all.add(cb.lessThan(startsAt, query.startsBefore()));
            }
            if (!query.statuses().isEmpty()) {
                all.add(cb.or(query.statuses().stream()
                        .map(status -> hasStatus(status, root, cb, now))
                        .toArray(Predicate[]::new)));
            }
            if (query.locationId() != null) {
                all.add(cb.equal(root.get("locationId"), query.locationId()));
            }
            if (query.hasSeats()) {
                // Same as OPEN: bookable right now with a seat left.
                all.add(hasStatus(WorkshopStatus.OPEN, root, cb, now));
            }
            if (query.text() != null && !query.text().isBlank()) {
                String pattern = "%" + escapeLike(query.text().trim().toLowerCase(Locale.ROOT)) + "%";
                Subquery<UUID> locations = criteria.subquery(UUID.class);
                Root<LocationEntity> location = locations.from(LocationEntity.class);
                locations.select(location.get("id"))
                        .where(cb.like(cb.lower(location.get("name")), pattern, LIKE_ESCAPE));
                all.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("instructor")), pattern, LIKE_ESCAPE),
                        root.get("locationId").in(locations)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    static Predicate hasStatus(WorkshopStatus status, Root<WorkshopEntity> root, CriteriaBuilder cb, Instant now) {
        Predicate scheduled = cb.equal(root.get("lifecycle"), WorkshopLifecycle.SCHEDULED);
        Expression<Instant> startsAt = root.get("startsAt");
        Expression<Instant> endsAt = root.get("endsAt");
        Expression<Integer> seatsTaken = root.get("seatsTaken");
        Expression<Integer> capacity = root.get("capacity");
        return switch (status) {
            case OPEN -> cb.and(scheduled, cb.greaterThan(startsAt, now), cb.lessThan(seatsTaken, capacity));
            case FULL -> cb.and(scheduled, cb.greaterThan(startsAt, now), cb.greaterThanOrEqualTo(seatsTaken, capacity));
            case IN_PROGRESS -> cb.and(scheduled, cb.lessThanOrEqualTo(startsAt, now), cb.greaterThan(endsAt, now));
            case COMPLETED -> cb.and(scheduled, cb.lessThanOrEqualTo(endsAt, now));
            case CANCELLED -> cb.equal(root.get("lifecycle"), WorkshopLifecycle.CANCELLED);
        };
    }

    static Sort sort(WorkshopSort sort) {
        Sort primary = switch (sort) {
            case STARTS_AT_ASC -> Sort.by(Sort.Order.asc("startsAt"));
            case STARTS_AT_DESC -> Sort.by(Sort.Order.desc("startsAt"));
            case TITLE_ASC -> Sort.by(Sort.Order.asc("title"));
            case TITLE_DESC -> Sort.by(Sort.Order.desc("title"));
        };
        return primary.and(Sort.by(Sort.Order.asc("id")));
    }

    /** Typed text is matched literally: {@code %} and {@code _} are not wildcards. */
    static String escapeLike(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                out.append(LIKE_ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }
}
