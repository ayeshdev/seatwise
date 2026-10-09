package com.seatwise.audit.internal;

import static com.seatwise.support.Catalogue.attendee;
import static com.seatwise.support.Catalogue.edit;
import static com.seatwise.support.Catalogue.workshop;
import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.seatwise.accounts.internal.CreateStaffAccountRequest;
import com.seatwise.accounts.internal.IdentityProvisioner;
import com.seatwise.accounts.internal.PasswordResetRequest;
import com.seatwise.accounts.internal.StaffAccountService;
import com.seatwise.accounts.internal.UpdateStaffAccountRequest;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.registrations.internal.CancelRegistrationRequest;
import com.seatwise.registrations.internal.RegistrationResponse;
import com.seatwise.registrations.internal.RegistrationService;
import com.seatwise.support.Catalogue;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.internal.CancelWorkshopRequest;
import com.seatwise.workshops.internal.WorkshopRequest;
import com.seatwise.workshops.internal.WorkshopResponse;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every domain event leaves exactly one audit row, in the same transaction as
 * the change (FR-AUD-01..03), against a real PostgreSQL. Keycloak is mocked.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class AuditRecorderIT {

    private static final Instant NEXT_WEEK = MONDAY_MORNING.plus(Duration.ofDays(7));

    private static final TypeReference<Map<String, Map<String, Object>>> CHANGES = new TypeReference<>() {};

    @Autowired
    private WorkshopService workshops;

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private StaffAccountService accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactions;

    @MockitoBean
    private IdentityProvisioner identity;

    private StaffPrincipal manager;

    @BeforeEach
    void startWithASignedInManager() {
        // CASCADE also empties audit_event, whose actor_id references staff_account.
        TestStaff.wipeCatalogue(jdbc);
        clock.set(MONDAY_MORNING);
        manager = TestStaff.insert(jdbc, "Morgan Reyes", StaffRole.MANAGER);
        TestStaff.signIn(manager);
    }

    @AfterEach
    void signOut() {
        TestStaff.signOut();
    }

    // ---- workshops ----

    @Test
    void schedulingRecordsTheInitialValues() {
        // Act
        WorkshopResponse created = workshops.create(Catalogue.workshop(
                "POT-0412", "Pottery wheel basics", "Amara Silva", Catalogue.NORTHSIDE, NEXT_WEEK, 12));

        // Assert
        Row row = single(created.id(), "CREATED");
        assertThat(row.entityType()).isEqualTo("WORKSHOP");
        assertThat(row.workshopId()).isEqualTo(created.id());
        assertThat(row.actorId()).isEqualTo(manager.id());
        assertThat(row.occurredAt()).isEqualTo(MONDAY_MORNING);
        assertThat(row.summary()).isEqualTo("Scheduled POT-0412 Pottery wheel basics");
        assertThat(row.changes().get("location")).containsEntry("from", null).containsEntry("to", "Northside Studio");
        assertThat(row.changes().get("capacity")).containsEntry("to", 12);
        assertThat(row.changes().get("startsAt")).containsEntry("to", NEXT_WEEK.toString());
    }

    @Test
    void anEditWritesExactlyOneUpdatedRowWithTheDiff() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 12);
        WorkshopResponse created = workshops.create(original);
        WorkshopRequest changed = new WorkshopRequest(original.code(), original.title(), original.description(),
                "Blake Tutor", Catalogue.RIVERSIDE, original.startsAt(), original.endsAt(), 16, created.version());

        // Act
        workshops.update(created.id(), changed);

        // Assert
        Row row = single(created.id(), "UPDATED");
        assertThat(row.entityType()).isEqualTo("WORKSHOP");
        assertThat(row.workshopId()).isEqualTo(created.id());
        assertThat(row.actorId()).isEqualTo(manager.id());
        assertThat(row.summary()).isEqualTo("Changed instructor from Alex Instructor to Blake Tutor, "
                + "location from Northside Studio to Riverside Hall and capacity from 12 to 16");
        assertThat(row.changes()).containsOnlyKeys("instructor", "location", "capacity");
        assertThat(row.changes().get("capacity")).containsEntry("from", 12).containsEntry("to", 16);
        assertThat(row.changes().get("instructor"))
                .containsEntry("from", "Alex Instructor").containsEntry("to", "Blake Tutor");
        assertThat(row.changes().get("location"))
                .containsEntry("from", "Northside Studio").containsEntry("to", "Riverside Hall");
    }

    @Test
    void movingTheStartNamesTheTimesInTheCentreTimezone() {
        // Arrange: 2026-10-19 08:00 UTC is 09:00 in London (BST).
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 12);
        WorkshopResponse created = workshops.create(original);
        Duration hour = Duration.ofHours(1);
        WorkshopRequest moved = new WorkshopRequest(original.code(), original.title(), original.description(),
                original.instructor(), original.locationId(), original.startsAt().plus(hour),
                original.endsAt().plus(hour), original.capacity(), created.version());

        // Act
        workshops.update(created.id(), moved);

        // Assert
        Row row = single(created.id(), "UPDATED");
        assertThat(row.summary()).isEqualTo(
                "Changed start from Mon 19 Oct, 09:00 to Mon 19 Oct, 10:00 and end from Mon 19 Oct, 11:00 "
                        + "to Mon 19 Oct, 12:00");
        assertThat(row.changes().get("startsAt"))
                .containsEntry("from", NEXT_WEEK.toString())
                .containsEntry("to", NEXT_WEEK.plus(hour).toString());
    }

    @Test
    void aSaveWithoutChangesWritesNothing() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 12);
        WorkshopResponse created = workshops.create(original);

        // Act
        workshops.update(created.id(), edit(original, 12, created.version()));

        // Assert
        assertThat(rows(created.id(), "UPDATED")).isEmpty();
    }

    @Test
    void anEditRefusedForCapacityBelowSeatsTakenWritesNothing() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 5);
        WorkshopResponse created = workshops.create(original);
        registrations.register(created.id(), attendee(1));
        registrations.register(created.id(), attendee(2));

        // Act / Assert
        assertThatThrownBy(() -> workshops.update(created.id(), edit(original, 1, created.version())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAPACITY_BELOW_TAKEN));
        assertThat(rows(created.id(), "UPDATED")).isEmpty();
    }

    @Test
    void anEditWhoseTransactionRollsBackLeavesNoRow() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 12);
        WorkshopResponse created = workshops.create(original);

        // Act: the edit itself succeeds (event published, row written), then the outer transaction rolls back.
        transactions.executeWithoutResult(status -> {
            workshops.update(created.id(), edit(original, 20, created.version()));
            assertThat(rows(created.id(), "UPDATED")).as("written inside the transaction").hasSize(1);
            status.setRollbackOnly();
        });

        // Assert
        assertThat(workshops.get(created.id()).capacity()).isEqualTo(12);
        assertThat(rows(created.id(), "UPDATED")).isEmpty();
    }

    @Test
    void cancellingAWorkshopNamesItAndTheReason() {
        // Arrange
        WorkshopResponse created = workshops.create(Catalogue.workshop(
                "POT-0412", "Pottery wheel basics", "Amara Silva", Catalogue.NORTHSIDE, NEXT_WEEK, 12));

        // Act
        workshops.cancel(created.id(), new CancelWorkshopRequest("Instructor unwell"));

        // Assert
        Row row = single(created.id(), "CANCELLED");
        assertThat(row.entityType()).isEqualTo("WORKSHOP");
        assertThat(row.summary()).isEqualTo("Cancelled POT-0412 Pottery wheel basics — reason: Instructor unwell");
        assertThat(row.changes().get("status")).containsEntry("from", "SCHEDULED").containsEntry("to", "CANCELLED");
        assertThat(row.changes().get("cancellationReason")).containsEntry("to", "Instructor unwell");
    }

    // ---- registrations ----

    @Test
    void registerWaitlistCancelAndPromoteRowsCarryTheWorkshop() {
        // Arrange
        UUID workshopId = workshops.create(workshop("POT-0412", NEXT_WEEK, 1)).id();
        StaffPrincipal staff = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);
        TestStaff.signIn(staff);

        // Act
        RegistrationResponse priya = registrations.register(
                workshopId, attendee("Priya Shah", "priya@example.com", false));
        RegistrationResponse wendy = registrations.register(
                workshopId, attendee("Wendy Wait", "wendy@example.com", true));
        clock.advance(Duration.ofMinutes(1));
        registrations.cancel(priya.id(), new CancelRegistrationRequest("Feeling unwell"));

        // Assert
        Row registered = single(priya.id(), "REGISTERED");
        assertThat(registered.summary()).isEqualTo("Registered Priya Shah (priya@example.com)");
        assertThat(registered.changes().get("attendeeEmail")).containsEntry("to", "priya@example.com");
        Row waitlisted = single(wendy.id(), "WAITLISTED");
        assertThat(waitlisted.summary()).isEqualTo("Added Wendy Wait (wendy@example.com) to the waitlist");
        Row cancelled = single(priya.id(), "CANCELLED");
        assertThat(cancelled.summary()).isEqualTo("Cancelled Priya Shah's booking — reason: Feeling unwell");
        assertThat(cancelled.changes().get("status")).containsEntry("from", "ACTIVE").containsEntry("to", "CANCELLED");
        Row promoted = single(wendy.id(), "PROMOTED");
        assertThat(promoted.summary()).isEqualTo("Moved Wendy Wait up from the waitlist");
        assertThat(List.of(registered, waitlisted, cancelled, promoted)).allSatisfy(row -> {
            assertThat(row.entityType()).isEqualTo("REGISTRATION");
            assertThat(row.workshopId()).isEqualTo(workshopId);
            assertThat(row.actorId()).isEqualTo(staff.id());
        });
    }

    @Test
    void leavingTheWaitlistSaysSo() {
        // Arrange
        UUID workshopId = workshops.create(workshop("POT-0412", NEXT_WEEK, 1)).id();
        registrations.register(workshopId, attendee(1));
        RegistrationResponse wendy = registrations.register(
                workshopId, attendee("Wendy Wait", "wendy@example.com", true));

        // Act
        registrations.cancel(wendy.id(), null);

        // Assert
        assertThat(single(wendy.id(), "CANCELLED").summary()).isEqualTo("Cancelled Wendy Wait's waitlist place");
    }

    @Test
    void aCapacityIncreaseRecordsTheEditBeforeThePromotionsItCaused() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 1);
        WorkshopResponse created = workshops.create(original);
        registrations.register(created.id(), attendee(1));
        RegistrationResponse wendy = registrations.register(
                created.id(), attendee("Wendy Wait", "wendy@example.com", true));

        // Act
        workshops.update(created.id(), edit(original, 2, created.version()));

        // Assert
        Row updated = single(created.id(), "UPDATED");
        Row promoted = single(wendy.id(), "PROMOTED");
        assertThat(promoted.workshopId()).isEqualTo(created.id());
        assertThat(updated.id()).isLessThan(promoted.id());
    }

    // ---- staff accounts ----

    @Test
    void creatingAnAccountRecordsWhoCreatedItWithoutThePassword() {
        // Arrange
        StaffPrincipal admin = signInAdmin();
        UUID newId = UUID.randomUUID();
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(newId);

        // Act
        accounts.create(new CreateStaffAccountRequest(
                "Dana.Lee@Example.com", "Dana Lee", StaffRole.STAFF, "Temporary#Pass1"));

        // Assert
        Row row = single(newId, "CREATED");
        assertThat(row.entityType()).isEqualTo("STAFF_ACCOUNT");
        assertThat(row.workshopId()).isNull();
        assertThat(row.actorId()).isEqualTo(admin.id());
        assertThat(row.summary()).isEqualTo("Created the account for Dana Lee (dana.lee@example.com) as Staff");
        assertThat(row.changes().get("role")).containsEntry("to", "STAFF");
        assertThat(row.rawChanges()).doesNotContain("Temporary#Pass1");
    }

    @Test
    void aRoleChangeIsRecordedWithFromAndTo() {
        // Arrange
        signInAdmin();
        StaffPrincipal sam = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);

        // Act
        accounts.update(sam.id(), new UpdateStaffAccountRequest(null, StaffRole.MANAGER, null, versionOf(sam.id())));

        // Assert
        Row row = single(sam.id(), "ROLE_CHANGED");
        assertThat(row.entityType()).isEqualTo("STAFF_ACCOUNT");
        assertThat(row.entityId()).isEqualTo(sam.id());
        assertThat(row.workshopId()).isNull();
        assertThat(row.summary()).isEqualTo("Changed role from Staff to Manager");
        assertThat(row.changes()).containsOnlyKeys("role");
        assertThat(row.changes().get("role")).containsEntry("from", "STAFF").containsEntry("to", "MANAGER");
    }

    @Test
    void renameAndDeactivationInOneSaveAreTwoRows() {
        // Arrange
        signInAdmin();
        StaffPrincipal sam = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);

        // Act
        accounts.update(sam.id(), new UpdateStaffAccountRequest("Samuel Taylor", null, false, versionOf(sam.id())));

        // Assert
        assertThat(single(sam.id(), "RENAMED").summary()).isEqualTo("Renamed from Sam Taylor to Samuel Taylor");
        Row deactivated = single(sam.id(), "DEACTIVATED");
        assertThat(deactivated.summary()).isEqualTo("Deactivated the account");
        assertThat(deactivated.changes().get("active")).containsEntry("from", true).containsEntry("to", false);
    }

    @Test
    void aDeactivationThatKeycloakRefusesLeavesNoRow() {
        // Arrange: the event is published (and the row written) before Keycloak is called.
        signInAdmin();
        StaffPrincipal sam = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);
        doThrow(new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "down"))
                .when(identity).setEnabled(any(), eq(false));

        // Act / Assert
        assertThatThrownBy(() -> accounts.update(
                        sam.id(), new UpdateStaffAccountRequest(null, null, false, versionOf(sam.id()))))
                .isInstanceOf(DomainException.class);
        assertThat(rows(sam.id(), "DEACTIVATED")).isEmpty();
    }

    @Test
    void aPasswordResetIsRecordedWithoutThePassword() {
        // Arrange
        signInAdmin();
        StaffPrincipal sam = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);

        // Act
        accounts.resetPassword(sam.id(), new PasswordResetRequest("Another#Pass22"));

        // Assert
        Row row = single(sam.id(), "PASSWORD_RESET");
        assertThat(row.summary()).isEqualTo("Set a new temporary password");
        assertThat(row.changes()).isEmpty();
        assertThat(row.rawChanges()).isEqualTo("{}");
    }

    @Test
    void systemSeedingIsRecordedWithoutAnActor() {
        // Arrange
        TestStaff.signOut();
        UUID id = UUID.randomUUID();

        // Act
        accounts.ensureSystemAccount(id, "admin@seatwise.local", "Centre Administrator", StaffRole.ADMIN);

        // Assert
        Row row = single(id, "CREATED");
        assertThat(row.actorId()).isNull();
        assertThat(row.summary())
                .isEqualTo("Created the account for Centre Administrator (admin@seatwise.local) as Admin");
    }

    // ---- immutability ----

    @Test
    void theDatabaseRefusesToChangeOrDeleteAnAuditRow() {
        // Arrange
        UUID workshopId = workshops.create(workshop("POT-0412", NEXT_WEEK, 12)).id();
        long id = single(workshopId, "CREATED").id();

        // Act / Assert
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET summary = 'tampered' WHERE id = ?", id))
                .hasMessageContaining("audit events are immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", id))
                .hasMessageContaining("audit events are immutable");
        assertThat(single(workshopId, "CREATED").summary()).startsWith("Scheduled POT-0412");
    }

    // ---- helpers ----

    private record Row(
            long id,
            Instant occurredAt,
            UUID actorId,
            String entityType,
            UUID entityId,
            UUID workshopId,
            String action,
            String summary,
            String rawChanges,
            Map<String, Map<String, Object>> changes) {}

    private Row single(UUID entityId, String action) {
        List<Row> rows = rows(entityId, action);
        assertThat(rows).as("%s rows for %s", action, entityId).hasSize(1);
        return rows.getFirst();
    }

    private List<Row> rows(UUID entityId, String action) {
        return jdbc.query("""
                        SELECT id, occurred_at, actor_id, entity_type, entity_id, workshop_id, action, summary,
                               changes::text AS changes
                          FROM audit_event
                         WHERE entity_id = ? AND action = ?
                         ORDER BY id""",
                (rs, n) -> new Row(
                        rs.getLong("id"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("entity_type"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getObject("workshop_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("summary"),
                        rs.getString("changes"),
                        json.readValue(rs.getString("changes"), CHANGES)),
                entityId, action);
    }

    private StaffPrincipal signInAdmin() {
        StaffPrincipal admin = TestStaff.insert(jdbc, "Alex Admin", StaffRole.ADMIN);
        TestStaff.signIn(admin);
        return admin;
    }

    private long versionOf(UUID staffId) {
        return jdbc.queryForObject("SELECT version FROM staff_account WHERE id = ?", Long.class, staffId);
    }
}
