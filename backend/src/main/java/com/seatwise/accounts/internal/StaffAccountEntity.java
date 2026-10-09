package com.seatwise.accounts.internal;

import com.seatwise.common.security.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A row of {@code staff_account}. The id is assigned (it is the Keycloak user
 * id), so Spring Data decides "new or existing" from the null {@link #version}.
 * There is no delete: accounts are deactivated, because history points at them.
 */
@Entity
@Table(name = "staff_account")
public class StaffAccountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private StaffRole role;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StaffAccountEntity() {
        // for JPA
    }

    public StaffAccountEntity(UUID id, String email, String fullName, StaffRole role, UUID createdBy, Instant now) {
        this.id = id;
        this.email = normalizeEmail(email);
        this.fullName = fullName;
        this.role = role;
        this.active = true;
        this.createdAt = now;
        this.createdBy = createdBy;
        this.updatedAt = now;
        this.updatedBy = createdBy;
    }

    /** Emails are stored lower-cased so equality in Java matches the unique index on lower(email). */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void rename(String newFullName, UUID actor, Instant now) {
        this.fullName = newFullName;
        touch(actor, now);
    }

    public void changeRole(StaffRole newRole, UUID actor, Instant now) {
        this.role = newRole;
        touch(actor, now);
    }

    public void setActive(boolean newActive, UUID actor, Instant now) {
        this.active = newActive;
        touch(actor, now);
    }

    private void touch(UUID actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public StaffRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
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
