package com.seatwise.audit.internal;

import com.seatwise.accounts.StaffAccountCreated;
import com.seatwise.accounts.StaffAccountDeactivated;
import com.seatwise.accounts.StaffAccountReactivated;
import com.seatwise.accounts.StaffAccountRenamed;
import com.seatwise.accounts.StaffPasswordReset;
import com.seatwise.accounts.StaffRoleChanged;
import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.security.StaffRole;
import com.seatwise.registrations.AttendeeRegistered;
import com.seatwise.registrations.AttendeeWaitlisted;
import com.seatwise.registrations.RegistrationCancelled;
import com.seatwise.registrations.RegistrationStatus;
import com.seatwise.registrations.WaitlistPromoted;
import com.seatwise.workshops.FieldChange;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopCancelled;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopLifecycle;
import com.seatwise.workshops.WorkshopScheduled;
import com.seatwise.workshops.WorkshopUpdated;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Turns every domain event into one {@code audit_event} row (FR-AUD-01..03).
 *
 * <p>Plain synchronous {@code @EventListener}s on purpose (architecture section
 * 5): each runs inside the publisher's transaction, so the row commits or rolls
 * back together with the change it records. An audit write that fails therefore
 * fails the change too, which is the point: no change without its record.
 *
 * <p>Each row gets a plain-language {@code summary} the desk shows as-is, and a
 * {@code changes} map of field to {@code {from, to}}. Times in summaries are in
 * the centre's timezone; in {@code changes} they stay ISO-8601 UTC. Passwords
 * never reach this class (the reset event doesn't carry one).
 */
@Component
class AuditRecorder {

    static final int SUMMARY_MAX = 300;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    /** The order fields are named in an edit's summary, whatever order the event's map has. */
    private static final List<String> WORKSHOP_FIELDS = List.of(
            "code", "title", "description", "instructor", "locationId", "startsAt", "endsAt", "capacity");

    private final AuditEventRepository repository;
    private final WorkshopCatalogue catalogue;
    private final ZoneId zone;

    AuditRecorder(AuditEventRepository repository, WorkshopCatalogue catalogue, SeatwiseProperties properties) {
        this.repository = repository;
        this.catalogue = catalogue;
        this.zone = Objects.requireNonNullElse(properties.centreTimezone(), ZoneId.of("Europe/London"));
    }

    // ---- staff accounts ----

    @EventListener
    void on(StaffAccountCreated e) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        changes.put("fullName", change(null, e.fullName()));
        changes.put("email", change(null, e.email()));
        changes.put("role", change(null, e.role()));
        account(e.staffId(), AuditAction.CREATED, e.actorId(), e.occurredAt(),
                "Created the account for " + e.fullName() + " (" + e.email() + ") as " + roleLabel(e.role()),
                changes);
    }

    @EventListener
    void on(StaffAccountRenamed e) {
        account(e.staffId(), AuditAction.RENAMED, e.actorId(), e.occurredAt(),
                "Renamed from " + e.fromFullName() + " to " + e.toFullName(),
                Map.of("fullName", change(e.fromFullName(), e.toFullName())));
    }

    @EventListener
    void on(StaffRoleChanged e) {
        account(e.staffId(), AuditAction.ROLE_CHANGED, e.actorId(), e.occurredAt(),
                "Changed role from " + roleLabel(e.from()) + " to " + roleLabel(e.to()),
                Map.of("role", change(e.from(), e.to())));
    }

    @EventListener
    void on(StaffAccountDeactivated e) {
        account(e.staffId(), AuditAction.DEACTIVATED, e.actorId(), e.occurredAt(),
                "Deactivated the account", Map.of("active", change(true, false)));
    }

    @EventListener
    void on(StaffAccountReactivated e) {
        account(e.staffId(), AuditAction.REACTIVATED, e.actorId(), e.occurredAt(),
                "Reactivated the account", Map.of("active", change(false, true)));
    }

    @EventListener
    void on(StaffPasswordReset e) {
        account(e.staffId(), AuditAction.PASSWORD_RESET, e.actorId(), e.occurredAt(),
                "Set a new temporary password", Map.of());
    }

    // ---- workshops ----

    @EventListener
    void on(WorkshopScheduled e) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        changes.put("code", change(null, e.code()));
        changes.put("title", change(null, e.title()));
        changes.put("instructor", change(null, e.instructor()));
        changes.put("location", change(null, locationName(e.locationId())));
        changes.put("startsAt", change(null, e.startsAt()));
        changes.put("endsAt", change(null, e.endsAt()));
        changes.put("capacity", change(null, e.capacity()));
        workshop(e.workshopId(), AuditAction.CREATED, e.actorId(), e.occurredAt(),
                "Scheduled " + e.code() + " " + e.title(), changes);
    }

    /**
     * Runs before any other listener of the edit (such as the waitlist
     * promotion a capacity increase triggers), so the edit's row precedes the
     * rows of what it caused.
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void on(WorkshopUpdated e) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        List<String> phrases = new ArrayList<>();
        List<String> fields = new ArrayList<>(WORKSHOP_FIELDS);
        e.changes().keySet().stream().filter(f -> !fields.contains(f)).sorted().forEach(fields::add);
        for (String field : fields) {
            FieldChange c = e.changes().get(field);
            if (c == null) {
                continue;
            }
            switch (field) {
                case "locationId" -> {
                    // Names, not ids: the trail must read correctly even if a location is renamed later.
                    String from = locationName(c.from());
                    String to = locationName(c.to());
                    changes.put("location", change(from, to));
                    phrases.add("location from " + from + " to " + to);
                }
                case "description" -> {
                    changes.put(field, change(c.from(), c.to()));
                    phrases.add("the description");
                }
                case "startsAt" -> {
                    changes.put(field, change(c.from(), c.to()));
                    phrases.add("start from " + when(c.from()) + " to " + when(c.to()));
                }
                case "endsAt" -> {
                    changes.put(field, change(c.from(), c.to()));
                    phrases.add("end from " + when(c.from()) + " to " + when(c.to()));
                }
                default -> {
                    changes.put(field, change(c.from(), c.to()));
                    phrases.add(field + " from " + text(c.from()) + " to " + text(c.to()));
                }
            }
        }
        workshop(e.workshopId(), AuditAction.UPDATED, e.actorId(), e.occurredAt(),
                "Changed " + sentence(phrases), changes);
    }

    @EventListener
    void on(WorkshopCancelled e) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        changes.put("status", change(WorkshopLifecycle.SCHEDULED, WorkshopLifecycle.CANCELLED));
        if (e.reason() != null) {
            changes.put("cancellationReason", change(null, e.reason()));
        }
        workshop(e.workshopId(), AuditAction.CANCELLED, e.actorId(), e.occurredAt(),
                "Cancelled " + e.code() + " " + e.title() + reason(e.reason()), changes);
    }

    // ---- registrations ----

    @EventListener
    void on(AttendeeRegistered e) {
        registration(e.registrationId(), e.workshopId(), AuditAction.REGISTERED, e.actorId(), e.occurredAt(),
                "Registered " + e.attendeeName() + " (" + e.attendeeEmail() + ")",
                attendee(e.attendeeName(), e.attendeeEmail(), RegistrationStatus.ACTIVE));
    }

    @EventListener
    void on(AttendeeWaitlisted e) {
        registration(e.registrationId(), e.workshopId(), AuditAction.WAITLISTED, e.actorId(), e.occurredAt(),
                "Added " + e.attendeeName() + " (" + e.attendeeEmail() + ") to the waitlist",
                attendee(e.attendeeName(), e.attendeeEmail(), RegistrationStatus.WAITLISTED));
    }

    @EventListener
    void on(RegistrationCancelled e) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        changes.put("status", change(e.previousStatus(), RegistrationStatus.CANCELLED));
        if (e.reason() != null) {
            changes.put("cancellationReason", change(null, e.reason()));
        }
        String what = e.previousStatus() == RegistrationStatus.WAITLISTED ? "waitlist place" : "booking";
        registration(e.registrationId(), e.workshopId(), AuditAction.CANCELLED, e.actorId(), e.occurredAt(),
                "Cancelled " + e.attendeeName() + "'s " + what + reason(e.reason()), changes);
    }

    @EventListener
    void on(WaitlistPromoted e) {
        registration(e.registrationId(), e.workshopId(), AuditAction.PROMOTED, e.actorId(), e.occurredAt(),
                "Moved " + e.attendeeName() + " up from the waitlist",
                Map.of("status", change(RegistrationStatus.WAITLISTED, RegistrationStatus.ACTIVE)));
    }

    // ---- writing ----

    private void account(
            UUID staffId, AuditAction action, UUID actor, Instant at, String summary, Map<String, AuditChange> changes) {
        write(AuditEntityType.STAFF_ACCOUNT, staffId, null, action, actor, at, summary, changes);
    }

    private void workshop(
            UUID workshopId, AuditAction action, UUID actor, Instant at, String summary,
            Map<String, AuditChange> changes) {
        write(AuditEntityType.WORKSHOP, workshopId, workshopId, action, actor, at, summary, changes);
    }

    private void registration(
            UUID registrationId, UUID workshopId, AuditAction action, UUID actor, Instant at, String summary,
            Map<String, AuditChange> changes) {
        write(AuditEntityType.REGISTRATION, registrationId, workshopId, action, actor, at, summary, changes);
    }

    private void write(
            AuditEntityType type, UUID entityId, UUID workshopId, AuditAction action, UUID actor, Instant at,
            String summary, Map<String, AuditChange> changes) {
        repository.insert(new AuditEventRow(
                null, at, actor, type, entityId, workshopId, action, truncate(summary), changes));
    }

    // ---- wording ----

    private static Map<String, AuditChange> attendee(String name, String email, RegistrationStatus status) {
        Map<String, AuditChange> changes = new LinkedHashMap<>();
        changes.put("attendeeName", change(null, name));
        changes.put("attendeeEmail", change(null, email));
        changes.put("status", change(null, status));
        return changes;
    }

    /** JSON-friendly values: instants as ISO-8601 UTC, ids as strings, enums by name. */
    private static AuditChange change(Object from, Object to) {
        return new AuditChange(jsonValue(from), jsonValue(to));
    }

    private static Object jsonValue(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant.toString();
            case UUID id -> id.toString();
            case Enum<?> constant -> constant.name();
            default -> value;
        };
    }

    private String locationName(Object id) {
        if (!(id instanceof UUID locationId)) {
            return text(id);
        }
        return catalogue.findLocation(locationId).map(LocationView::name).orElse(locationId.toString());
    }

    private String when(Object value) {
        return value instanceof Instant instant ? WHEN.format(instant.atZone(zone)) : text(value);
    }

    private static String text(Object value) {
        return value == null ? "not set" : value.toString();
    }

    static String roleLabel(StaffRole role) {
        String name = role.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String reason(String reason) {
        return reason == null ? "" : " — reason: " + reason;
    }

    /** "a", "a and b", "a, b and c". */
    static String sentence(List<String> phrases) {
        if (phrases.size() <= 1) {
            return String.join("", phrases);
        }
        return String.join(", ", phrases.subList(0, phrases.size() - 1)) + " and " + phrases.getLast();
    }

    static String truncate(String summary) {
        return summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX - 1) + "…";
    }
}
