package com.seatwise.registrations.internal;

import com.seatwise.registrations.RegistrationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately a plain {@link Repository} with no delete methods: registrations
 * are permanent history (a trigger refuses DELETE as well). Status changes are
 * conditional native updates whose row count says who won a race. They clear
 * the persistence context, so a later read never sees a stale entity.
 */
public interface RegistrationRepository extends Repository<RegistrationEntity, UUID> {

    Optional<RegistrationEntity> findById(UUID id);

    RegistrationEntity saveAndFlush(RegistrationEntity registration);

    /** Newest first; ties (same microsecond) broken by id so the order is stable. */
    List<RegistrationEntity> findByWorkshopIdOrderByRegisteredAtDescIdDesc(UUID workshopId);

    long countByWorkshopIdAndStatus(UUID workshopId, RegistrationStatus status);

    /** Bypasses the persistence context: the committed status, read after taking the workshop lock. */
    @Query(nativeQuery = true, value = "SELECT status FROM registration WHERE id = :id")
    Optional<String> currentStatus(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE registration
               SET status = 'CANCELLED',
                   cancelled_at = :now,
                   cancelled_by = :actor,
                   cancellation_reason = CAST(:reason AS varchar)
             WHERE id = :id
               AND status IN ('ACTIVE', 'WAITLISTED')""")
    int cancel(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("actor") UUID actor,
            @Param("reason") String reason);

    /** The waitlist in queue order (first come, first served). */
    @Query(nativeQuery = true, value = """
            SELECT id
              FROM registration
             WHERE workshop_id = :workshopId
               AND status = 'WAITLISTED'
             ORDER BY registered_at, id""")
    List<UUID> waitlistQueue(@Param("workshopId") UUID workshopId);

    /** The head of the waitlist, locked; SKIP LOCKED so two promoters never pick the same person. */
    @Query(nativeQuery = true, value = """
            SELECT id
              FROM registration
             WHERE workshop_id = :workshopId
               AND status = 'WAITLISTED'
             ORDER BY registered_at, id
             LIMIT 1
               FOR UPDATE SKIP LOCKED""")
    Optional<UUID> lockNextWaitlisted(@Param("workshopId") UUID workshopId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE registration
               SET status = 'ACTIVE',
                   promoted_at = :now
             WHERE id = :id
               AND status = 'WAITLISTED'""")
    int promote(@Param("id") UUID id, @Param("now") Instant now);
}
