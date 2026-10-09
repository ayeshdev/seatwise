package com.seatwise.search.internal;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.error.FieldErrorItem;
import com.seatwise.common.error.Problems;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What staff asked the list screen for, in their terms: calendar dates in the
 * centre's timezone (both inclusive), derived statuses, free text. Every
 * {@link WorkshopSearch} implementation receives exactly this, so the
 * PostgreSQL and the index implementation answer the same question.
 *
 * @param statuses any of these (OR); empty means every status
 * @param page zero-based
 * @param size 1..{@value #MAX_SIZE}
 */
public record WorkshopSearchCriteria(
        LocalDate from,
        LocalDate to,
        Set<WorkshopStatus> statuses,
        UUID locationId,
        boolean hasSeats,
        String q,
        int page,
        int size,
        WorkshopSort sort) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public WorkshopSearchCriteria {
        List<FieldErrorItem> errors = new ArrayList<>();
        if (from != null && to != null && to.isBefore(from)) {
            errors.add(new FieldErrorItem("to", "must be on or after the from date"));
        }
        if (page < 0) {
            errors.add(new FieldErrorItem("page", "must be greater than or equal to 0"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(new FieldErrorItem("size", "must be between 1 and " + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        q = q == null || q.isBlank() ? null : q.trim();
        sort = sort == null ? WorkshopSort.STARTS_AT_ASC : sort;
    }

    /**
     * Parses {@code field[,direction]} for the sortable fields {@code startsAt}
     * (the default, soonest first) and {@code title}. Null or blank means the default.
     */
    public static WorkshopSort parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return WorkshopSort.STARTS_AT_ASC;
        }
        String[] parts = raw.trim().split(",", -1);
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (parts.length <= 2 && (direction.equals("asc") || direction.equals("desc"))) {
            boolean ascending = direction.equals("asc");
            switch (parts[0].trim()) {
                case "startsAt" -> {
                    return ascending ? WorkshopSort.STARTS_AT_ASC : WorkshopSort.STARTS_AT_DESC;
                }
                case "title" -> {
                    return ascending ? WorkshopSort.TITLE_ASC : WorkshopSort.TITLE_DESC;
                }
                default -> {
                    // falls through to the error below
                }
            }
        }
        throw invalid(List.of(new FieldErrorItem(
                "sort", "must be startsAt or title, optionally followed by ,asc or ,desc")));
    }

    private static DomainException invalid(List<FieldErrorItem> errors) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "Please check the search filters.",
                Map.of(Problems.ERRORS, errors));
    }
}
