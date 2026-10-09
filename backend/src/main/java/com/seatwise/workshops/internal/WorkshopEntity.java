package com.seatwise.workshops.internal;

import com.seatwise.workshops.WorkshopLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of {@code workshop}. {@link #seatsTaken} is mapped read-only
 * ({@code insertable = false, updatable = false}): a Manager editing the title
 * through JPA can never write back a stale seat count. Only the atomic
 * statements behind {@code SeatInventory} change that column. There is no
 * delete: workshops are cancelled, and a trigger refuses DELETE anyway.
 */
@Entity
@Table(name = "workshop")
public class WorkshopEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "instructor", nullable = false, length = 120)
    private String instructor;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "seats_taken", nullable = false, insertable = false, updatable = false)
    private int seatsTaken;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", nullable = false, length = 16)
    private WorkshopLifecycle lifecycle;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    // Null until persisted, which is how Spring Data tells "new" from "existing".
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected WorkshopEntity() {
        // for JPA
    }

    public WorkshopEntity(WorkshopDetails details, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        apply(details);
        this.lifecycle = WorkshopLifecycle.SCHEDULED;
        this.createdAt = now;
        this.createdBy = createdBy;
        this.updatedAt = now;
        this.updatedBy = createdBy;
    }

    public void edit(WorkshopDetails details, UUID actor, Instant now) {
        apply(details);
        touch(actor, now);
    }

    public void cancel(String reason, UUID actor, Instant now) {
        this.lifecycle = WorkshopLifecycle.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = actor;
        this.cancellationReason = reason;
        touch(actor, now);
    }

    private void apply(WorkshopDetails details) {
        this.code = details.code();
        this.title = details.title();
        this.description = details.description();
        this.instructor = details.instructor();
        this.locationId = details.locationId();
        this.startsAt = details.startsAt();
        this.endsAt = details.endsAt();
        this.capacity = details.capacity();
    }

    private void touch(UUID actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** The editable fields as they are now, for diffing against an edit. */
    public WorkshopDetails details() {
        return new WorkshopDetails(code, title, description, instructor, locationId, startsAt, endsAt, capacity);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getInstructor() {
        return instructor;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public WorkshopLifecycle getLifecycle() {
        return lifecycle;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }
}
