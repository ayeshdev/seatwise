package com.seatwise.workshops.internal;

import com.seatwise.workshops.LocationView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** A row of {@code location}. Seeded by migration; the API only reads it. */
@Entity
@Immutable
@Table(name = "location")
public class LocationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected LocationEntity() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public LocationView toView() {
        return new LocationView(id, name);
    }
}
