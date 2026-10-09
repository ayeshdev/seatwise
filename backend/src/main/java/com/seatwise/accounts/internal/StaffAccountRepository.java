package com.seatwise.accounts.internal;

import com.seatwise.common.security.StaffRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffAccountRepository
        extends JpaRepository<StaffAccountEntity, UUID>, JpaSpecificationExecutor<StaffAccountEntity> {

    Optional<StaffAccountEntity> findByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(StaffRole role);

    boolean existsByRoleAndActiveTrue(StaffRole role);

    /**
     * Locks every active row with the given role ({@code SELECT ... FOR UPDATE}).
     * The last-Admin guard calls this before deciding: two Admins demoting each
     * other at the same moment are serialized, and the second one re-reads the
     * rows after the first commits, so it sees one Admin left and is refused.
     * A plain count would let both pass and leave zero Admins.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffAccountEntity s where s.role = :role and s.active = true")
    List<StaffAccountEntity> lockActiveByRole(@Param("role") StaffRole role);
}
