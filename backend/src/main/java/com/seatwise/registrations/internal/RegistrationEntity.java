package com.seatwise.registrations.internal;

import com.seatwise.registrations.RegistrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * A row of {@code registration}. Inserted through JPA, then never written by
 * it again ({@link Immutable}): every status change is a conditional native
 * {@code UPDATE} in {@link RegistrationRepository}, so "who got there first"
 * is decided by PostgreSQL, not by whichever stale entity flushes last.
 * The workshop is a raw id: it belongs to another module.
 */
@Entity
@Immutable
@Table(name = "registration")
public class RegistrationEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;

    @Column(name = "attendee_name", nullable = false, length = 120)
    private String attendeeName;

    @Column(name = "attendee_email", nullable = false, length = 254)
    private String attendeeEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RegistrationStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected RegistrationEntity() {
        // for JPA
    }

    public RegistrationEntity(
            UUID workshopId,
            String attendeeName,
            String attendeeEmail,
            RegistrationStatus status,
            UUID registeredBy,
            Instant now) {
        this.workshopId = workshopId;
        this.attendeeName = attendeeName;
        this.attendeeEmail = attendeeEmail;
        this.status = status;
        this.registeredBy = registeredBy;
        this.registeredAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkshopId() {
        return workshopId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public String getAttendeeEmail() {
        return attendeeEmail;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
