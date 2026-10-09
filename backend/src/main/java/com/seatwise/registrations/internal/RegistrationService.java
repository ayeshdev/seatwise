package com.seatwise.registrations.internal;

import com.seatwise.accounts.StaffDirectory;
import com.seatwise.accounts.StaffSummary;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.error.ProblemDetailsAdvice;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.registrations.AttendeeRegistered;
import com.seatwise.registrations.AttendeeWaitlisted;
import com.seatwise.registrations.RegistrationCancelled;
import com.seatwise.registrations.RegistrationStatus;
import com.seatwise.registrations.WaitlistPromoted;
import com.seatwise.workshops.BookingAvailability;
import com.seatwise.workshops.SeatInventory;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering, cancelling and the waitlist (FR-REG, FR-WL; architecture
 * section 7). Seats are only ever moved through {@link SeatInventory}, inside
 * the same transaction as the registration row they belong to.
 *
 * <p>Locking: every path takes the workshop's row lock before it touches any
 * of that workshop's registration rows (the seat claim's UPDATE, or
 * {@link SeatInventory#availabilityOf}). One lock order everywhere means no
 * deadlocks, and while the lock is held no other status change on that
 * workshop's registrations can interleave.
 */
@Service
public class RegistrationService {

    private final RegistrationRepository repository;
    private final SeatInventory seats;
    private final WorkshopCatalogue catalogue;
    private final StaffDirectory staff;
    private final ApplicationEventPublisher events;
    private final ActorProvider actors;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository repository,
            SeatInventory seats,
            WorkshopCatalogue catalogue,
            StaffDirectory staff,
            ApplicationEventPublisher events,
            ActorProvider actors,
            Clock clock) {
        this.repository = repository;
        this.seats = seats;
        this.catalogue = catalogue;
        this.staff = staff;
        this.events = events;
        this.actors = actors;
        this.clock = clock;
    }

    /**
     * Claims a seat and inserts the ACTIVE row, or (if full and asked to)
     * inserts a WAITLISTED row. A duplicate attendee hits the partial unique
     * index on insert; the exception rolls back the whole transaction, seat
     * claim included, so nothing is partially saved (FR-REG-03).
     */
    @Transactional
    public RegistrationResponse register(UUID workshopId, RegisterRequest request) {
        UUID actor = actors.currentStaffId();
        RegistrationStatus status = claimSeatOrJoinWaitlist(workshopId, request.waitlistIfFull());
        Instant now = now();
        RegistrationEntity registration = insert(new RegistrationEntity(
                workshopId, request.attendeeName().trim(), request.attendeeEmail().trim(), status, actor, now));
        if (status == RegistrationStatus.ACTIVE) {
            events.publishEvent(new AttendeeRegistered(registration.getId(), workshopId,
                    registration.getAttendeeName(), registration.getAttendeeEmail(), actor, now));
        } else {
            events.publishEvent(new AttendeeWaitlisted(registration.getId(), workshopId,
                    registration.getAttendeeName(), registration.getAttendeeEmail(), actor, now));
        }
        return respond(List.of(registration), workshopId).getFirst();
    }

    /**
     * Cancels an ACTIVE or WAITLISTED registration with one conditional UPDATE:
     * if a colleague got there first it affects no row and the caller is told
     * ALREADY_CANCELLED (FR-REG-10). A freed seat goes straight to the head of
     * the waitlist when the workshop is still bookable; otherwise it is released.
     */
    @Transactional
    public CancelResult cancel(UUID registrationId, CancelRegistrationRequest request) {
        UUID actor = actors.currentStaffId();
        RegistrationEntity target = repository.findById(registrationId)
                .orElseThrow(RegistrationService::registrationNotFound);
        UUID workshopId = target.getWorkshopId();
        String attendeeName = target.getAttendeeName();

        // Workshop lock first (see class comment); after it, the status we read
        // can't change under us, so it is the status we are about to cancel.
        BookingAvailability availability = seats.availabilityOf(workshopId);
        RegistrationStatus previous = repository.currentStatus(registrationId)
                .map(RegistrationStatus::valueOf)
                .orElseThrow(RegistrationService::registrationNotFound);
        Instant now = now();
        String reason = request == null ? null : blankToNull(request.reason());
        if (previous == RegistrationStatus.CANCELLED || repository.cancel(registrationId, now, actor, reason) == 0) {
            throw new DomainException(
                    ErrorCode.ALREADY_CANCELLED, "This booking was already cancelled, possibly by a colleague just now.");
        }
        events.publishEvent(new RegistrationCancelled(
                registrationId, workshopId, attendeeName, previous, reason, actor, now));

        Optional<UUID> promoted = Optional.empty();
        if (previous == RegistrationStatus.ACTIVE) {
            if (availability.isBookable()) {
                promoted = promoteNext(workshopId, registrationId, actor, now);
            }
            if (promoted.isEmpty()) {
                seats.releaseSeat(workshopId);
            }
            // else: the seat passed to the promoted attendee and seats_taken never dipped.
        }

        RegistrationEntity cancelled = repository.findById(registrationId).orElseThrow();
        List<RegistrationEntity> rows = promoted
                .map(id -> List.of(cancelled, repository.findById(id).orElseThrow()))
                .orElse(List.of(cancelled));
        List<RegistrationResponse> responses = respond(rows, workshopId);
        return new CancelResult(responses.getFirst(), responses.size() > 1 ? responses.get(1) : null);
    }

    /** The full history of one workshop, newest first, optionally one status only (FR-REG-09). */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> history(UUID workshopId, RegistrationStatus status) {
        if (catalogue.find(workshopId).isEmpty()) {
            throw workshopNotFound();
        }
        List<RegistrationEntity> rows = repository.findByWorkshopIdOrderByRegisteredAtDescIdDesc(workshopId).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .toList();
        return respond(rows, workshopId);
    }

    /**
     * Moves people from the head of the waitlist into free seats, one claim per
     * person, until the waitlist is empty or the workshop is full again. Runs in
     * the caller's transaction, which must already hold the workshop lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fillFromWaitlist(UUID workshopId, UUID actor) {
        Instant now = now();
        Optional<UUID> next = repository.lockNextWaitlisted(workshopId);
        while (next.isPresent() && seats.tryClaimSeat(workshopId)) {
            repository.promote(next.get(), now);
            events.publishEvent(new WaitlistPromoted(
                    next.get(), workshopId, attendeeName(next.get()), null, actor, now));
            next = repository.lockNextWaitlisted(workshopId);
        }
    }

    // ---- helpers ----

    private RegistrationStatus claimSeatOrJoinWaitlist(UUID workshopId, boolean waitlistIfFull) {
        if (seats.tryClaimSeat(workshopId)) {
            return RegistrationStatus.ACTIVE;
        }
        // Why did it fail? This also locks the workshop row, so the answer holds until commit.
        return switch (seats.availabilityOf(workshopId)) {
            case NOT_FOUND -> throw workshopNotFound();
            case NOT_OPEN -> throw notOpen(workshopId);
            case FULL -> {
                if (!waitlistIfFull) {
                    throw new DomainException(ErrorCode.WORKSHOP_FULL,
                            "Every seat on this workshop is taken. You can add this person to the waitlist instead.");
                }
                yield RegistrationStatus.WAITLISTED;
            }
            // A seat was freed between the claim and the check. We hold the lock
            // now, so this second claim can't lose to anyone.
            case OPEN -> {
                if (!seats.tryClaimSeat(workshopId)) {
                    throw new DomainException(ErrorCode.WORKSHOP_FULL, "Every seat on this workshop is taken.");
                }
                yield RegistrationStatus.ACTIVE;
            }
        };
    }

    private Optional<UUID> promoteNext(UUID workshopId, UUID freedBy, UUID actor, Instant now) {
        Optional<UUID> next = repository.lockNextWaitlisted(workshopId)
                .filter(id -> repository.promote(id, now) == 1);
        next.ifPresent(id -> events.publishEvent(
                new WaitlistPromoted(id, workshopId, attendeeName(id), freedBy, actor, now)));
        return next;
    }

    private String attendeeName(UUID registrationId) {
        return repository.findById(registrationId).orElseThrow().getAttendeeName();
    }

    private RegistrationEntity insert(RegistrationEntity registration) {
        try {
            return repository.saveAndFlush(registration);
        } catch (DataIntegrityViolationException e) {
            if ("uq_registration_live_attendee".equals(ProblemDetailsAdvice.violatedConstraint(e).orElse(null))) {
                throw new DomainException(ErrorCode.DUPLICATE_REGISTRATION,
                        "This person already has a booking or waitlist place on this workshop.");
            }
            throw e;
        }
    }

    /** Resolves every actor with one directory call and adds waitlist positions. */
    private List<RegistrationResponse> respond(List<RegistrationEntity> rows, UUID workshopId) {
        Set<UUID> actorIds = new HashSet<>();
        boolean anyWaitlisted = false;
        for (RegistrationEntity r : rows) {
            actorIds.add(r.getRegisteredBy());
            if (r.getCancelledBy() != null) {
                actorIds.add(r.getCancelledBy());
            }
            anyWaitlisted |= r.getStatus() == RegistrationStatus.WAITLISTED;
        }
        Map<UUID, StaffSummary> names = staff.findAllById(actorIds);
        List<UUID> queue = anyWaitlisted ? repository.waitlistQueue(workshopId) : List.of();
        return rows.stream()
                .map(r -> RegistrationResponse.from(r, position(queue, r.getId()), names))
                .toList();
    }

    private static Integer position(List<UUID> queue, UUID id) {
        int index = queue.indexOf(id);
        return index < 0 ? null : index + 1;
    }

    private DomainException notOpen(UUID workshopId) {
        WorkshopStatus status = catalogue.find(workshopId).map(w -> w.status()).orElse(null);
        String detail = switch (Objects.requireNonNullElse(status, WorkshopStatus.CANCELLED)) {
            case CANCELLED -> "This workshop has been cancelled, so it can't take new bookings.";
            case IN_PROGRESS -> "This workshop has already started. Bookings close when it starts.";
            case COMPLETED -> "This workshop has already finished.";
            case OPEN, FULL -> "This workshop isn't open for bookings.";
        };
        return new DomainException(ErrorCode.WORKSHOP_NOT_OPEN, detail);
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

    private static DomainException workshopNotFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "There is no workshop with that id.");
    }

    private static DomainException registrationNotFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "There is no registration with that id.");
    }
}
