package com.seatwise.workshops.internal;

import com.seatwise.accounts.StaffDirectory;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.error.FieldErrorItem;
import com.seatwise.common.error.ProblemDetailsAdvice;
import com.seatwise.common.error.Problems;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.workshops.FieldChange;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WaitlistCounter;
import com.seatwise.workshops.WorkshopCancelled;
import com.seatwise.workshops.WorkshopLifecycle;
import com.seatwise.workshops.WorkshopScheduled;
import com.seatwise.workshops.WorkshopUpdated;
import com.seatwise.workshops.WorkshopView;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduling, editing and cancelling workshops (FR-WS). Edits and cancellation
 * take the workshop's row lock first: concurrent seat claims wait for it, so
 * "capacity can't go below the seats taken" is checked against an exact count,
 * with the {@code ck_workshop_seats_within_capacity} CHECK as the backstop.
 */
@Service
public class WorkshopService {

    static final Pattern CODE_FORMAT = Pattern.compile("^[A-Z0-9-]{3,32}$");

    private final WorkshopRepository repository;
    private final LocationRepository locations;
    private final WorkshopViews views;
    private final WaitlistCounter waitlist;
    private final StaffDirectory staff;
    private final ApplicationEventPublisher events;
    private final ActorProvider actors;
    private final Clock clock;

    public WorkshopService(
            WorkshopRepository repository,
            LocationRepository locations,
            WorkshopViews views,
            WaitlistCounter waitlist,
            StaffDirectory staff,
            ApplicationEventPublisher events,
            ActorProvider actors,
            Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.views = views;
        this.waitlist = waitlist;
        this.staff = staff;
        this.events = events;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkshopResponse get(UUID id) {
        return respond(load(id));
    }

    @Transactional(readOnly = true)
    public List<LocationView> activeLocations() {
        return locations.findAllByActiveTrueOrderByNameAsc().stream().map(LocationEntity::toView).toList();
    }

    @Transactional
    public WorkshopResponse create(WorkshopRequest request) {
        UUID actor = actors.currentStaffId();
        Instant now = now();
        WorkshopDetails details = validate(request, now, null);
        // Checked first for a field-level message; the unique constraint still
        // catches two Managers picking the same code at the same moment.
        if (repository.existsByCode(details.code())) {
            throw duplicateCode();
        }
        WorkshopEntity workshop = save(new WorkshopEntity(details, actor, now));
        events.publishEvent(new WorkshopScheduled(
                workshop.getId(),
                details.code(),
                details.title(),
                details.instructor(),
                details.locationId(),
                details.startsAt(),
                details.endsAt(),
                details.capacity(),
                actor,
                now));
        return respond(workshop);
    }

    @Transactional
    public WorkshopResponse update(UUID id, WorkshopRequest request) {
        UUID actor = actors.currentStaffId();
        if (request.version() == null) {
            throw validation(List.of(new FieldErrorItem("version", "is required when editing a workshop")));
        }
        WorkshopEntity workshop = repository.findByIdForUpdate(id).orElseThrow(WorkshopService::notFound);
        if (!workshop.getVersion().equals(request.version())) {
            throw stale(workshop.getVersion());
        }
        Instant now = now();
        WorkshopDetails before = workshop.details();
        WorkshopDetails after = validate(request, now, before);
        if (!after.code().equals(before.code()) && repository.existsByCode(after.code())) {
            throw duplicateCode();
        }
        // Exact: we hold the row lock, so no claim can slip in before commit.
        if (after.capacity() < workshop.getSeatsTaken()) {
            throw capacityBelowTaken(workshop.getSeatsTaken());
        }
        Map<String, FieldChange> changes = diff(before, after);
        if (changes.isEmpty()) {
            return respond(workshop);
        }
        workshop.edit(after, actor, now);
        save(workshop);
        events.publishEvent(new WorkshopUpdated(id, changes, actor, now));
        // A synchronous listener may have moved seats (waitlist promotion after
        // a capacity increase), which clears the persistence context: re-read.
        return respond(load(id));
    }

    @Transactional
    public WorkshopResponse cancel(UUID id, CancelWorkshopRequest request) {
        UUID actor = actors.currentStaffId();
        WorkshopEntity workshop = repository.findByIdForUpdate(id).orElseThrow(WorkshopService::notFound);
        Instant now = now();
        if (workshop.getLifecycle() == WorkshopLifecycle.CANCELLED) {
            throw new DomainException(ErrorCode.WORKSHOP_NOT_OPEN, "This workshop is already cancelled.");
        }
        if (!workshop.getEndsAt().isAfter(now)) {
            throw new DomainException(
                    ErrorCode.WORKSHOP_NOT_OPEN, "This workshop has already finished, so it can't be cancelled.");
        }
        String reason = request == null ? null : blankToNull(request.reason());
        workshop.cancel(reason, actor, now);
        save(workshop);
        events.publishEvent(new WorkshopCancelled(id, workshop.getCode(), workshop.getTitle(), reason, actor, now));
        return respond(workshop);
    }

    // ---- helpers ----

    private WorkshopDetails validate(WorkshopRequest request, Instant now, WorkshopDetails before) {
        List<FieldErrorItem> errors = new ArrayList<>();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!CODE_FORMAT.matcher(code).matches()) {
            errors.add(new FieldErrorItem("code", "Use 3 to 32 letters, digits or dashes, for example POT-0412."));
        }
        boolean keepsLocation = before != null && before.locationId().equals(request.locationId());
        boolean locationOk = locations.findById(request.locationId())
                .map(location -> location.isActive() || keepsLocation)
                .orElse(false);
        if (!locationOk) {
            errors.add(new FieldErrorItem("locationId", "Choose one of the centre's locations."));
        }
        Instant startsAt = request.startsAt().truncatedTo(ChronoUnit.MICROS);
        Instant endsAt = request.endsAt().truncatedTo(ChronoUnit.MICROS);
        // On an edit only a moved start must be in the future, so a running
        // workshop can still get its description fixed.
        boolean startMoved = before == null || !before.startsAt().equals(startsAt);
        if (startMoved && !startsAt.isAfter(now)) {
            errors.add(new FieldErrorItem("startsAt", "The start must be in the future."));
        }
        if (!endsAt.isAfter(startsAt)) {
            errors.add(new FieldErrorItem("endsAt", "The end must be after the start."));
        }
        if (!errors.isEmpty()) {
            throw validation(errors);
        }
        return new WorkshopDetails(
                code,
                request.title().trim(),
                blankToNull(request.description()),
                request.instructor().trim(),
                request.locationId(),
                startsAt,
                endsAt,
                request.capacity());
    }

    private static Map<String, FieldChange> diff(WorkshopDetails before, WorkshopDetails after) {
        Map<String, FieldChange> changes = new LinkedHashMap<>();
        put(changes, "code", before.code(), after.code());
        put(changes, "title", before.title(), after.title());
        put(changes, "description", before.description(), after.description());
        put(changes, "instructor", before.instructor(), after.instructor());
        put(changes, "locationId", before.locationId(), after.locationId());
        put(changes, "startsAt", before.startsAt(), after.startsAt());
        put(changes, "endsAt", before.endsAt(), after.endsAt());
        put(changes, "capacity", before.capacity(), after.capacity());
        return changes;
    }

    private static void put(Map<String, FieldChange> changes, String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            changes.put(field, new FieldChange(from, to));
        }
    }

    private WorkshopEntity save(WorkshopEntity workshop) {
        try {
            return repository.saveAndFlush(workshop);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw stale(null);
        } catch (DataIntegrityViolationException e) {
            String constraint = ProblemDetailsAdvice.violatedConstraint(e).orElse("");
            if (constraint.equals("uq_workshop_code")) {
                throw duplicateCode();
            }
            if (constraint.equals("ck_workshop_seats_within_capacity")) {
                throw new DomainException(
                        ErrorCode.CAPACITY_BELOW_TAKEN, "Capacity can't be lower than the seats already taken.");
            }
            throw e;
        }
    }

    private WorkshopResponse respond(WorkshopEntity workshop) {
        WorkshopView view = views.toView(workshop, clock.instant());
        Set<UUID> actorIds = new LinkedHashSet<>(List.of(view.createdBy(), view.updatedBy()));
        return WorkshopResponse.from(view, waitlist.waitlistCount(view.id()), staff.findAllById(actorIds));
    }

    private WorkshopEntity load(UUID id) {
        return repository.findById(id).orElseThrow(WorkshopService::notFound);
    }

    private Instant now() {
        // PostgreSQL keeps microseconds; truncating keeps Java and the row equal.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "There is no workshop with that id.");
    }

    private static DomainException capacityBelowTaken(int seatsTaken) {
        return new DomainException(
                ErrorCode.CAPACITY_BELOW_TAKEN,
                "Capacity can't be lower than the " + seatsTaken + " seats already taken.",
                Map.of("seatsTaken", seatsTaken));
    }

    private static DomainException duplicateCode() {
        String message = "That workshop code is already used.";
        return new DomainException(ErrorCode.VALIDATION_FAILED, message,
                Map.of(Problems.ERRORS, List.of(new FieldErrorItem("code", message))));
    }

    private static DomainException validation(List<FieldErrorItem> errors) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "Please check the highlighted fields.",
                Map.of(Problems.ERRORS, errors));
    }

    private static DomainException stale(Long currentVersion) {
        String detail = "Someone else changed this workshop after you opened it. Reload to see the latest version.";
        return currentVersion == null
                ? new DomainException(ErrorCode.STALE_VERSION, detail)
                : new DomainException(ErrorCode.STALE_VERSION, detail, Map.of("currentVersion", currentVersion));
    }
}
