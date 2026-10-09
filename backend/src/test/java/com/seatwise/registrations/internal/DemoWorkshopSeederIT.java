package com.seatwise.registrations.internal;

import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;

import com.seatwise.accounts.StaffDirectory;
import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.WorkshopView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The demo seeder only runs under the demo profile with Keycloak, so here it
 * is constructed by hand against the test database to prove its rows satisfy
 * every constraint and that the seat counts match the ACTIVE rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class DemoWorkshopSeederIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private StaffDirectory staff;

    @Autowired
    private WorkshopCatalogue catalogue;

    @Autowired
    private MutableClock clock;

    @Autowired
    private SeatwiseProperties properties;

    private DemoWorkshopSeeder seeder;

    @BeforeEach
    void startWithAnEmptyCatalogue() {
        TestStaff.wipeCatalogue(jdbc);
        clock.set(MONDAY_MORNING);
        seeder = new DemoWorkshopSeeder(jdbc, transactions, staff, clock, properties);
    }

    @Test
    void seedsTenConsistentWorkshopsCoveringEveryCase() {
        // Arrange
        insertStaff("manager@seatwise.local", "Morgan Reyes", "MANAGER");
        insertStaff("staff@seatwise.local", "Sam Taylor", "STAFF");

        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        List<UUID> ids = jdbc.queryForList("SELECT id FROM workshop", UUID.class);
        assertThat(ids).hasSize(10);
        List<WorkshopView> workshops = catalogue.findAll(ids);
        assertThat(workshops).extracting(WorkshopView::status)
                .contains(WorkshopStatus.OPEN, WorkshopStatus.FULL, WorkshopStatus.CANCELLED, WorkshopStatus.COMPLETED);
        assertThat(workshops).anySatisfy(w -> assertThat(w.seatsLeft()).isEqualTo(1));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM workshop w
                 WHERE w.seats_taken <> (SELECT count(*) FROM registration r
                                          WHERE r.workshop_id = w.id AND r.status = 'ACTIVE')""", Integer.class))
                .as("seats_taken matches the ACTIVE rows everywhere").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration WHERE status = 'WAITLISTED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM registration WHERE status = 'CANCELLED' AND cancellation_reason IS NOT NULL",
                Integer.class)).isPositive();
    }

    @Test
    void runningAgainChangesNothing() {
        // Arrange
        insertStaff("manager@seatwise.local", "Morgan Reyes", "MANAGER");
        seeder.run(new DefaultApplicationArguments());
        Integer registrations = jdbc.queryForObject("SELECT count(*) FROM registration", Integer.class);

        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workshop", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration", Integer.class)).isEqualTo(registrations);
    }

    @Test
    void withoutTheDemoManagerNothingIsSeeded() {
        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workshop", Integer.class)).isZero();
    }

    private void insertStaff(String email, String fullName, String role) {
        jdbc.update("""
                INSERT INTO staff_account (id, email, full_name, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, now(), now(), 0)""", UUID.randomUUID(), email, fullName, role);
    }
}
