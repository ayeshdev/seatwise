package com.seatwise.search.internal;

import com.seatwise.common.security.Policies;
import com.seatwise.workshops.WorkshopStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/workshops}: the list and search screen (FR-FIND).
 * {@code status} may repeat ({@code status=OPEN&status=FULL}); dates are
 * {@code YYYY-MM-DD} in the centre's timezone, both inclusive.
 */
@RestController
public class WorkshopSearchController {

    private final WorkshopSearch search;

    public WorkshopSearchController(WorkshopSearch search) {
        this.search = search;
    }

    @GetMapping("/api/v1/workshops")
    @PreAuthorize(Policies.CAN_VIEW_CATALOGUE)
    public WorkshopSearchResult search(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(name = "status", required = false) List<WorkshopStatus> statuses,
            @RequestParam(name = "locationId", required = false) UUID locationId,
            @RequestParam(name = "hasSeats", defaultValue = "false") boolean hasSeats,
            @RequestParam(name = "q", required = false) @Size(max = 100) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + WorkshopSearchCriteria.DEFAULT_SIZE) int size,
            @RequestParam(name = "sort", required = false) String sort) {
        WorkshopSearchCriteria criteria = new WorkshopSearchCriteria(
                from,
                to,
                statuses == null ? Set.of() : Set.copyOf(statuses),
                locationId,
                hasSeats,
                q,
                page,
                size,
                WorkshopSearchCriteria.parseSort(sort));
        return search.search(criteria);
    }
}
