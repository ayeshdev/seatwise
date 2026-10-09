package com.seatwise.audit.internal;

import com.seatwise.common.security.Policies;
import com.seatwise.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/audit-events}: the activity timelines (FR-AUD-04, -05).
 * Every role may call it; which entity types each role sees is enforced by
 * {@link AuditQueryService}, because it depends on the filters. Dates are
 * {@code YYYY-MM-DD} in the centre's timezone, both inclusive.
 */
@RestController
public class AuditEventController {

    private final AuditQueryService service;

    public AuditEventController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/audit-events")
    @PreAuthorize(Policies.CAN_VIEW_AUDIT)
    public PageResponse<AuditEventResponse> list(
            @RequestParam(name = "entityType", required = false) AuditEntityType entityType,
            @RequestParam(name = "entityId", required = false) UUID entityId,
            @RequestParam(name = "workshopId", required = false) UUID workshopId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "" + AuditQuery.DEFAULT_SIZE) @Min(1)
                    @Max(AuditQuery.MAX_SIZE) int size) {
        return service.list(new AuditQuery(entityType, entityId, workshopId, from, to, page, size));
    }
}
