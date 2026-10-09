package com.seatwise.search.internal;

import static com.seatwise.support.Catalogue.CENTRAL;
import static com.seatwise.support.Catalogue.NORTHSIDE;
import static com.seatwise.support.Catalogue.RIVERSIDE;
import static com.seatwise.support.Catalogue.attendee;
import static com.seatwise.support.MutableClockConfiguration.MONDAY_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.model.Settings;
import com.seatwise.common.security.StaffRole;
import com.seatwise.registrations.internal.RegistrationService;
import com.seatwise.support.Catalogue;
import com.seatwise.support.MeilisearchTestConfiguration;
import com.seatwise.support.MutableClock;
import com.seatwise.support.MutableClockConfiguration;
import com.seatwise.support.TestStaff;
import com.seatwise.support.TestcontainersConfiguration;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * The Meilisearch path against a real index, next to the PostgreSQL search it
 * must agree with. The catalogue, seen at Monday 2026-10-12 09:00 in
 * Europe/London (BST, UTC+1):
 *
 * <pre>
 * PY-0002   Sat 10 Oct 11:00  Central    completed
 * POT-0412  Mon 12 Oct 08:00  Northside  in progress, "Pottery wheel basics"
 * WAT-0003  Wed 14 Oct 14:00  Riverside  full (1 of 1)
 * YOG-0004  Thu 15 Oct 10:00  Riverside  open, instructor Priya Nair
 * SOU-0005  Fri 16 Oct 10:00  Northside  cancelled
 * POT-0421  Sat 17 Oct 10:00  Riverside  open, "Raku glazing"
 * GTR-0006  Sun 18 Oct 23:30  Central    open (last half hour of the week)
 * KNT-0007  Mon 19 Oct 00:10  Central    open (first minutes of next week)
 * </pre>
 */
@SpringBootTest(properties = "seatwise.search.enabled=true")
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MeilisearchTestConfiguration.class, MutableClockConfiguration.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkshopSearchIT {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);
    private static final Duration INDEX_LAG = Duration.ofSeconds(2);

    @Autowired
    private WorkshopSearch search;

    @Autowired
    private JpaWorkshopSearch database;

    @Autowired
    private MeiliWorkshopSearch index;

    @Autowired
    private WorkshopIndexer indexer;

    @Autowired
    private MeilisearchClients clients;

    @Autowired
    private GenericContainer<?> meilisearchContainer;

    @Autowired
    private WorkshopService workshops;

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private final Map<String, UUID> ids = new HashMap<>();

    @BeforeEach
    void scheduleTheCatalogueIndexItAndMoveTheClockToMondayMorning() {
        TestStaff.wipeCatalogue(jdbc);
        ids.clear();
        TestStaff.signIn(TestStaff.insert(jdbc, "Morgan Reyes", StaffRole.MANAGER));
        // Scheduled while every start is still in the future.
        clock.set(Instant.parse("2026-10-01T08:00:00Z"));
        schedule("PY-0002", "Intro to Python", "Daniel Okafor", CENTRAL, local(2026, 10, 10, 11, 0), 10);
        schedule("POT-0412", "Pottery wheel basics", "Amara Silva", NORTHSIDE, local(2026, 10, 12, 8, 0), 10);
        schedule("WAT-0003", "Watercolour landscapes", "Helen Marsh", RIVERSIDE, local(2026, 10, 14, 14, 0), 1);
        registrations.register(ids.get("WAT-0003"), attendee(1));
        schedule("YOG-0004", "Beginners' yoga", "Priya Nair", RIVERSIDE, local(2026, 10, 15, 10, 0), 5);
        schedule("SOU-0005", "Sourdough baking", "Tom Becker", NORTHSIDE, local(2026, 10, 16, 10, 0), 5);
        workshops.cancel(ids.get("SOU-0005"), null);
        schedule("POT-0421", "Raku glazing", "Amara Silva", RIVERSIDE, local(2026, 10, 17, 10, 0), 6);
        schedule("GTR-0006", "Guitar for beginners", "Luis Romero", CENTRAL, local(2026, 10, 18, 23, 30), 10);
        schedule("KNT-0007", "Knitting circle", "Margaret Doyle", CENTRAL, local(2026, 10, 19, 0, 10), 10);
        clock.set(MONDAY_MORNING);
        // The truncate above bypassed the indexer, so drop what earlier tests
        // left in the index. Runs behind every queued update and waits for
        // Meilisearch, so the index is exactly the catalogue afterwards.
        assertThat(indexer.reindexAll()).isEqualTo(8);
    }

    @AfterEach
    void signOut() {
        TestStaff.signOut();
    }

    @Test
    @Order(1)
    void bootstrapAppliedTheIndexSettings() {
        // Act
        Settings settings = clients.admin().index(WorkshopDocuments.INDEX).getSettings();

        // Assert
        assertThat(settings.getSearchableAttributes())
                .containsExactly("code", "title", "instructor", "locationName", "description");
        assertThat(settings.getFilterableAttributes())
                .containsExactlyInAnyOrder("startsAt", "endsAt", "lifecycle", "locationId", "seatsLeft");
        assertThat(settings.getSortableAttributes()).containsExactlyInAnyOrder("startsAt", "title");
        assertThat(settings.getTypoTolerance().isEnabled()).isTrue();
        assertThat(settings.getTypoTolerance().getDisableOnAttributes()).containsExactly("code");
        assertThat(settings.getPagination().getMaxTotalHits()).isEqualTo(1000);
    }

    @Test
    @Order(2)
    void theRuntimeClientUsesAKeyScopedToTheWorkshopsIndex() {
        // Act
        var key = clients.admin().getKey(MeilisearchClients.RUNTIME_KEY_UID);

        // Assert
        assertThat(key.getIndexes()).containsExactly("workshops");
        assertThat(key.getActions()).containsExactlyInAnyOrder("search", "documents.*", "settings.*", "tasks.get");
        assertThatThrownBy(() -> clients.runtime().getKeys()).as("key management needs the master key")
                .isInstanceOf(MeilisearchApiException.class);
        assertThatThrownBy(() -> clients.runtime().createIndex("other")).as("other indexes are out of scope")
                .isInstanceOf(MeilisearchApiException.class);
    }

    @Test
    @Order(3)
    void indexAndPostgresReturnTheSameWorkshopsForTheSameFilters() {
        List<WorkshopSearchCriteria> matrix = List.of(
                criteria().build(),
                criteria().statuses(WorkshopStatus.OPEN).build(),
                criteria().statuses(WorkshopStatus.FULL).build(),
                criteria().statuses(WorkshopStatus.IN_PROGRESS).build(),
                criteria().statuses(WorkshopStatus.COMPLETED).build(),
                criteria().statuses(WorkshopStatus.CANCELLED).build(),
                criteria().statuses(WorkshopStatus.OPEN, WorkshopStatus.FULL).build(),
                criteria().statuses(WorkshopStatus.COMPLETED, WorkshopStatus.CANCELLED).location(NORTHSIDE).build(),
                criteria().hasSeats().build(),
                criteria().location(RIVERSIDE).build(),
                criteria().location(RIVERSIDE).hasSeats().build(),
                criteria().between(MONDAY, SUNDAY).build(),
                criteria().between(MONDAY, SUNDAY).hasSeats().build(),
                criteria().between(MONDAY.plusDays(7), SUNDAY.plusDays(7)).build(),
                criteria().between(MONDAY, MONDAY).build(),
                criteria().page(0, 3).build(),
                criteria().page(2, 3).build(),
                criteria().sort(WorkshopSort.TITLE_ASC).build(),
                criteria().sort(WorkshopSort.TITLE_DESC).statuses(WorkshopStatus.OPEN).build(),
                criteria().sort(WorkshopSort.STARTS_AT_DESC).page(1, 2).build());

        for (WorkshopSearchCriteria criteria : matrix) {
            // Act
            WorkshopSearchResult fromIndex = index.search(criteria);
            WorkshopSearchResult fromDatabase = database.search(criteria);

            // Assert
            assertThat(codes(fromIndex)).as(criteria.toString()).containsExactlyElementsOf(codes(fromDatabase));
            assertThat(fromIndex.totalItems()).as(criteria.toString()).isEqualTo(fromDatabase.totalItems());
            assertThat(fromIndex.items()).as(criteria.toString()).isEqualTo(fromDatabase.items());
            assertThat(fromIndex.searchMode()).isEqualTo("index");
        }
    }

    @Test
    @Order(4)
    void plainWordsFindTheSameWorkshopsAsTheFallback() {
        for (String q : List.of("riverside", "priya", "sourdough", "amara", "central")) {
            WorkshopSearchCriteria criteria = criteria().text(q).build();
            assertThat(codes(index.search(criteria))).as(q)
                    .containsExactlyInAnyOrderElementsOf(codes(database.search(criteria)));
        }
    }

    @Test
    @Order(5)
    void theEndpointSearchUsesTheIndexWhileItIsUp() {
        // Act
        WorkshopSearchResult result = search.search(criteria().between(MONDAY, SUNDAY).hasSeats().build());

        // Assert
        assertThat(result.searchMode()).isEqualTo("index");
        assertThat(codes(result)).containsExactly("YOG-0004", "POT-0421", "GTR-0006");
    }

    @Test
    @Order(6)
    void aTypoStillFindsTheWorkshop() {
        // Act
        WorkshopSearchResult result = search.search(criteria().text("potery").build());

        // Assert: the fallback's "contains" can't do this.
        assertThat(result.searchMode()).isEqualTo("index");
        assertThat(result.items()).extracting(WorkshopSummaryResponse::title).containsExactly("Pottery wheel basics");
        assertThat(database.search(criteria().text("potery").build()).items()).isEmpty();
    }

    @Test
    @Order(7)
    void codesMatchExactlyWithoutTypoTolerance() {
        assertThat(codes(search.search(criteria().text("POT-0412").build()))).containsExactly("POT-0412");
        assertThat(codes(search.search(criteria().text("POT-0421").build()))).containsExactly("POT-0421");
        assertThat(codes(search.search(criteria().text("pot-0412").build()))).containsExactly("POT-0412");
    }

    @Test
    @Order(8)
    void bookingTheLastSeatReachesTheIndexAfterCommit() {
        // Arrange: a one-seat workshop, open and indexed.
        clock.set(Instant.parse("2026-10-01T08:00:00Z"));
        UUID taster = schedule("CAL-0008", "Calligraphy taster", "Ines Duarte", CENTRAL, local(2026, 10, 17, 15, 0), 1);
        clock.set(MONDAY_MORNING);
        await().atMost(INDEX_LAG).ignoreExceptions()
                .untilAsserted(() -> assertThat(indexed(taster).seatsLeft()).isEqualTo(1));
        assertThat(codes(index.search(criteria().hasSeats().build()))).contains("CAL-0008");

        // Act
        registrations.register(taster, attendee(2));

        // Assert
        await().atMost(INDEX_LAG).untilAsserted(() -> {
            IndexedSeats seats = indexed(taster);
            assertThat(seats.seatsLeft()).isZero();
            assertThat(seats.seatsTaken()).isEqualTo(1);
        });
        assertThat(codes(index.search(criteria().hasSeats().build()))).doesNotContain("CAL-0008");
        assertThat(codes(index.search(criteria().statuses(WorkshopStatus.FULL).build())))
                .containsExactly("WAT-0003", "CAL-0008");
    }

    @Test
    @Order(9)
    void aRolledBackBookingNeverReachesTheIndex() {
        // Arrange
        UUID yoga = ids.get("YOG-0004");
        UUID knitting = ids.get("KNT-0007");

        // Act: the booking happens inside a transaction that then rolls back.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            registrations.register(yoga, attendee(3));
            tx.setRollbackOnly();
        });
        // A committed booking afterwards: once it is indexed, anything the
        // rolled-back one could have queued has been processed too (one writer, in order).
        registrations.register(knitting, attendee(4));
        await().atMost(INDEX_LAG).untilAsserted(() -> assertThat(indexed(knitting).seatsTaken()).isEqualTo(1));

        // Assert
        assertThat(indexed(yoga).seatsTaken()).isZero();
        assertThat(indexed(yoga).seatsLeft()).isEqualTo(5);
    }

    @Test
    @Order(10)
    void hydrationShowsExactSeatsEvenWhenTheIndexIsStale() {
        // Arrange: overwrite the full workshop's document with a stale copy.
        UUID watercolour = ids.get("WAT-0003");
        Map<String, Object> stale = staleCopy(watercolour);
        stale.put("title", "Stale title");
        stale.put("seatsTaken", 0);
        stale.put("seatsLeft", 1);
        write(stale);
        assertThat(indexed(watercolour).seatsLeft()).isEqualTo(1);

        // Act: the stale copy still matches "has seats" in the index ...
        WorkshopSearchResult result = index.search(criteria().location(RIVERSIDE).hasSeats().build());

        // Assert: ... but the row shown comes from PostgreSQL.
        assertThat(result.items()).filteredOn(w -> w.id().equals(watercolour)).singleElement().satisfies(w -> {
            assertThat(w.title()).isEqualTo("Watercolour landscapes");
            assertThat(w.seatsTaken()).isEqualTo(1);
            assertThat(w.seatsLeft()).isZero();
            assertThat(w.status()).isEqualTo(WorkshopStatus.FULL);
        });
    }

    @Test
    @Order(11)
    void hitsForWorkshopsTheCatalogueDoesNotKnowAreDropped() {
        // Arrange: a document with no workshop behind it.
        Map<String, Object> ghost = staleCopy(ids.get("YOG-0004"));
        ghost.put("id", UUID.randomUUID().toString());
        ghost.put("code", "GHO-0001");
        write(ghost);

        // Act
        WorkshopSearchResult result = index.search(criteria().location(RIVERSIDE).build());

        // Assert
        assertThat(codes(result)).containsExactly("WAT-0003", "YOG-0004", "POT-0421");
        assertThat(result.totalItems()).isEqualTo(3);
    }

    @Test
    @Order(Integer.MAX_VALUE)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void withMeilisearchStoppedSearchFallsBackToPostgres() {
        // Arrange
        WorkshopSearchCriteria thisWeekWithSeats = criteria().between(MONDAY, SUNDAY).hasSeats().build();
        assertThat(search.search(thisWeekWithSeats).searchMode()).isEqualTo("index");

        // Act
        meilisearchContainer.stop();
        WorkshopSearchResult first = search.search(thisWeekWithSeats);
        long started = System.nanoTime();
        WorkshopSearchResult second = search.search(criteria().text("priya").build());
        Duration secondTook = Duration.ofNanos(System.nanoTime() - started);

        // Assert
        assertThat(first.searchMode()).isEqualTo("fallback");
        assertThat(codes(first)).containsExactly("YOG-0004", "POT-0421", "GTR-0006");
        assertThat(second.searchMode()).isEqualTo("fallback");
        assertThat(codes(second)).containsExactly("YOG-0004");
        assertThat(secondTook).as("the open circuit skips the index").isLessThan(Duration.ofMillis(400));
        // Bookings still work and don't fail on the unreachable index.
        registrations.register(ids.get("GTR-0006"), attendee(5));
    }

    // ---- helpers ----

    /** The seat fields of an indexed document. */
    record IndexedSeats(String id, int seatsTaken, int seatsLeft) {}

    private IndexedSeats indexed(UUID workshopId) {
        return workshopsIndex().getDocument(workshopId.toString(), IndexedSeats.class);
    }

    private Map<String, Object> staleCopy(UUID workshopId) {
        var view = new LinkedHashMap<String, Object>();
        var source = database.search(criteria().build()).items().stream()
                .filter(w -> w.id().equals(workshopId)).findFirst().orElseThrow();
        view.put("id", source.id().toString());
        view.put("code", source.code());
        view.put("title", source.title());
        view.put("instructor", source.instructor());
        view.put("locationId", source.location().id().toString());
        view.put("locationName", source.location().name());
        view.put("startsAt", source.startsAt().getEpochSecond());
        view.put("endsAt", source.endsAt().getEpochSecond());
        view.put("lifecycle", "SCHEDULED");
        view.put("capacity", source.capacity());
        view.put("seatsTaken", source.seatsTaken());
        view.put("seatsLeft", source.seatsLeft());
        return view;
    }

    private void write(Map<String, Object> document) {
        Index workshops = workshopsIndex();
        String json = workshops.getConfig().getJsonHandler().encode(List.of(document));
        MeiliTasks.awaitSuccess(clients.runtime(), workshops.addDocuments(json, "id"));
    }

    private Index workshopsIndex() {
        return clients.runtime().index(WorkshopDocuments.INDEX);
    }

    private UUID schedule(String code, String title, String instructor, UUID location, Instant startsAt, int capacity) {
        UUID id = workshops.create(Catalogue.workshop(code, title, instructor, location, startsAt, capacity)).id();
        ids.put(code, id);
        return id;
    }

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(LONDON).toInstant();
    }

    private static List<String> codes(WorkshopSearchResult result) {
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
