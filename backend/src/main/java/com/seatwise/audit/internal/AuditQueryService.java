package com.seatwise.audit.internal;

import com.seatwise.accounts.StaffDirectory;
import com.seatwise.accounts.StaffRef;
import com.seatwise.accounts.StaffSummary;
import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.error.FieldErrorItem;
import com.seatwise.common.error.Problems;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.common.security.StaffRole;
import com.seatwise.common.web.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the audit trail (FR-AUD-04, -05). Who may see what depends on the data
 * asked for, so it is enforced here rather than in a URL rule (architecture
 * section 8):
 *
 * <ul>
 *   <li>Admin: STAFF_ACCOUNT events only. An omitted {@code entityType} means
 *       STAFF_ACCOUNT; any other type, or a {@code workshopId}, is FORBIDDEN.
 *   <li>Manager and Staff: WORKSHOP and REGISTRATION events only. An omitted
 *       {@code entityType} means both; {@code workshopId} narrows to one
 *       workshop's timeline (the workshop and its bookings); STAFF_ACCOUNT is
 *       FORBIDDEN.
 * </ul>
 */
@Service
public class AuditQueryService {

    private static final Set<AuditEntityType> ACCOUNT_TYPES = EnumSet.of(AuditEntityType.STAFF_ACCOUNT);
    private static final Set<AuditEntityType> DESK_TYPES =
            EnumSet.of(AuditEntityType.WORKSHOP, AuditEntityType.REGISTRATION);

    private final AuditEventRepository repository;
    private final StaffDirectory staff;
    private final ActorProvider actors;
    private final ZoneId zone;

    public AuditQueryService(
            AuditEventRepository repository,
            StaffDirectory staff,
            ActorProvider actors,
            SeatwiseProperties properties) {
        this.repository = repository;
        this.staff = staff;
        this.actors = actors;
        this.zone = Objects.requireNonNullElse(properties.centreTimezone(), ZoneId.of("Europe/London"));
    }

    /** Newest first; {@code actor} resolved to a name, null for the system. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(AuditQuery query) {
        Set<AuditEntityType> types = visibleTypes(actors.current().role(), query);
        validate(query);
        AuditEventRepository.Filter filter = new AuditEventRepository.Filter(
                types, query.entityId(), query.workshopId(), startOfDay(query.from()), dayAfter(query.to()));

        long total = repository.count(filter);
        List<AuditEventRow> rows = total == 0
                ? List.of()
                : repository.find(filter, (long) query.page() * query.size(), query.size());
        Set<UUID> actorIds = rows.stream()
                .map(AuditEventRow::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, StaffSummary> names = actorIds.isEmpty() ? Map.of() : staff.findAllById(actorIds);
        List<AuditEventResponse> items = rows.stream().map(row -> respond(row, names)).toList();
        return new PageResponse<>(items, query.page(), query.size(), total);
    }

    private static Set<AuditEntityType> visibleTypes(StaffRole role, AuditQuery query) {
        AuditEntityType asked = query.entityType();
        if (role == StaffRole.ADMIN) {
            if ((asked != null && asked != AuditEntityType.STAFF_ACCOUNT) || query.workshopId() != null) {
                throw new DomainException(
                        ErrorCode.FORBIDDEN, "Admins can see account activity only, not workshops or bookings.");
            }
            return ACCOUNT_TYPES;
        }
        if (asked == AuditEntityType.STAFF_ACCOUNT) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only Admins can see account activity.");
        }
        return asked == null ? DESK_TYPES : EnumSet.of(asked);
    }

    private static void validate(AuditQuery query) {
        List<FieldErrorItem> errors = new ArrayList<>();
        if (query.page() < 0) {
            errors.add(new FieldErrorItem("page", "must be 0 or more"));
        }
        if (query.size() < 1 || query.size() > AuditQuery.MAX_SIZE) {
            errors.add(new FieldErrorItem("size", "must be between 1 and " + AuditQuery.MAX_SIZE));
        }
        if (query.from() != null && query.to() != null && query.to().isBefore(query.from())) {
            errors.add(new FieldErrorItem("to", "must be on or after the from date"));
        }
        if (!errors.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Please check the highlighted fields.",
                    Map.of(Problems.ERRORS, errors));
        }
    }

    // atStartOfDay(zone) also copes with a DST gap at midnight.
    private Instant startOfDay(LocalDate day) {
        return day == null ? null : day.atStartOfDay(zone).toInstant();
    }

    /** {@code to} is inclusive, so the bound is the start of the next day, exclusive. */
    private Instant dayAfter(LocalDate day) {
        return day == null ? null : day.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private static AuditEventResponse respond(AuditEventRow row, Map<UUID, StaffSummary> names) {
        return new AuditEventResponse(
                row.id(),
                row.occurredAt(),
                StaffRef.resolve(row.actorId(), names),
                row.entityType(),
                row.entityId(),
                row.workshopId(),
                row.action(),
                row.summary(),
                row.changes());
    }
}
