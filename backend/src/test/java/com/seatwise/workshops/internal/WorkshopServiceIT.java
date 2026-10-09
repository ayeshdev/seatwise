package com.seatwise.workshops.internal;

import static com.seatwise.support.Catalogue.attendee;
import static com.seatwise.support.Catalogue.edit;
import static com.seatwise.support.Catalogue.workshop;
import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.error.FieldErrorItem;
import com.seatwise.common.error.Problems;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.registrations.internal.RegistrationService;
import com.seatwise.support.Catalogue;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.FieldChange;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopScheduled;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.WorkshopUpdated;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
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

/** Workshop rules against a real PostgreSQL, with a clock the test controls. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@RecordApplicationEvents
class WorkshopServiceIT {

    private static final Instant NEXT_WEEK = MONDAY_MORNING.plus(Duration.ofDays(7));

    @Autowired
    private WorkshopService service;

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationEvents events;

    private StaffPrincipal manager;

    @BeforeEach
    void startWithASignedInManager() {
        TestStaff.wipeCatalogue(jdbc);
        clock.set(MONDAY_MORNING);
        manager = TestStaff.insert(jdbc, "Morgan Reyes", StaffRole.MANAGER);
        TestStaff.signIn(manager);
    }

    @AfterEach
    void signOut() {
        TestStaff.signOut();
    }

    @Test
    void createNormalizesTheCodeAndPublishesWorkshopScheduled() {
        // Act
        WorkshopResponse created = service.create(workshop("  pot-0412 ", NEXT_WEEK, 12));

        // Assert
        assertThat(created.code()).isEqualTo("POT-0412");
        assertThat(created.status()).isEqualTo(WorkshopStatus.OPEN);
        assertThat(created.seatsTaken()).isZero();
        assertThat(created.seatsLeft()).isEqualTo(12);
        assertThat(created.waitlistCount()).isZero();
        assertThat(created.version()).isZero();
        assertThat(created.location().name()).isEqualTo("Northside Studio");
        assertThat(created.createdBy().fullName()).isEqualTo("Morgan Reyes");
        assertThat(created.updatedBy().id()).isEqualTo(manager.id());
        assertThat(events.stream(WorkshopScheduled.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.workshopId()).isEqualTo(created.id());
                    assertThat(e.actorId()).isEqualTo(manager.id());
                    assertThat(e.capacity()).isEqualTo(12);
                });
    }

    @Test
    void createRefusesAStartInThePast() {
        // Act / Assert
        assertValidationError(
                () -> service.create(workshop("POT-0001", MONDAY_MORNING.minus(Duration.ofMinutes(1)), 10)),
                "startsAt");
    }

    @Test
    void createRefusesACodeOutsideTheAllowedFormat() {
        // Act / Assert
        assertValidationError(() -> service.create(workshop("P!", NEXT_WEEK, 10)), "code");
    }

    @Test
    void createRefusesAnEndBeforeTheStart() {
        // Arrange
        WorkshopRequest backwards = new WorkshopRequest("POT-0001", "Pottery", null, "Amara Silva",
                Catalogue.NORTHSIDE, NEXT_WEEK, NEXT_WEEK.minus(Duration.ofHours(1)), 10, null);

        // Act / Assert
        assertValidationError(() -> service.create(backwards), "endsAt");
    }

    @Test
    void createRefusesAnUnknownLocation() {
        // Arrange
        WorkshopRequest nowhere = workshop("POT-0001", "Pottery", "Amara Silva", UUID.randomUUID(), NEXT_WEEK, 10);

        // Act / Assert
        assertValidationError(() -> service.create(nowhere), "locationId");
    }

    @Test
    void duplicateCodeIsRefusedWhateverItsCase() {
        // Arrange
        service.create(workshop("POT-0412", NEXT_WEEK, 10));

        // Act / Assert
        assertValidationError(() -> service.create(workshop("pot-0412", NEXT_WEEK, 10)), "code");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workshop", Integer.class)).isEqualTo(1);
    }

    @Test
    void updateWithAStaleVersionIsRefused() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 10);
        WorkshopResponse created = service.create(original);
        service.update(created.id(), edit(original, 11, created.version()));

        // Act / Assert
        assertThatThrownBy(() -> service.update(created.id(), edit(original, 12, created.version())))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.STALE_VERSION);
                    assertThat(e.properties()).containsEntry("currentVersion", 1L);
                });
        assertThat(service.get(created.id()).capacity()).isEqualTo(11);
    }

    @Test
    void updateWithoutAVersionIsRefused() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 10);
        WorkshopResponse created = service.create(original);

        // Act / Assert
        assertValidationError(() -> service.update(created.id(), original), "version");
    }

    @Test
    void capacityBelowTheSeatsTakenIsRefusedWithTheCount() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 10);
        WorkshopResponse created = service.create(original);
        for (int i = 1; i <= 3; i++) {
            registrations.register(created.id(), attendee(i));
        }

        // Act / Assert
        assertThatThrownBy(() -> service.update(created.id(), edit(original, 2, created.version())))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CAPACITY_BELOW_TAKEN);
                    assertThat(e.detail()).isEqualTo("Capacity can't be lower than the 3 seats already taken.");
                    assertThat(e.properties()).containsEntry("seatsTaken", 3);
                });
        assertThat(service.update(created.id(), edit(original, 3, created.version())).status())
                .isEqualTo(WorkshopStatus.FULL);
    }

    @Test
    void updatePublishesOnlyTheFieldsThatChanged() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 10);
        WorkshopResponse created = service.create(original);
        WorkshopRequest renamed = new WorkshopRequest(original.code(), "Pottery wheel basics", original.description(),
                original.instructor(), original.locationId(), original.startsAt(), original.endsAt(), 14,
                created.version());

        // Act
        WorkshopResponse updated = service.update(created.id(), renamed);

        // Assert
        assertThat(updated.title()).isEqualTo("Pottery wheel basics");
        assertThat(updated.version()).isEqualTo(1);
        assertThat(events.stream(WorkshopUpdated.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.changes()).containsOnlyKeys("title", "capacity");
                    assertThat(e.changes().get("capacity")).isEqualTo(new FieldChange(10, 14));
                    assertThat(e.actorId()).isEqualTo(manager.id());
                });
    }

    @Test
    void saveWithoutChangesKeepsTheVersionAndPublishesNothing() {
        // Arrange
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 10);
        WorkshopResponse created = service.create(original);

        // Act
        WorkshopResponse same = service.update(created.id(), edit(original, 10, created.version()));

        // Assert
        assertThat(same.version()).isZero();
        assertThat(events.stream(WorkshopUpdated.class)).isEmpty();
    }

    @Test
    void anEditNeverWritesBackTheSeatCount() {
        // Arrange: the edit form was loaded before two people booked.
        WorkshopRequest original = workshop("POT-0412", NEXT_WEEK, 10);
        WorkshopResponse loaded = service.create(original);
        registrations.register(loaded.id(), attendee(1));
        registrations.register(loaded.id(), attendee(2));

        // Act
        WorkshopResponse updated = service.update(loaded.id(), edit(original, 9, loaded.version()));

        // Assert
        assertThat(updated.seatsTaken()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT seats_taken FROM workshop WHERE id = ?", Integer.class, loaded.id()))
                .isEqualTo(2);
    }

    @Test
    void cancelledWorkshopRefusesNewRegistrationsAndASecondCancel() {
        // Arrange
        WorkshopResponse created = service.create(workshop("POT-0412", NEXT_WEEK, 10));
        registrations.register(created.id(), attendee(1));

        // Act
        WorkshopResponse cancelled = service.cancel(created.id(), new CancelWorkshopRequest("  Instructor unwell "));

        // Assert
        assertThat(cancelled.status()).isEqualTo(WorkshopStatus.CANCELLED);
        assertThat(cancelled.seatsTaken()).as("existing bookings are kept").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM workshop WHERE id = ?", String.class,
                created.id())).isEqualTo("Instructor unwell");
        assertThatThrownBy(() -> registrations.register(created.id(), attendee(2)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.WORKSHOP_NOT_OPEN));
        assertThatThrownBy(() -> service.cancel(created.id(), null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.WORKSHOP_NOT_OPEN));
    }

    @Test
    void finishedWorkshopCannotBeCancelled() {
        // Arrange
        WorkshopResponse created = service.create(workshop("POT-0412", NEXT_WEEK, 10));
        clock.set(NEXT_WEEK.plus(Duration.ofHours(3)));

        // Act / Assert
        assertThatThrownBy(() -> service.cancel(created.id(), null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.WORKSHOP_NOT_OPEN));
    }

    @Test
    void statusIsDerivedFromTheLifecycleTheClockAndTheSeats() {
        // Arrange
        WorkshopResponse created = service.create(workshop("POT-0412", NEXT_WEEK, 1));
        WorkshopResponse other = service.create(workshop("PY-0415", NEXT_WEEK, 5));
        service.cancel(other.id(), null);

        // Act / Assert
        assertThat(service.get(created.id()).status()).isEqualTo(WorkshopStatus.OPEN);
        registrations.register(created.id(), attendee(1));
        assertThat(service.get(created.id()).status()).isEqualTo(WorkshopStatus.FULL);
        clock.set(NEXT_WEEK);
        assertThat(service.get(created.id()).status()).as("starts_at <= now").isEqualTo(WorkshopStatus.IN_PROGRESS);
        clock.set(NEXT_WEEK.plus(Duration.ofHours(2)));
        assertThat(service.get(created.id()).status()).as("ends_at <= now").isEqualTo(WorkshopStatus.COMPLETED);
        assertThat(service.get(other.id()).status()).isEqualTo(WorkshopStatus.CANCELLED);
    }

    @Test
    void detailCountsTheWaitlist() {
        // Arrange
        WorkshopResponse created = service.create(workshop("POT-0412", NEXT_WEEK, 1));
        registrations.register(created.id(), attendee(1));
        registrations.register(created.id(), attendee("Bea Waiting", "bea@example.com", true));

        // Act
        WorkshopResponse detail = service.get(created.id());

        // Assert
        assertThat(detail.seatsLeft()).isZero();
        assertThat(detail.waitlistCount()).isEqualTo(1);
    }

    @Test
    void unknownWorkshopIsNotFound() {
        // Act / Assert
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void locationsAreTheThreeCentresByName() {
        // Act
        List<LocationView> locations = service.activeLocations();

        // Assert
        assertThat(locations).extracting(LocationView::name)
                .containsExactly("Central Library Annex", "Northside Studio", "Riverside Hall");
    }

    @Test
    void deletingAWorkshopIsRefusedByTheDatabase() {
        // Arrange
        WorkshopResponse created = service.create(workshop("POT-0412", NEXT_WEEK, 10));

        // Act / Assert
        assertThatThrownBy(() -> jdbc.update("DELETE FROM workshop WHERE id = ?", created.id()))
                .hasMessageContaining("workshops are permanent history");
    }

    private static void assertValidationError(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.properties().get(Problems.ERRORS))
                    .asInstanceOf(InstanceOfAssertFactories.list(FieldErrorItem.class))
                    .extracting(FieldErrorItem::field)
                    .contains(field);
        });
    }
}
