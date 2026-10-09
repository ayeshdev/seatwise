package com.seatwise.accounts;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The accounts module's public lookup. Other modules hold staff ids as plain
 * UUIDs and resolve names through this interface, never through the entity.
 */
public interface StaffDirectory {

    Optional<StaffSummary> findById(UUID id);

    /** Batch lookup for history lists; ids without an account are simply absent from the map. */
    Map<UUID, StaffSummary> findAllById(Collection<UUID> ids);
}
