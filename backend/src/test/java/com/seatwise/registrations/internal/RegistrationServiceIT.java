package com.seatwise.registrations.internal;

import static com.seatwise.support.Catalogue.attendee;
import static com.seatwise.support.Catalogue.edit;
import static com.seatwise.support.Catalogue.workshop;
import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.registrations.AttendeeRegistered;
import com.seatwise.registrations.AttendeeWaitlisted;
import com.seatwise.registrations.RegistrationCancelled;
import com.seatwise.registrations.RegistrationStatus;
import com.seatwise.registrations.WaitlistPromoted;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.internal.CancelWorkshopRequest;
import com.seatwise.workshops.internal.WorkshopRequest;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Registration and waitlist rules against a real PostgreSQL (unique index, CHECKs, trigger). */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@RecordApplicationEvents
class RegistrationServiceIT {

    private static final Instant NEXT_WEEK = MONDAY_MORNING.plus(Duration.ofDays(7));

    @Autowired
    private RegistrationService service;

    @Autowired
    private WorkshopService workshops;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationEvents events;

    private StaffPrincipal manager;
    private StaffPrincipal staff;

    @BeforeEach
    void startWithAManagerAndAStaffMember() {
        TestStaff.wipeCatalogue(jdbc);
        clock.set(MONDAY_MORNING);
        manager = TestStaff.insert(jdbc, "Morgan Reyes", StaffRole.MANAGER);
        staff = TestStaff.insert(jdbc, "Sam Taylor", StaffRole.STAFF);
        TestStaff.signIn(manager);
    }

    @AfterEach
    void signOut() {
        TestStaff.signOut();
    }

    @Test
    void registerTakesASeatAndRecordsWhoAndWhen() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 10);
        TestStaff.signIn(staff);

        // Act
        RegistrationResponse registered = service.register(
                workshopId, attendee("  Dana Lee ", " Dana.Lee@Example.com ", false));

        // Assert
        assertThat(registered.status()).isEqualTo(RegistrationStatus.ACTIVE);
        assertThat(registered.attendeeName()).isEqualTo("Dana Lee");
        assertThat(registered.attendeeEmail()).isEqualTo("Dana.Lee@Example.com");
        assertThat(registered.registeredBy().fullName()).isEqualTo("Sam Taylor");
        assertThat(registered.registeredAt()).isEqualTo(MONDAY_MORNING);
        assertThat(registered.waitlistPosition()).isNull();
        assertThat(seatsTaken(workshopId)).isEqualTo(1);
        assertThat(events.stream(AttendeeRegistered.class))
                .singleElement()
                .satisfies(e -> assertThat(e.actorId()).isEqualTo(staff.id()));
    }

    @Test
    void duplicateEmailIsRefusedCaseInsensitivelyAndTakesNoSeat() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 10);
        service.register(workshopId, attendee("Dana Lee", "dana@example.com", false));

        // Act / Assert
        assertThatThrownBy(() -> service.register(workshopId, attendee("Dana Again", "  DANA@Example.COM", false)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_REGISTRATION));
        assertThat(seatsTaken(workshopId)).as("the claimed seat rolled back with the insert").isEqualTo(1);
        assertThat(rowCount(workshopId)).isEqualTo(1);
    }

    @Test
    void duplicateOfAWaitlistedAttendeeIsRefusedToo() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        service.register(workshopId, attendee(1));
        service.register(workshopId, attendee("Bea Waiting", "bea@example.com", true));

        // Act / Assert
        assertThatThrownBy(() -> service.register(workshopId, attendee("Bea Waiting", "BEA@example.com", true)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_REGISTRATION));
    }

    @Test
    void cancelledAttendeeCanBookAgain() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 10);
        RegistrationResponse first = service.register(workshopId, attendee("Dana Lee", "dana@example.com", false));
        service.cancel(first.id(), null);

        // Act
        RegistrationResponse again = service.register(workshopId, attendee("Dana Lee", "dana@example.com", false));

        // Assert
        assertThat(again.status()).isEqualTo(RegistrationStatus.ACTIVE);
        assertThat(seatsTaken(workshopId)).isEqualTo(1);
    }

    @Test
    void registrationOnACancelledWorkshopIsRefused() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 10);
        workshops.cancel(workshopId, new CancelWorkshopRequest(null));

        // Act / Assert
        assertNotOpen(workshopId, "cancelled");
    }

    @Test
    void registrationOnceTheWorkshopHasStartedIsRefused() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 10);
        clock.set(NEXT_WEEK);

        // Act / Assert
        assertNotOpen(workshopId, "started");
    }

    @Test
    void registrationOnAFinishedWorkshopIsRefused() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 10);
        clock.set(NEXT_WEEK.plus(Duration.ofDays(1)));

        // Act / Assert
        assertNotOpen(workshopId, "finished");
    }

    @Test
    void registrationOnAnUnknownWorkshopIsNotFound() {
        // Act / Assert
        assertThatThrownBy(() -> service.register(UUID.randomUUID(), attendee(1)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fullWorkshopWithoutTheWaitlistIsRefusedAndNothingIsSaved() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        service.register(workshopId, attendee(1));

        // Act / Assert
        assertThatThrownBy(() -> service.register(workshopId, attendee(2)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.WORKSHOP_FULL));
        assertThat(rowCount(workshopId)).isEqualTo(1);
        assertThat(seatsTaken(workshopId)).isEqualTo(1);
    }

    @Test
    void fullWorkshopQueuesWaitlistedPeopleInArrivalOrderWithoutTakingSeats() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        service.register(workshopId, attendee(1));

        // Act
        RegistrationResponse first = service.register(workshopId, attendee("Bea", "bea@example.com", true));
        clock.advance(Duration.ofMinutes(1));
        RegistrationResponse second = service.register(workshopId, attendee("Cal", "cal@example.com", true));

        // Assert
        assertThat(first.status()).isEqualTo(RegistrationStatus.WAITLISTED);
        assertThat(first.waitlistPosition()).isEqualTo(1);
        assertThat(second.waitlistPosition()).isEqualTo(2);
        assertThat(seatsTaken(workshopId)).isEqualTo(1);
        assertThat(workshops.get(workshopId).waitlistCount()).isEqualTo(2);
        assertThat(events.stream(AttendeeWaitlisted.class)).hasSize(2);
    }

    @Test
    void waitlistIsNotOfferedWhenTheWorkshopHasSeats() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 5);

        // Act
        RegistrationResponse registered = service.register(workshopId, attendee("Bea", "bea@example.com", true));

        // Assert
        assertThat(registered.status()).isEqualTo(RegistrationStatus.ACTIVE);
    }

    @Test
    void cancellingAnActiveRegistrationFreesTheSeatImmediately() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        RegistrationResponse first = service.register(workshopId, attendee(1));

        // Act
        CancelResult result = service.cancel(first.id(), new CancelRegistrationRequest("Feeling unwell"));

        // Assert
        assertThat(result.cancelled().status()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(result.promoted()).isNull();
        assertThat(seatsTaken(workshopId)).isZero();
        assertThat(service.register(workshopId, attendee(2)).status()).isEqualTo(RegistrationStatus.ACTIVE);
        assertThat(events.stream(RegistrationCancelled.class))
                .singleElement()
                .satisfies(e -> assertThat(e.previousStatus()).isEqualTo(RegistrationStatus.ACTIVE));
    }

    @Test
    void cancellingWithAWaitlistHandsTheSeatToTheFirstInLine() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        RegistrationResponse holder = service.register(workshopId, attendee(1));
        RegistrationResponse bea = service.register(workshopId, attendee("Bea", "bea@example.com", true));
        clock.advance(Duration.ofMinutes(1));
        RegistrationResponse cal = service.register(workshopId, attendee("Cal", "cal@example.com", true));
        clock.advance(Duration.ofMinutes(1));

        // Act
        CancelResult result = service.cancel(holder.id(), null);

        // Assert
        assertThat(result.promoted()).isNotNull();
        assertThat(result.promoted().id()).isEqualTo(bea.id());
        assertThat(result.promoted().status()).isEqualTo(RegistrationStatus.ACTIVE);
        assertThat(result.promoted().promotedAt()).isEqualTo(clock.instant());
        assertThat(result.promoted().waitlistPosition()).isNull();
        assertThat(seatsTaken(workshopId)).as("the seat passed straight on").isEqualTo(1);
        assertThat(positionOf(workshopId, cal.id())).isEqualTo(1);
        assertThat(events.stream(WaitlistPromoted.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.registrationId()).isEqualTo(bea.id());
                    assertThat(e.freedByRegistrationId()).isEqualTo(holder.id());
                });
    }

    @Test
    void leavingTheWaitlistDoesNotReleaseASeat() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        service.register(workshopId, attendee(1));
        RegistrationResponse waiting = service.register(workshopId, attendee("Bea", "bea@example.com", true));

        // Act
        CancelResult result = service.cancel(waiting.id(), null);

        // Assert
        assertThat(result.cancelled().status()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(seatsTaken(workshopId)).isEqualTo(1);
        assertThat(workshops.get(workshopId).waitlistCount()).isZero();
    }

    @Test
    void cancellingAfterTheStartReleasesTheSeatWithoutPromoting() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 1);
        RegistrationResponse holder = service.register(workshopId, attendee(1));
        RegistrationResponse waiting = service.register(workshopId, attendee("Bea", "bea@example.com", true));
        clock.set(NEXT_WEEK.plus(Duration.ofMinutes(10)));

        // Act
        CancelResult result = service.cancel(holder.id(), null);

        // Assert
        assertThat(result.promoted()).isNull();
        assertThat(seatsTaken(workshopId)).isZero();
        assertThat(statusOf(waiting.id())).isEqualTo("WAITLISTED");
    }

    @Test
    void cancellingTwiceIsAlreadyCancelled() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 5);
        RegistrationResponse registered = service.register(workshopId, attendee(1));
        service.cancel(registered.id(), null);

        // Act / Assert
        assertThatThrownBy(() -> service.cancel(registered.id(), null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_CANCELLED));
        assertThat(seatsTaken(workshopId)).isZero();
    }

    @Test
    void cancellingAnUnknownRegistrationIsNotFound() {
        // Act / Assert
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void historyKeepsCancelledRowsWithWhoWhenAndWhyNewestFirst() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 5);
        TestStaff.signIn(staff);
        RegistrationResponse dana = service.register(workshopId, attendee("Dana", "dana@example.com", false));
        clock.advance(Duration.ofMinutes(5));
        service.register(workshopId, attendee("Eli", "eli@example.com", false));
        clock.advance(Duration.ofMinutes(5));
        TestStaff.signIn(manager);
        service.cancel(dana.id(), new CancelRegistrationRequest("  Can't make this date  "));

        // Act
        List<RegistrationResponse> history = service.history(workshopId, null);
        List<RegistrationResponse> cancelledOnly = service.history(workshopId, RegistrationStatus.CANCELLED);

        // Assert
        assertThat(history).extracting(RegistrationResponse::attendeeName).containsExactly("Eli", "Dana");
        RegistrationResponse cancelled = history.get(1);
        assertThat(cancelled.status()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(cancelled.registeredBy().fullName()).isEqualTo("Sam Taylor");
        assertThat(cancelled.registeredAt()).isEqualTo(MONDAY_MORNING);
        assertThat(cancelled.cancelledBy().fullName()).isEqualTo("Morgan Reyes");
        assertThat(cancelled.cancelledAt()).isEqualTo(MONDAY_MORNING.plus(Duration.ofMinutes(10)));
        assertThat(cancelled.cancellationReason()).isEqualTo("Can't make this date");
        assertThat(history.get(0).cancelledBy()).isNull();
        assertThat(cancelledOnly).extracting(RegistrationResponse::id).containsExactly(dana.id());
    }

    @Test
    void historyOfAnUnknownWorkshopIsNotFound() {
        // Act / Assert
        assertThatThrownBy(() -> service.history(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void raisingTheCapacityGivesTheNewSeatsToTheWaitlistFirst() {
        // Arrange
        WorkshopRequest request = workshop("POT-0412", NEXT_WEEK, 1);
        UUID workshopId = workshops.create(request).id();
        service.register(workshopId, attendee(1));
        RegistrationResponse bea = service.register(workshopId, attendee("Bea", "bea@example.com", true));
        clock.advance(Duration.ofMinutes(1));
        RegistrationResponse cal = service.register(workshopId, attendee("Cal", "cal@example.com", true));
        clock.advance(Duration.ofMinutes(1));
        RegistrationResponse dee = service.register(workshopId, attendee("Dee", "dee@example.com", true));

        // Act
        var updated = workshops.update(workshopId, edit(request, 3, workshops.get(workshopId).version()));

        // Assert
        assertThat(updated.seatsTaken()).isEqualTo(3);
        assertThat(updated.waitlistCount()).isEqualTo(1);
        assertThat(statusOf(bea.id())).isEqualTo("ACTIVE");
        assertThat(statusOf(cal.id())).isEqualTo("ACTIVE");
        assertThat(statusOf(dee.id())).isEqualTo("WAITLISTED");
        assertThat(events.stream(WaitlistPromoted.class)).hasSize(2);
    }

    @Test
    void deletingARegistrationIsRefusedByTheDatabase() {
        // Arrange
        UUID workshopId = schedule("POT-0412", 5);
        RegistrationResponse registered = service.register(workshopId, attendee(1));

        // Act / Assert
        assertThatThrownBy(() -> jdbc.update("DELETE FROM registration WHERE id = ?", registered.id()))
                .hasMessageContaining("registrations are permanent history");
        assertThat(rowCount(workshopId)).isEqualTo(1);
    }

    // ---- helpers ----

    private UUID schedule(String code, int capacity) {
        return workshops.create(workshop(code, NEXT_WEEK, capacity)).id();
    }

    private void assertNotOpen(UUID workshopId, String reasonWord) {
        assertThatThrownBy(() -> service.register(workshopId, attendee("Late", "late@example.com", true)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.WORKSHOP_NOT_OPEN);
                    assertThat(e.detail()).contains(reasonWord);
                });
        assertThat(rowCount(workshopId)).isZero();
        assertThat(seatsTaken(workshopId)).isZero();
    }

    private Integer positionOf(UUID workshopId, UUID registrationId) {
        return service.history(workshopId, RegistrationStatus.WAITLISTED).stream()
                .filter(r -> r.id().equals(registrationId))
                .findFirst()
                .orElseThrow()
                .waitlistPosition();
    }

    private int seatsTaken(UUID workshopId) {
        return jdbc.queryForObject("SELECT seats_taken FROM workshop WHERE id = ?", Integer.class, workshopId);
    }

    private int rowCount(UUID workshopId) {
        return jdbc.queryForObject("SELECT count(*) FROM registration WHERE workshop_id = ?", Integer.class,
                workshopId);
    }

    private String statusOf(UUID registrationId) {
        return jdbc.queryForObject("SELECT status FROM registration WHERE id = ?", String.class, registrationId);
    }
}
