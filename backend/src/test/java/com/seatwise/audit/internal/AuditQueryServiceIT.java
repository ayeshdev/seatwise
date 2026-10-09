package com.seatwise.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatwise.accounts.internal.IdentityProvisioner;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.common.web.PageResponse;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Reading the audit trail (FR-AUD-04, -05): filters, newest-first order, the
 * inclusive date range in the centre's timezone (Europe/London), and the
 * data-dependent role rule of architecture section 8. Rows are inserted
 * directly so their times are exact; recording is covered by AuditRecorderIT.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class AuditQueryServiceIT {

    private static final Instant T1 = Instant.parse("2026-10-12T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-12T09:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-12T10:00:00Z");

    @Autowired
    private AuditQueryService service;

    @Autowired
    private JdbcTemplate jdbc;

    // Same configuration as AuditRecorderIT, so both share one application context.
    @MockitoBean
    private IdentityProvisioner identity;

    private StaffPrincipal admin;
    private StaffPrincipal manager;
    private StaffPrincipal staff;

    @BeforeEach
    void startWithOneOfEachRole() {
        // CASCADE also empties audit_event, whose actor_id references staff_account.
        TestStaff.wipeCatalogue(jdbc);
        admin = TestStaff.insert(jdbc, "Alex Admin", StaffRole.ADMIN);
        manager = TestStaff.insert(jdbc, "Morgan Reyes", StaffRole.MANAGER);
        staff = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);
    }

    @AfterEach
    void signOut() {
        TestStaff.signOut();
    }

    // ---- order, actors, shape ----

    @Test
    void newestFirstWithTheActorNamedAndTheSystemAsNull() {
        // Arrange
        UUID workshop = UUID.randomUUID();
        long first = insert(T1, manager.id(), AuditEntityType.WORKSHOP, workshop, workshop, AuditAction.CREATED,
                "{\"capacity\":{\"from\":null,\"to\":12}}");
        long second = insert(T2, null, AuditEntityType.WORKSHOP, workshop, workshop, AuditAction.UPDATED, "{}");
        long third = insert(T3, staff.id(), AuditEntityType.WORKSHOP, workshop, workshop, AuditAction.UPDATED,
                "{\"capacity\":{\"from\":12,\"to\":16}}");
        TestStaff.signIn(manager);

        // Act
        PageResponse<AuditEventResponse> page = service.list(query(null, null, null, null, null));

        // Assert
        assertThat(page.items()).extracting(AuditEventResponse::id).containsExactly(third, second, first);
        assertThat(page.totalItems()).isEqualTo(3);
        AuditEventResponse newest = page.items().getFirst();
        assertThat(newest.actor().id()).isEqualTo(staff.id());
        assertThat(newest.actor().fullName()).isEqualTo("Sam Taylor");
        assertThat(newest.occurredAt()).isEqualTo(T3);
        assertThat(newest.workshopId()).isEqualTo(workshop);
        assertThat(newest.changes()).containsEntry("capacity", new AuditChange(12, 16));
        assertThat(page.items().get(1).actor()).as("the system").isNull();
        assertThat(page.items().get(1).changes()).isEmpty();
        assertThat(page.items().get(2).changes().get("capacity").from()).isNull();
    }

    @Test
    void sameInstantEventsKeepTheirInsertOrderNewestFirst() {
        // Arrange
        UUID workshop = UUID.randomUUID();
        long edit = insert(T1, manager.id(), AuditEntityType.WORKSHOP, workshop, workshop, AuditAction.UPDATED, "{}");
        long promotion = insert(T1, manager.id(), AuditEntityType.REGISTRATION, UUID.randomUUID(), workshop,
                AuditAction.PROMOTED, "{}");
        TestStaff.signIn(manager);

        // Act / Assert
        assertThat(service.list(query(null, null, workshop, null, null)).items())
                .extracting(AuditEventResponse::id)
                .containsExactly(promotion, edit);
    }

    @Test
    void pagesAreZeroBasedAndCountEveryMatch() {
        // Arrange
        UUID workshop = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            insert(T1.plusSeconds(i), manager.id(), AuditEntityType.WORKSHOP, workshop, workshop,
                    AuditAction.UPDATED, "{}");
        }
        TestStaff.signIn(manager);

        // Act
        PageResponse<AuditEventResponse> page =
                service.list(new AuditQuery(null, null, null, null, null, 1, 2));

        // Assert
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.items()).extracting(AuditEventResponse::occurredAt)
                .containsExactly(T1.plusSeconds(2), T1.plusSeconds(1));
    }

    // ---- filters ----

    @Test
    void workshopIdIsThatWorkshopsTimelineIncludingItsBookings() {
        // Arrange
        UUID pottery = UUID.randomUUID();
        UUID yoga = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        insert(T1, manager.id(), AuditEntityType.WORKSHOP, pottery, pottery, AuditAction.CREATED, "{}");
        insert(T2, staff.id(), AuditEntityType.REGISTRATION, booking, pottery, AuditAction.REGISTERED, "{}");
        insert(T3, manager.id(), AuditEntityType.WORKSHOP, yoga, yoga, AuditAction.CREATED, "{}");
        TestStaff.signIn(staff);

        // Act
        PageResponse<AuditEventResponse> timeline = service.list(query(null, null, pottery, null, null));

        // Assert
        assertThat(timeline.items()).extracting(AuditEventResponse::entityId).containsExactly(booking, pottery);
        assertThat(timeline.items()).allSatisfy(e -> assertThat(e.workshopId()).isEqualTo(pottery));
    }

    @Test
    void entityTypeAndEntityIdNarrowTheResult() {
        // Arrange
        UUID pottery = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        UUID otherBooking = UUID.randomUUID();
        insert(T1, manager.id(), AuditEntityType.WORKSHOP, pottery, pottery, AuditAction.CREATED, "{}");
        insert(T2, staff.id(), AuditEntityType.REGISTRATION, booking, pottery, AuditAction.REGISTERED, "{}");
        insert(T3, staff.id(), AuditEntityType.REGISTRATION, otherBooking, pottery, AuditAction.REGISTERED, "{}");
        TestStaff.signIn(manager);

        // Act
        PageResponse<AuditEventResponse> registrations =
                service.list(query(AuditEntityType.REGISTRATION, null, null, null, null));
        PageResponse<AuditEventResponse> one = service.list(query(null, booking, null, null, null));

        // Assert
        assertThat(registrations.items()).extracting(AuditEventResponse::entityId)
                .containsExactly(otherBooking, booking);
        assertThat(one.items()).extracting(AuditEventResponse::entityId).containsExactly(booking);
    }

    @Test
    void theDateRangeIsInclusiveDaysInTheCentreTimezone() {
        // Arrange: London is on BST (UTC+1) on 12 October 2026.
        UUID workshop = UUID.randomUUID();
        insert(at("2026-10-11T22:59:59"), manager.id(), AuditEntityType.WORKSHOP, workshop, workshop,
                AuditAction.CREATED, "{}"); // 23:59:59 on the 11th, local
        long startOfDay = insert(at("2026-10-11T23:00:00"), manager.id(), AuditEntityType.WORKSHOP, workshop,
                workshop, AuditAction.UPDATED, "{}"); // 00:00 on the 12th, local
        long endOfDay = insert(at("2026-10-12T22:59:59"), manager.id(), AuditEntityType.WORKSHOP, workshop,
                workshop, AuditAction.UPDATED, "{}"); // 23:59:59 on the 12th, local
        insert(at("2026-10-12T23:00:00"), manager.id(), AuditEntityType.WORKSHOP, workshop, workshop,
                AuditAction.CANCELLED, "{}"); // 00:00 on the 13th, local
        TestStaff.signIn(manager);
        LocalDate day = LocalDate.of(2026, 10, 12);

        // Act
        PageResponse<AuditEventResponse> oneDay = service.list(query(null, null, null, day, day));
        PageResponse<AuditEventResponse> fromOnly = service.list(query(null, null, null, day, null));
        PageResponse<AuditEventResponse> toOnly = service.list(query(null, null, null, null, day));

        // Assert
        assertThat(oneDay.items()).extracting(AuditEventResponse::id).containsExactly(endOfDay, startOfDay);
        assertThat(fromOnly.totalItems()).isEqualTo(3);
        assertThat(toOnly.totalItems()).isEqualTo(3);
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsAValidationError() {
        // Arrange
        TestStaff.signIn(manager);

        // Act / Assert
        assertThatThrownBy(() -> service.list(
                        query(null, null, null, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 12))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aPageLargerThanTheMaximumIsAValidationError() {
        // Arrange
        TestStaff.signIn(manager);

        // Act / Assert
        assertThatThrownBy(() -> service.list(new AuditQuery(null, null, null, null, null, 0, 101)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ---- the role rule (architecture section 8) ----

    @Test
    void adminSeesAccountEventsOnlyEvenWithoutAType() {
        // Arrange
        seedOneOfEachType();
        TestStaff.signIn(admin);

        // Act
        PageResponse<AuditEventResponse> page = service.list(query(null, null, null, null, null));
        PageResponse<AuditEventResponse> typed = service.list(query(AuditEntityType.STAFF_ACCOUNT, null, null, null,
                null));

        // Assert
        assertThat(page.items()).extracting(AuditEventResponse::entityType)
                .containsOnly(AuditEntityType.STAFF_ACCOUNT);
        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(typed.items()).extracting(AuditEventResponse::entityId).containsExactly(staff.id());
    }

    @Test
    void adminAskingForWorkshopEventsIsForbidden() {
        // Arrange
        seedOneOfEachType();
        TestStaff.signIn(admin);

        // Act / Assert
        assertForbidden(() -> service.list(query(AuditEntityType.WORKSHOP, null, null, null, null)));
        assertForbidden(() -> service.list(query(AuditEntityType.REGISTRATION, null, null, null, null)));
    }

    @Test
    void adminAskingForAWorkshopTimelineIsForbidden() {
        // Arrange
        UUID workshop = seedOneOfEachType();
        TestStaff.signIn(admin);

        // Act / Assert
        assertForbidden(() -> service.list(query(null, null, workshop, null, null)));
        assertForbidden(() -> service.list(query(AuditEntityType.STAFF_ACCOUNT, null, workshop, null, null)));
    }

    @Test
    void managerAskingForAccountEventsIsForbidden() {
        // Arrange
        seedOneOfEachType();
        TestStaff.signIn(manager);

        // Act / Assert
        assertForbidden(() -> service.list(query(AuditEntityType.STAFF_ACCOUNT, null, null, null, null)));
    }

    @Test
    void staffWithoutATypeSeeOnlyWorkshopAndRegistrationEvents() {
        // Arrange
        seedOneOfEachType();
        TestStaff.signIn(staff);

        // Act
        PageResponse<AuditEventResponse> page = service.list(query(null, null, null, null, null));

        // Assert
        assertThat(page.items()).extracting(AuditEventResponse::entityType)
                .containsExactlyInAnyOrder(AuditEntityType.WORKSHOP, AuditEntityType.REGISTRATION);
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void staffAskingForAnAccountByIdWithoutATypeGetNothing() {
        // Arrange: the id of an account event, but no type, so the desk types apply.
        seedOneOfEachType();
        TestStaff.signIn(staff);

        // Act
        PageResponse<AuditEventResponse> page = service.list(query(null, staff.id(), null, null, null));

        // Assert
        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
    }

    // ---- helpers ----

    /** One event per entity type; returns the workshop id. */
    private UUID seedOneOfEachType() {
        UUID workshop = UUID.randomUUID();
        insert(T1, admin.id(), AuditEntityType.STAFF_ACCOUNT, staff.id(), null, AuditAction.ROLE_CHANGED,
                "{\"role\":{\"from\":\"STAFF\",\"to\":\"MANAGER\"}}");
        insert(T2, manager.id(), AuditEntityType.WORKSHOP, workshop, workshop, AuditAction.CREATED, "{}");
        insert(T3, staff.id(), AuditEntityType.REGISTRATION, UUID.randomUUID(), workshop, AuditAction.REGISTERED,
                "{}");
        return workshop;
    }

    private static AuditQuery query(
            AuditEntityType type, UUID entityId, UUID workshopId, LocalDate from, LocalDate to) {
        return new AuditQuery(type, entityId, workshopId, from, to, 0, AuditQuery.DEFAULT_SIZE);
    }

    private static Instant at(String utcLocalDateTime) {
        return LocalDateTime.parse(utcLocalDateTime).toInstant(ZoneOffset.UTC);
    }

    private long insert(
            Instant occurredAt, UUID actor, AuditEntityType type, UUID entityId, UUID workshopId, AuditAction action,
            String changes) {
        return jdbc.queryForObject("""
                        INSERT INTO audit_event (occurred_at, actor_id, entity_type, entity_id, workshop_id, action,
                                                 summary, changes)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                        RETURNING id""",
                Long.class,
                occurredAt.atOffset(ZoneOffset.UTC), actor, type.name(), entityId, workshopId, action.name(),
                action.name().toLowerCase(Locale.ROOT) + " at " + occurredAt, changes);
    }

    private static void assertForbidden(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
