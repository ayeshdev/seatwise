package com.seatwise.accounts.internal;

import com.seatwise.accounts.StaffAccountCreated;
import com.seatwise.accounts.StaffAccountDeactivated;
import com.seatwise.accounts.StaffAccountReactivated;
import com.seatwise.accounts.StaffAccountRenamed;
import com.seatwise.accounts.StaffPasswordReset;
import com.seatwise.accounts.StaffRoleChanged;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.common.security.StaffRole;
import com.seatwise.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff account lifecycle. Keycloak and the database are two systems without
 * a shared transaction, so every write is ordered to keep them consistent:
 * the database change is flushed first (so constraint and version failures
 * surface before Keycloak is touched), and the Keycloak call comes last, so
 * its failure rolls the database change back. Account creation is the one
 * case that has to start in Keycloak (the user id comes from there); it is
 * undone by a compensating delete if the insert fails.
 */
@Service
public class StaffAccountService {

    private static final Logger log = LoggerFactory.getLogger(StaffAccountService.class);

    private static final Sort BY_NAME = Sort.by("fullName").ascending().and(Sort.by("id"));

    private final StaffAccountRepository repository;
    private final IdentityProvisioner identity;
    private final ApplicationEventPublisher events;
    private final ActorProvider actors;
    private final Clock clock;

    public StaffAccountService(
            StaffAccountRepository repository,
            IdentityProvisioner identity,
            ApplicationEventPublisher events,
            ActorProvider actors,
            Clock clock) {
        this.repository = repository;
        this.identity = identity;
        this.events = events;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffAccountResponse> list(StaffRole role, Boolean active, int page, int size) {
        Specification<StaffAccountEntity> filter = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(
                repository.findAll(filter, PageRequest.of(page, size, BY_NAME)), StaffAccountResponse::from);
    }

    @Transactional(readOnly = true)
    public StaffAccountResponse get(UUID id) {
        return StaffAccountResponse.from(load(id));
    }

    @Transactional
    public StaffAccountResponse create(CreateStaffAccountRequest request) {
        UUID actor = actors.currentStaffId();
        String email = StaffAccountEntity.normalizeEmail(request.email());
        String fullName = request.fullName().trim();
        // Checked here so the common case never creates a Keycloak user only to
        // delete it again; the unique index still catches a concurrent duplicate.
        if (repository.findByEmailIgnoreCase(email).isPresent()) {
            throw emailInUse();
        }

        UUID id = identity.createUser(email, fullName, request.temporaryPassword(), true);
        try {
            StaffAccountEntity account = repository.saveAndFlush(
                    new StaffAccountEntity(id, email, fullName, request.role(), actor, clock.instant()));
            events.publishEvent(new StaffAccountCreated(
                    id, email, fullName, request.role(), actor, account.getCreatedAt()));
            return StaffAccountResponse.from(account);
        } catch (RuntimeException e) {
            compensateCreate(id, e);
            throw e;
        }
    }

    @Transactional
    public StaffAccountResponse update(UUID id, UpdateStaffAccountRequest request) {
        UUID actor = actors.currentStaffId();
        StaffAccountEntity account = load(id);
        requireVersion(account, request.version());

        String newName = request.fullName() == null ? account.getFullName() : request.fullName().trim();
        StaffRole newRole = request.role() == null ? account.getRole() : request.role();
        boolean newActive = request.active() == null ? account.isActive() : request.active();

        boolean renaming = !newName.equals(account.getFullName());
        boolean changingRole = newRole != account.getRole();
        boolean changingActive = newActive != account.isActive();
        if (!renaming && !changingRole && !changingActive) {
            return StaffAccountResponse.from(account);
        }

        // An Admin locking themselves out is never intended; another Admin can do it.
        if (account.getId().equals(actor) && (changingRole || (changingActive && !newActive))) {
            throw new DomainException(
                    ErrorCode.SELF_MODIFICATION,
                    "You can't change your own role or deactivate your own account. Ask another Admin.");
        }

        boolean removesAnAdmin = account.getRole() == StaffRole.ADMIN
                && account.isActive()
                && (newRole != StaffRole.ADMIN || !newActive);
        if (removesAnAdmin) {
            requireAnotherActiveAdmin(id);
        }

        Instant now = clock.instant();
        List<Object> changes = new ArrayList<>();
        if (renaming) {
            changes.add(new StaffAccountRenamed(id, account.getFullName(), newName, actor, now));
            account.rename(newName, actor, now);
        }
        if (changingRole) {
            changes.add(new StaffRoleChanged(id, account.getRole(), newRole, actor, now));
            account.changeRole(newRole, actor, now);
        }
        if (changingActive) {
            changes.add(newActive
                    ? new StaffAccountReactivated(id, actor, now)
                    : new StaffAccountDeactivated(id, actor, now));
            account.setActive(newActive, actor, now);
        }

        flush(account);
        changes.forEach(events::publishEvent);
        // Last, so a Keycloak failure rolls back the row (and its audit events).
        // The database flag alone already refuses the user's next request.
        if (changingActive) {
            identity.setEnabled(id, newActive);
        }
        return StaffAccountResponse.from(account);
    }

    @Transactional
    public void resetPassword(UUID id, PasswordResetRequest request) {
        UUID actor = actors.currentStaffId();
        load(id);
        // The row itself is not touched: bumping its version would make an
        // Admin's open edit form stale for a change that isn't on the form.
        events.publishEvent(new StaffPasswordReset(id, actor, clock.instant()));
        identity.resetPassword(id, request.temporaryPassword(), true);
    }

    // ---- System operations for startup seeding (no signed-in actor) ----

    @Transactional(readOnly = true)
    public boolean hasActiveAdmin() {
        return repository.existsByRoleAndActiveTrue(StaffRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return repository.findByEmailIgnoreCase(StaffAccountEntity.normalizeEmail(email)).isPresent();
    }

    /**
     * Makes sure the identity {@code id} has an active staff account with the
     * given role. Inserts the row (created_by NULL) if there is none; if there
     * is one, it is promoted and reactivated, which is how a bootstrap recovers
     * when every Admin was somehow lost. Returns true if an existing row changed.
     */
    @Transactional
    public boolean ensureSystemAccount(UUID id, String email, String fullName, StaffRole role) {
        Instant now = clock.instant();
        String normalized = StaffAccountEntity.normalizeEmail(email);
        StaffAccountEntity existing = repository.findById(id).orElse(null);
        if (existing == null) {
            repository.findByEmailIgnoreCase(normalized).ifPresent(other -> {
                throw new DomainException(
                        ErrorCode.EMAIL_IN_USE,
                        "A staff account for " + normalized + " exists with a different identity id");
            });
            repository.saveAndFlush(new StaffAccountEntity(id, normalized, fullName.trim(), role, null, now));
            events.publishEvent(new StaffAccountCreated(id, normalized, fullName.trim(), role, null, now));
            return false;
        }
        boolean changed = false;
        if (existing.getRole() != role) {
            events.publishEvent(new StaffRoleChanged(id, existing.getRole(), role, null, now));
            existing.changeRole(role, null, now);
            changed = true;
        }
        if (!existing.isActive()) {
            events.publishEvent(new StaffAccountReactivated(id, null, now));
            existing.setActive(true, null, now);
            changed = true;
        }
        return changed;
    }

    // ---- helpers ----

    private StaffAccountEntity load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DomainException(
                ErrorCode.NOT_FOUND, "There is no staff account with that id."));
    }

    private static void requireVersion(StaffAccountEntity account, Long expected) {
        if (!account.getVersion().equals(expected)) {
            throw stale(account.getVersion());
        }
    }

    /**
     * Locks all active Admin rows, then checks one other than {@code targetId}
     * remains. The lock serializes concurrent demotions, so two Admins removing
     * each other can't both succeed (see {@link StaffAccountRepository#lockActiveByRole}).
     */
    private void requireAnotherActiveAdmin(UUID targetId) {
        boolean anotherAdmin = repository.lockActiveByRole(StaffRole.ADMIN).stream()
                .anyMatch(admin -> !admin.getId().equals(targetId));
        if (!anotherAdmin) {
            throw new DomainException(
                    ErrorCode.LAST_ADMIN,
                    "This is the last active Admin. Make someone else an Admin first.");
        }
    }

    private void flush(StaffAccountEntity account) {
        try {
            repository.saveAndFlush(account);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Someone committed a change between our read and our write.
            throw stale(null);
        }
    }

    private void compensateCreate(UUID identityId, RuntimeException cause) {
        try {
            identity.deleteUser(identityId);
            log.warn("Staff account insert failed; removed the Keycloak user {} again", identityId);
        } catch (RuntimeException compensationFailure) {
            // Leaves an orphan Keycloak user without a staff row. It can't sign in
            // to anything (no row = ACCOUNT_INACTIVE), but an operator should clean it up.
            log.error("Could not remove orphaned Keycloak user {} after a failed insert", identityId,
                    compensationFailure);
            cause.addSuppressed(compensationFailure);
        }
    }

    private static DomainException stale(Long currentVersion) {
        String detail = "Someone else changed this account after you opened it. Reload to see the latest version.";
        return currentVersion == null
                ? new DomainException(ErrorCode.STALE_VERSION, detail)
                : new DomainException(ErrorCode.STALE_VERSION, detail, Map.of("currentVersion", currentVersion));
    }

    private static DomainException emailInUse() {
        return new DomainException(ErrorCode.EMAIL_IN_USE, "Another staff account already uses that email address.");
    }
}
