package com.seatwise.workshops.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only: locations are seeded by migration. */
public interface LocationRepository extends Repository<LocationEntity, UUID> {

    Optional<LocationEntity> findById(UUID id);

    List<LocationEntity> findAll();

    List<LocationEntity> findAllByActiveTrueOrderByNameAsc();
}
