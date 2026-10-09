package com.seatwise.registrations.internal;

import static com.seatwise.support.Catalogue.attendee;
import static com.seatwise.support.Catalogue.edit;
import static com.seatwise.support.Catalogue.workshop;
import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.internal.WorkshopRequest;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The capacity rule under real concurrency (FR-REG-02, FR-REG-10, FR-WL-03;
 * architecture section 7): real service beans, a real PostgreSQL, and every
 * thread released at the same instant by a latch. The pool is big enough that
 * every thread holds its own connection, so the database (not the pool) is
 * what serializes them.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=60")
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class CapacityConcurrencyIT {

    private static final Instant NEXT_WEEK = MONDAY_MORNING.plus(Duration.ofDays(7));

    /** A thread's result: null if the call succeeded, else the domain error code. */
    private static final ErrorCode SUCCESS = null;

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private WorkshopService workshops;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

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
    void fiftySimultaneousRegistrationsForTwentySeatsTakeExactlyTwenty() throws Exception {
        // Arrange
        UUID workshopId = workshops.create(workshop("POT-0412", NEXT_WEEK, 20)).id();
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int n = i;
            tasks.add(as(n % 2 == 0 ? staff : manager, () -> registrations.register(workshopId, attendee(n))));
        }

        // Act
        List<ErrorCode> results = runAtOnce(tasks);

        // Assert
        assertThat(Collections.frequency(results, SUCCESS)).isEqualTo(20);
        assertThat(Collections.frequency(results, ErrorCode.WORKSHOP_FULL)).isEqualTo(30);
        assertThat(seatsTaken(workshopId)).isEqualTo(20);
        assertThat(count(workshopId, "ACTIVE")).isEqualTo(20);
        assertThat(count(workshopId, null)).as("a refused request saves nothing").isEqualTo(20);
    }

    @Test
    void twentySimultaneousCancelsOfOneBookingRecordExactlyOne() throws Exception {
        // Arrange
        UUID workshopId = workshops.create(workshop("POT-0412", NEXT_WEEK, 5)).id();
        UUID registrationId = registrations.register(workshopId, attendee(1)).id();
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(as(i % 2 == 0 ? staff : manager,
                    () -> registrations.cancel(registrationId, new CancelRegistrationRequest("Called to cancel"))));
        }

        // Act
        List<ErrorCode> results = runAtOnce(tasks);

        // Assert
        assertThat(Collections.frequency(results, SUCCESS)).isEqualTo(1);
        assertThat(Collections.frequency(results, ErrorCode.ALREADY_CANCELLED)).isEqualTo(19);
        assertThat(seatsTaken(workshopId)).as("the seat was released once").isZero();
        assertThat(count(workshopId, "CANCELLED")).isEqualTo(1);
    }

    @RepeatedTest(5)
    void registrationsRacingACapacityCutNeverExceedTheCapacity() throws Exception {
        // Arrange: 10 of 20 seats taken; 10 more bookings race a cut to 12.
        WorkshopRequest request = workshop("POT-0412", NEXT_WEEK, 20);
        UUID workshopId = workshops.create(request).id();
        for (int i = 0; i < 10; i++) {
            registrations.register(workshopId, attendee(i));
        }
        long version = workshops.get(workshopId).version();
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        tasks.add(as(manager, () -> workshops.update(workshopId, edit(request, 12, version))));
        for (int i = 10; i < 20; i++) {
            int n = i;
            tasks.add(as(staff, () -> registrations.register(workshopId, attendee(n))));
        }

        // Act
        List<ErrorCode> results = runAtOnce(tasks);

        // Assert
        ErrorCode cut = results.getFirst();
        List<ErrorCode> bookings = results.subList(1, results.size());
        int capacity = jdbc.queryForObject("SELECT capacity FROM workshop WHERE id = ?", Integer.class, workshopId);
        int taken = seatsTaken(workshopId);
        assertThat(taken).isLessThanOrEqualTo(capacity);
        assertThat(count(workshopId, "ACTIVE")).isEqualTo(taken);
        assertThat(taken).isEqualTo(10 + Collections.frequency(bookings, SUCCESS));
        assertThat(bookings).allMatch(code -> code == SUCCESS || code == ErrorCode.WORKSHOP_FULL);
        assertThat(cut).isIn(SUCCESS, ErrorCode.CAPACITY_BELOW_TAKEN);
        if (cut == SUCCESS) {
            // The cut won while at most 12 were taken; the remaining bookings filled it to 12.
            assertThat(capacity).isEqualTo(12);
            assertThat(taken).isEqualTo(12);
        } else {
            // Bookings got past 12 first, so the cut was refused and every booking fit.
            assertThat(capacity).isEqualTo(20);
            assertThat(taken).isEqualTo(20);
        }
    }

    @Test
    void simultaneousCancelsPromoteTheWaitlistInQueueOrder() throws Exception {
        // Arrange: 5 seats taken, 7 people waiting (arriving a minute apart).
        UUID workshopId = workshops.create(workshop("POT-0412", NEXT_WEEK, 5)).id();
        List<UUID> holders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            holders.add(registrations.register(workshopId, attendee(i)).id());
        }
        List<UUID> queue = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            clock.advance(Duration.ofMinutes(1));
            queue.add(registrations.register(workshopId,
                    attendee("Waiting " + i, "waiting" + i + "@example.com", true)).id());
        }
        Map<UUID, UUID> promotedBy = new ConcurrentHashMap<>();
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        for (UUID holder : holders) {
            tasks.add(as(staff, () -> {
                CancelResult result = registrations.cancel(holder, null);
                promotedBy.put(result.promoted().id(), holder);
            }));
        }

        // Act
        List<ErrorCode> results = runAtOnce(tasks);

        // Assert
        assertThat(results).containsOnly(SUCCESS);
        assertThat(promotedBy.keySet()).as("five different people, the first five in line")
                .containsExactlyInAnyOrderElementsOf(queue.subList(0, 5));
        assertThat(statuses(queue.subList(5, 7))).containsOnly("WAITLISTED");
        assertThat(seatsTaken(workshopId)).as("seats passed on, never released").isEqualTo(5);
        assertThat(count(workshopId, "ACTIVE")).isEqualTo(5);
        assertThat(count(workshopId, "WAITLISTED")).isEqualTo(2);
    }

    // ---- helpers ----

    private interface Action {
        void run() throws Exception;
    }

    /** Wraps an action so it runs signed in as {@code actor} and reports its error code (null = success). */
    private static Callable<ErrorCode> as(StaffPrincipal actor, Action action) {
        return () -> {
            TestStaff.signIn(actor);
            try {
                action.run();
                return SUCCESS;
            } catch (DomainException e) {
                return e.code();
            } finally {
                TestStaff.signOut();
            }
        };
    }

    /**
     * Starts one thread per task, waits until all are parked on the start gate,
     * then opens it so they hit the database together. Results keep task order.
     */
    private static List<ErrorCode> runAtOnce(List<Callable<ErrorCode>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (Callable<ErrorCode> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("all threads ready").isTrue();
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int seatsTaken(UUID workshopId) {
        return jdbc.queryForObject("SELECT seats_taken FROM workshop WHERE id = ?", Integer.class, workshopId);
    }

    /** Rows of the workshop with the given status, or all rows when {@code status} is null. */
    private int count(UUID workshopId, String status) {
        return status == null
                ? jdbc.queryForObject("SELECT count(*) FROM registration WHERE workshop_id = ?", Integer.class,
                        workshopId)
                : jdbc.queryForObject("SELECT count(*) FROM registration WHERE workshop_id = ? AND status = ?",
                        Integer.class, workshopId, status);
    }

    private Set<String> statuses(List<UUID> ids) {
        return ids.stream()
                .map(id -> jdbc.queryForObject("SELECT status FROM registration WHERE id = ?", String.class, id))
                .collect(Collectors.toSet());
    }
}
