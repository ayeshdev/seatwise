package com.seatwise.workshops.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately a plain {@link Repository}: it exposes no delete methods
 * (workshops are permanent history). The seat statements are native and
 * conditional, so the check and the write are one atomic step in PostgreSQL.
 */
public interface WorkshopRepository extends Repository<WorkshopEntity, UUID> {

    Optional<WorkshopEntity> findById(UUID id);

    List<WorkshopEntity> findAllById(Iterable<UUID> ids);

    Page<WorkshopEntity> findAll(Specification<WorkshopEntity> spec, Pageable pageable);

    WorkshopEntity saveAndFlush(WorkshopEntity workshop);

    boolean existsByCode(String code);

    /**
     * Loads the workshop with its row lock ({@code SELECT ... FOR UPDATE}). Edits
     * and cancellation take it first, so concurrent seat claims wait and the
     * seat count checked against a new capacity is exact until commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkshopEntity w where w.id = :id")
    Optional<WorkshopEntity> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Architecture section 7, step 1. Concurrent claims queue on the row lock;
     * under READ COMMITTED each one re-evaluates the WHERE clause against the
     * committed row, so at most {@code capacity} claims ever succeed. The
     * persistence context is cleared because it may hold a stale seat count.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE workshop
               SET seats_taken = seats_taken + 1
             WHERE id = :id
               AND lifecycle = 'SCHEDULED'
               AND starts_at > :now
               AND seats_taken < capacity""")
    int claimSeat(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE workshop
               SET seats_taken = seats_taken - 1
             WHERE id = :id
               AND seats_taken > 0""")
    int releaseSeat(@Param("id") UUID id);

    /**
     * Locks the row and classifies it with the same predicates as
     * {@link #claimSeat}, so "why did my claim fail" can't disagree with the claim.
     * {@code FOR NO KEY UPDATE} is the lock the claim's UPDATE takes itself, so
     * it serializes with claims and releases but not with plain FK checks.
     */
    @Query(nativeQuery = true, value = """
            SELECT CASE
                     WHEN lifecycle <> 'SCHEDULED' OR starts_at <= :now THEN 'NOT_OPEN'
                     WHEN seats_taken >= capacity THEN 'FULL'
                     ELSE 'OPEN'
                   END
              FROM workshop
             WHERE id = :id
               FOR NO KEY UPDATE""")
    Optional<String> lockAndClassify(@Param("id") UUID id, @Param("now") Instant now);
}
