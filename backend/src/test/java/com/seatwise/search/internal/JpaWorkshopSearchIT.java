package com.seatwise.search.internal;

import static com.seatwise.support.Catalogue.CENTRAL;
import static com.seatwise.support.Catalogue.NORTHSIDE;
import static com.seatwise.support.Catalogue.RIVERSIDE;
import static com.seatwise.support.Catalogue.attendee;
import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.StaffRole;
import com.seatwise.registrations.internal.RegistrationService;
import com.seatwise.support.Catalogue;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The PostgreSQL search path against a fixed catalogue, seen at Monday
 * 2026-10-12 09:00 in Europe/London (BST, UTC+1):
 *
 * <pre>
 * PY-0002   Sat 10 Oct 11:00  Central    completed
 * POT-0001  Mon 12 Oct 08:00  Northside  in progress
 * WAT-0003  Wed 14 Oct 14:00  Riverside  full (1 of 1)
 * YOG-0004  Thu 15 Oct 10:00  Riverside  open, instructor Priya Nair
 * SOU-0005  Fri 16 Oct 10:00  Northside  cancelled
 * GTR-0006  Sun 18 Oct 23:30  Central    open (last half hour of the week)
 * KNT-0007  Mon 19 Oct 00:10  Central    open (first minutes of next week; still the 18th in UTC)
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class JpaWorkshopSearchIT {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);

    @Autowired
    private WorkshopSearch search;

    @Autowired
    private WorkshopService workshops;

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void scheduleTheCatalogueThenMoveTheClockToMondayMorning() {
        TestStaff.wipeCatalogue(jdbc);
        TestStaff.signIn(TestStaff.insert(jdbc, "Morgan Reyes", StaffRole.MANAGER));
        // Scheduled while every start is still in the future.
        clock.set(Instant.parse("2026-10-01T08:00:00Z"));
        schedule("PY-0002", "Intro to Python", "Daniel Okafor", CENTRAL, local(2026, 10, 10, 11, 0), 10);
        schedule("POT-0001", "Pottery wheel basics", "Amara Silva", NORTHSIDE, local(2026, 10, 12, 8, 0), 10);
        UUID full = schedule("WAT-0003", "Watercolour landscapes", "Helen Marsh", RIVERSIDE,
                local(2026, 10, 14, 14, 0), 1);
        registrations.register(full, attendee(1));
        schedule("YOG-0004", "Beginners' yoga", "Priya Nair", RIVERSIDE, local(2026, 10, 15, 10, 0), 5);
        UUID cancelled = schedule("SOU-0005", "Sourdough baking", "Tom Becker", NORTHSIDE,
                local(2026, 10, 16, 10, 0), 5);
        workshops.cancel(cancelled, null);
        schedule("GTR-0006", "Guitar for beginners", "Luis Romero", CENTRAL, local(2026, 10, 18, 23, 30), 10);
        schedule("KNT-0007", "Knitting circle", "Margaret Doyle", CENTRAL, local(2026, 10, 19, 0, 10), 10);
        clock.set(MONDAY_MORNING);
    }

    @AfterEach
    void signOut() {
        TestStaff.signOut();
    }

    @Test
    void withoutFiltersEverythingComesBackSoonestFirstInFallbackMode() {
        // Act
        WorkshopSearchResult result = search.search(criteria().build());

        // Assert
        assertThat(codes(result)).containsExactly(
                "PY-0002", "POT-0001", "WAT-0003", "YOG-0004", "SOU-0005", "GTR-0006", "KNT-0007");
        assertThat(result.totalItems()).isEqualTo(7);
        assertThat(result.searchMode()).isEqualTo("fallback");
        assertThat(result.items()).filteredOn(w -> w.code().equals("WAT-0003")).singleElement().satisfies(w -> {
            assertThat(w.status()).isEqualTo(WorkshopStatus.FULL);
            assertThat(w.seatsTaken()).isEqualTo(1);
            assertThat(w.seatsLeft()).isZero();
            assertThat(w.location().name()).isEqualTo("Riverside Hall");
        });
    }

    @Test
    void eachStatusFilterMatchesTheDerivedStatus() {
        assertThat(codes(statuses(WorkshopStatus.OPEN))).containsExactly("YOG-0004", "GTR-0006", "KNT-0007");
        assertThat(codes(statuses(WorkshopStatus.FULL))).containsExactly("WAT-0003");
        assertThat(codes(statuses(WorkshopStatus.IN_PROGRESS))).containsExactly("POT-0001");
        assertThat(codes(statuses(WorkshopStatus.COMPLETED))).containsExactly("PY-0002");
        assertThat(codes(statuses(WorkshopStatus.CANCELLED))).containsExactly("SOU-0005");
    }

    @Test
    void statusFilterAgreesWithTheStatusShownOnEveryRow() {
        for (WorkshopStatus status : WorkshopStatus.values()) {
            assertThat(statuses(status).items()).allSatisfy(w -> assertThat(w.status()).isEqualTo(status));
        }
    }

    @Test
    void severalStatusesAreCombinedWithOr() {
        // Act
        WorkshopSearchResult result = statuses(WorkshopStatus.OPEN, WorkshopStatus.FULL);

        // Assert
        assertThat(codes(result)).containsExactly("WAT-0003", "YOG-0004", "GTR-0006", "KNT-0007");
    }

    @Test
    void hasSeatsKeepsOnlyBookableWorkshopsWithASeatLeft() {
        // Act
        WorkshopSearchResult result = search.search(criteria().hasSeats().build());

        // Assert: the running workshop has free seats but can't be booked any more.
        assertThat(codes(result)).containsExactly("YOG-0004", "GTR-0006", "KNT-0007");
    }

    @Test
    void locationFilterKeepsOneLocation() {
        // Act
        WorkshopSearchResult result = search.search(criteria().location(RIVERSIDE).build());

        // Assert
        assertThat(codes(result)).containsExactly("WAT-0003", "YOG-0004");
    }

    @Test
    void textMatchesCodeTitleInstructorAndLocationIgnoringCase() {
        assertThat(codes(text("pot"))).as("code and title").containsExactly("POT-0001");
        assertThat(codes(text("SOURDOUGH"))).as("title").containsExactly("SOU-0005");
        assertThat(codes(text("priya"))).as("instructor").containsExactly("YOG-0004");
        assertThat(codes(text("riverside"))).as("location name").containsExactly("WAT-0003", "YOG-0004");
        assertThat(codes(text("py-0002"))).as("code").containsExactly("PY-0002");
        assertThat(codes(text("%"))).as("wildcards are literal").isEmpty();
    }

    @Test
    void thisWeekRunsFromMondayMidnightToSundayMidnightInTheCentreTimezone() {
        // Act
        WorkshopSearchResult thisWeek = search.search(criteria().between(MONDAY, SUNDAY).build());
        WorkshopSearchResult nextWeek = search.search(criteria().between(MONDAY.plusDays(7), SUNDAY.plusDays(7)).build());

        // Assert: Sunday 23:30 local is this week; Monday 00:10 local is next week,
        // although both fall on Sunday the 18th in UTC.
        assertThat(codes(thisWeek)).containsExactly("POT-0001", "WAT-0003", "YOG-0004", "SOU-0005", "GTR-0006");
        assertThat(codes(nextWeek)).containsExactly("KNT-0007");
    }

    @Test
    void thisWeekWithSeatsIsTheDefaultViewQuestion() {
        // Act
        WorkshopSearchResult result = search.search(criteria().between(MONDAY, SUNDAY).hasSeats().build());

        // Assert
        assertThat(codes(result)).containsExactly("YOG-0004", "GTR-0006");
    }

    @Test
    void resultsArePagedWithTheTotal() {
        // Act
        WorkshopSearchResult first = search.search(criteria().page(0, 3).build());
        WorkshopSearchResult last = search.search(criteria().page(2, 3).build());

        // Assert
        assertThat(codes(first)).containsExactly("PY-0002", "POT-0001", "WAT-0003");
        assertThat(first.totalItems()).isEqualTo(7);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(3);
        assertThat(codes(last)).containsExactly("KNT-0007");
    }

    @Test
    void resultsCanBeSortedByTitleOrLatestFirst() {
        // Act
        WorkshopSearchResult byTitle = search.search(criteria().sort(WorkshopSearchCriteria.parseSort("title")).build());
        WorkshopSearchResult latestFirst = search.search(
                criteria().sort(WorkshopSearchCriteria.parseSort("startsAt,desc")).build());

        // Assert
        assertThat(byTitle.items()).extracting(WorkshopSummaryResponse::title).startsWith(
                "Beginners' yoga", "Guitar for beginners", "Intro to Python");
        assertThat(codes(latestFirst)).startsWith("KNT-0007", "GTR-0006");
    }

    @Test
    void invalidCriteriaAreRefused() {
        assertThatThrownBy(() -> criteria().between(SUNDAY, MONDAY).build())
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> criteria().page(0, 101).build())
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> WorkshopSearchCriteria.parseSort("seatsLeft"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ---- helpers ----

    private UUID schedule(String code, String title, String instructor, UUID location, Instant startsAt, int capacity) {
        return workshops.create(Catalogue.workshop(code, title, instructor, location, startsAt, capacity)).id();
    }

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(LONDON).toInstant();
    }

    private WorkshopSearchResult statuses(WorkshopStatus... statuses) {
        return search.search(criteria().statuses(statuses).build());
    }

    private WorkshopSearchResult text(String q) {
        return search.search(criteria().text(q).build());
    }

    private static java.util.List<String> codes(WorkshopSearchResult result) {
        return result.items().stream().map(WorkshopSummaryResponse::code).toList();
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    /** Defaults match the endpoint's: no filters, first page of 20, soonest first. */
    private static final class CriteriaBuilder {
        private LocalDate from;
        private LocalDate to;
        private Set<WorkshopStatus> statuses = Set.of();
        private UUID locationId;
        private boolean hasSeats;
        private String q;
        private int page;
        private int size = WorkshopSearchCriteria.DEFAULT_SIZE;
        private WorkshopSort sort;

        CriteriaBuilder between(LocalDate from, LocalDate to) {
            this.from = from;
            this.to = to;
            return this;
        }

        CriteriaBuilder statuses(WorkshopStatus... statuses) {
            this.statuses = Set.of(statuses);
            return this;
        }

        CriteriaBuilder location(UUID locationId) {
            this.locationId = locationId;
            return this;
        }

        CriteriaBuilder hasSeats() {
            this.hasSeats = true;
            return this;
        }

        CriteriaBuilder text(String q) {
            this.q = q;
            return this;
        }

        CriteriaBuilder page(int page, int size) {
            this.page = page;
            this.size = size;
            return this;
        }

        CriteriaBuilder sort(WorkshopSort sort) {
            this.sort = sort;
            return this;
        }

        WorkshopSearchCriteria build() {
            return new WorkshopSearchCriteria(from, to, statuses, locationId, hasSeats, q, page, size, sort);
        }
    }
}
