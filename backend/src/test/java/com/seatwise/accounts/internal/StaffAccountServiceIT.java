package com.seatwise.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seatwise.accounts.StaffAccountCreated;
import com.seatwise.accounts.StaffAccountDeactivated;
import com.seatwise.accounts.StaffRoleChanged;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.AccountInactiveException;
import com.seatwise.common.security.StaffAuthenticationToken;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.support.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/** Account rules against a real PostgreSQL (constraints, locks, versions); Keycloak is mocked. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class StaffAccountServiceIT {

    private static final String PASSWORD = "Temporary#Pass1";

    @Autowired
    private StaffAccountService service;

    @Autowired
    private StaffAccountRepository repository;

    @Autowired
    private StaffAuthenticationConverter converter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TransactionTemplate transactions;

    @MockitoBean
    private IdentityProvisioner identity;

    private UUID adminId;

    @BeforeEach
    void startWithOneAdminSignedIn() {
        // Test-only cleanup; the application itself never deletes accounts.
        // CASCADE: workshops and registrations reference staff (V2/V3).
        jdbc.execute("TRUNCATE staff_account CASCADE");
        adminId = seed("admin@example.com", "Alex Admin", StaffRole.ADMIN);
        signIn(adminId);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createInsertsTheAccountUnderTheKeycloakIdAndPublishesAnEvent() {
        // Arrange
        UUID keycloakId = UUID.randomUUID();
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(keycloakId);

        // Act
        StaffAccountResponse created = service.create(
                new CreateStaffAccountRequest("  Dana.Lee@Example.com ", "Dana Lee", StaffRole.STAFF, PASSWORD));

        // Assert
        assertThat(created.id()).isEqualTo(keycloakId);
        assertThat(created.email()).isEqualTo("dana.lee@example.com");
        assertThat(created.active()).isTrue();
        assertThat(created.version()).isZero();
        verify(identity).createUser("dana.lee@example.com", "Dana Lee", PASSWORD, true);
        StaffAccountEntity row = repository.findById(keycloakId).orElseThrow();
        assertThat(row.getCreatedBy()).isEqualTo(adminId);
        assertThat(events.stream(StaffAccountCreated.class).filter(e -> e.staffId().equals(keycloakId)))
                .singleElement()
                .satisfies(e -> assertThat(e.actorId()).isEqualTo(adminId));
    }

    @Test
    void duplicateEmailIsRefusedCaseInsensitivelyWithoutTouchingKeycloak() {
        // Arrange
        seed("dana@example.com", "Dana Lee", StaffRole.STAFF);

        // Act / Assert
        assertThatThrownBy(() -> service.create(
                        new CreateStaffAccountRequest("DANA@Example.COM", "Dana Again", StaffRole.STAFF, PASSWORD)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_IN_USE));
        verify(identity, never()).createUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void passwordContainingTheEmailIsRefusedBeforeKeycloakIsCalled() {
        // Act / Assert
        assertThatThrownBy(() -> service.create(new CreateStaffAccountRequest(
                        "dana@example.com", "Dana Lee", StaffRole.STAFF, "xDANA@example.com1")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        verify(identity, never()).createUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void keycloakUserIsDeletedAgainWhenTheInsertFails() {
        // Arrange: Keycloak hands back an id that already has a row, so the insert violates the PK.
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(adminId);

        // Act / Assert
        assertThatThrownBy(() -> service.create(
                        new CreateStaffAccountRequest("new@example.com", "New Person", StaffRole.STAFF, PASSWORD)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(identity).deleteUser(adminId);
        assertThat(repository.findByEmailIgnoreCase("new@example.com")).isEmpty();
    }

    @Test
    void compensatingDeleteIsRetriedWhenKeycloakIsBrieflyUnavailable() {
        // Arrange: the insert fails (id already has a row); Keycloak refuses the first two deletes.
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(adminId);
        doThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "down"))
                .doThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "down"))
                .doNothing()
                .when(identity).deleteUser(adminId);

        // Act / Assert
        assertThatThrownBy(() -> service.create(
                        new CreateStaffAccountRequest("new@example.com", "New Person", StaffRole.STAFF, PASSWORD)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
        verify(identity, times(3)).deleteUser(adminId);
    }

    @Test
    void compensatingDeleteGivesUpAfterThreeAttemptsAndKeepsTheOriginalError() {
        // Arrange
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(adminId);
        doThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "down")).when(identity).deleteUser(adminId);

        // Act / Assert
        assertThatThrownBy(() -> service.create(
                        new CreateStaffAccountRequest("new@example.com", "New Person", StaffRole.STAFF, PASSWORD)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(e.getSuppressed())
                        .singleElement()
                        .isInstanceOfSatisfying(DomainException.class,
                                suppressed -> assertThat(suppressed.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE)));
        verify(identity, times(3)).deleteUser(adminId);
    }

    @Test
    void createTimeoutLooksTheUserUpDeletesItAndReportsIdentityUnavailable() {
        // Arrange: Keycloak committed the user but the response never arrived.
        UUID orphanId = UUID.randomUUID();
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "timed out"));
        when(identity.findUserIdByEmail("new@example.com")).thenReturn(Optional.of(orphanId));

        // Act / Assert
        assertThatThrownBy(() -> service.create(
                        new CreateStaffAccountRequest("new@example.com", "New Person", StaffRole.STAFF, PASSWORD)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
        verify(identity).findUserIdByEmail("new@example.com");
        verify(identity).deleteUser(orphanId);
        assertThat(repository.findByEmailIgnoreCase("new@example.com")).isEmpty();
    }

    @Test
    void createTimeoutWithNoUserInKeycloakDeletesNothing() {
        // Arrange: the request never reached Keycloak.
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "unreachable"));
        when(identity.findUserIdByEmail("new@example.com")).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.create(
                        new CreateStaffAccountRequest("new@example.com", "New Person", StaffRole.STAFF, PASSWORD)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
        verify(identity, never()).deleteUser(any());
    }

    @Test
    void keycloakUserIsDeletedWhenTheSurroundingTransactionRollsBackAfterTheInsert() {
        // Arrange: the insert succeeds, then the outer transaction fails at commit time.
        UUID keycloakId = UUID.randomUUID();
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(keycloakId);

        // Act
        transactions.executeWithoutResult(status -> {
            service.create(new CreateStaffAccountRequest("late@example.com", "Late Failure", StaffRole.STAFF, PASSWORD));
            status.setRollbackOnly();
        });

        // Assert
        verify(identity).deleteUser(keycloakId);
        assertThat(repository.findById(keycloakId)).isEmpty();
    }

    @Test
    void keycloakUserIsKeptWhenTheTransactionCommits() {
        // Arrange
        UUID keycloakId = UUID.randomUUID();
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(keycloakId);

        // Act
        service.create(new CreateStaffAccountRequest("kept@example.com", "Kept Person", StaffRole.STAFF, PASSWORD));

        // Assert
        verify(identity, never()).deleteUser(any());
    }

    @Test
    void roleChangeAppliesOnTheUsersNextRequest() {
        // Arrange
        UUID staffId = seed("sam@example.com", "Sam Taylor", StaffRole.STAFF);
        assertThat(authoritiesOf(staffId)).containsExactly("ROLE_STAFF");

        // Act
        StaffAccountResponse updated = service.update(
                staffId, new UpdateStaffAccountRequest(null, StaffRole.MANAGER, null, versionOf(staffId)));

        // Assert
        assertThat(updated.role()).isEqualTo(StaffRole.MANAGER);
        assertThat(updated.version()).isEqualTo(1);
        assertThat(authoritiesOf(staffId)).containsExactly("ROLE_MANAGER");
        assertThat(events.stream(StaffRoleChanged.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.from()).isEqualTo(StaffRole.STAFF);
                    assertThat(e.to()).isEqualTo(StaffRole.MANAGER);
                    assertThat(e.actorId()).isEqualTo(adminId);
                });
    }

    @Test
    void deactivationDisablesSignInAndRefusesTheNextRequest() {
        // Arrange
        UUID staffId = seed("sam@example.com", "Sam Taylor", StaffRole.STAFF);

        // Act
        service.update(staffId, new UpdateStaffAccountRequest(null, null, false, versionOf(staffId)));

        // Assert
        verify(identity).setEnabled(staffId, false);
        assertThatThrownBy(() -> converter.convert(jwtFor(staffId))).isInstanceOf(AccountInactiveException.class);
        assertThat(events.stream(StaffAccountDeactivated.class)).hasSize(1);
    }

    @Test
    void keycloakFailureDuringDeactivationLeavesTheAccountActive() {
        // Arrange
        UUID staffId = seed("sam@example.com", "Sam Taylor", StaffRole.STAFF);
        doThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "down"))
                .when(identity).setEnabled(any(), eq(false));

        // Act / Assert
        assertThatThrownBy(() -> service.update(
                        staffId, new UpdateStaffAccountRequest(null, null, false, versionOf(staffId))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
        assertThat(repository.findById(staffId).orElseThrow().isActive()).isTrue();
    }

    @Test
    void lastActiveAdminCannotBeDemoted() {
        // Arrange: a second Admin deactivates the first while the first's request is in flight.
        UUID otherAdmin = seed("blake@example.com", "Blake Admin", StaffRole.ADMIN);
        signIn(otherAdmin);
        service.update(adminId, new UpdateStaffAccountRequest(null, null, false, versionOf(adminId)));
        signIn(adminId);

        // Act / Assert
        assertThatThrownBy(() -> service.update(
                        otherAdmin, new UpdateStaffAccountRequest(null, StaffRole.STAFF, null, versionOf(otherAdmin))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.LAST_ADMIN));
        assertThat(repository.countByRoleAndActiveTrue(StaffRole.ADMIN)).isEqualTo(1);
    }

    @Test
    void twoAdminsDemotingEachOtherAtOnceLeaveOneAdmin() throws Exception {
        // Arrange
        UUID otherAdmin = seed("blake@example.com", "Blake Admin", StaffRole.ADMIN);
        long adminVersion = versionOf(adminId);
        long otherVersion = versionOf(otherAdmin);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ErrorCode>> outcomes = new ArrayList<>();
            outcomes.add(pool.submit(demote(adminId, otherAdmin, otherVersion, start)));
            outcomes.add(pool.submit(demote(otherAdmin, adminId, adminVersion, start)));

            // Act
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }

            // Assert: the row lock serializes them; whoever goes second sees one Admin left.
            assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.LAST_ADMIN);
            assertThat(repository.countByRoleAndActiveTrue(StaffRole.ADMIN)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void adminCannotChangeOwnRoleOrDeactivateThemselves() {
        // Arrange: a second Admin exists, so LAST_ADMIN can't be what refuses it.
        seed("blake@example.com", "Blake Admin", StaffRole.ADMIN);
        long version = versionOf(adminId);

        // Act / Assert
        assertThatThrownBy(() -> service.update(
                        adminId, new UpdateStaffAccountRequest(null, StaffRole.MANAGER, null, version)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SELF_MODIFICATION));
        assertThatThrownBy(() -> service.update(adminId, new UpdateStaffAccountRequest(null, null, false, version)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SELF_MODIFICATION));
    }

    @Test
    void adminCanRenameThemselves() {
        // Act
        StaffAccountResponse updated = service.update(
                adminId, new UpdateStaffAccountRequest("Alex Morgan", null, null, versionOf(adminId)));

        // Assert
        assertThat(updated.fullName()).isEqualTo("Alex Morgan");
    }

    @Test
    void staleVersionIsRefused() {
        // Arrange
        UUID staffId = seed("sam@example.com", "Sam Taylor", StaffRole.STAFF);
        long loadedVersion = versionOf(staffId);
        service.update(staffId, new UpdateStaffAccountRequest("Sam T.", null, null, loadedVersion));

        // Act / Assert
        assertThatThrownBy(() -> service.update(
                        staffId, new UpdateStaffAccountRequest(null, StaffRole.MANAGER, null, loadedVersion)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.STALE_VERSION));
        assertThat(repository.findById(staffId).orElseThrow().getRole()).isEqualTo(StaffRole.STAFF);
    }

    @Test
    void passwordResetSetsATemporaryPassword() {
        // Arrange
        UUID staffId = seed("sam@example.com", "Sam Taylor", StaffRole.STAFF);

        // Act
        service.resetPassword(staffId, new PasswordResetRequest(PASSWORD));

        // Assert
        verify(identity, times(1)).resetPassword(staffId, PASSWORD, true);
    }

    @Test
    void listFiltersByRoleAndSortsByName() {
        // Arrange
        seed("zoe@example.com", "Zoe Staff", StaffRole.STAFF);
        seed("bea@example.com", "Bea Staff", StaffRole.STAFF);
        seed("max@example.com", "Max Manager", StaffRole.MANAGER);

        // Act
        var page = service.list(StaffRole.STAFF, true, 0, 20);

        // Assert
        assertThat(page.items()).extracting(StaffAccountResponse::fullName).containsExactly("Bea Staff", "Zoe Staff");
        assertThat(page.totalItems()).isEqualTo(2);
    }

    // ---- helpers ----

    private UUID seed(String email, String fullName, StaffRole role) {
        UUID id = UUID.randomUUID();
        service.ensureSystemAccount(id, email, fullName, role);
        return id;
    }

    private long versionOf(UUID id) {
        return repository.findById(id).orElseThrow().getVersion();
    }

    private void signIn(UUID id) {
        StaffAccountEntity row = repository.findById(id).orElseThrow();
        StaffPrincipal principal = new StaffPrincipal(id, row.getEmail(), row.getFullName(), row.getRole());
        SecurityContextHolder.getContext().setAuthentication(new StaffAuthenticationToken(jwtFor(id), principal));
    }

    private List<String> authoritiesOf(UUID id) {
        return converter.convert(jwtFor(id)).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private Callable<ErrorCode> demote(UUID actor, UUID target, long version, CountDownLatch start) {
        return () -> {
            signIn(actor);
            try {
                start.await();
                service.update(target, new UpdateStaffAccountRequest(null, StaffRole.STAFF, null, version));
                return null;
            } catch (DomainException e) {
                return e.code();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private static Jwt jwtFor(UUID id) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(id.toString()).build();
    }
}
