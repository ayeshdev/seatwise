package com.seatwise.registrations.internal;

import com.seatwise.accounts.StaffDirectory;
import com.seatwise.accounts.StaffSummary;
import com.seatwise.common.config.SeatwiseProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo profile only (FR-OPS-02): ten workshops across the three locations,
 * dated relative to today, covering open, one seat left, full with a waitlist,
 * cancelled, completed and next week, plus registration history with some
 * cancellations.
 *
 * <p>Why Java and not a Flyway {@code R__} script: every workshop and
 * registration must name a real staff member as creator, and the demo staff
 * only exist once {@code DemoStaffSeeder} has provisioned them in Keycloak,
 * which happens after Flyway. So this runs after it, as the demo Manager.
 *
 * <p>Why plain JDBC in this module: it is a fixture, not a business path. It
 * writes the workshop rows with a {@code seats_taken} equal to the ACTIVE rows
 * it writes, in one transaction, so the count is consistent by construction
 * (past workshops can't be booked through {@code SeatInventory} anyway). It
 * lives in registrations, the downstream module that already knows about
 * workshops. It only runs on an empty catalogue, and every insert is
 * {@code ON CONFLICT DO NOTHING} on fixed ids, so restarts are harmless.
 */
@Component
@Profile("demo")
@Order(100) // after the Admin bootstrap (0) and DemoStaffSeeder (10)
@ConditionalOnProperty(name = "seatwise.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
class DemoWorkshopSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoWorkshopSeeder.class);

    // Same fixed ids as V2__workshops.sql.
    private static final UUID NORTHSIDE = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b01");
    private static final UUID RIVERSIDE = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b02");
    private static final UUID CENTRAL = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b03");

    private static final String MANAGER_EMAIL = "manager@seatwise.local";
    private static final String STAFF_EMAIL = "staff@seatwise.local";

    private record DemoWorkshop(
            String code,
            String title,
            String instructor,
            String description,
            UUID locationId,
            int dayOffset,
            LocalTime start,
            int minutes,
            int capacity,
            int active,
            int cancelled,
            int waitlisted,
            String workshopCancelReason) {}

    private static final List<DemoWorkshop> WORKSHOPS = List.of(
            new DemoWorkshop("POT-0412", "Pottery wheel basics", "Amara Silva",
                    "Centre and throw your first bowls on the wheel. Clay and aprons provided.",
                    NORTHSIDE, 2, LocalTime.of(10, 0), 120, 12, 9, 1, 0, null),
            new DemoWorkshop("PY-0415", "Intro to Python", "Daniel Okafor",
                    "Variables, loops and your first small program. Laptops available to borrow.",
                    CENTRAL, 3, LocalTime.of(18, 0), 120, 16, 15, 1, 0, null),
            new DemoWorkshop("WAT-0416", "Watercolour landscapes", "Helen Marsh",
                    "Washes, layering and skies. Bring a sketchbook if you have one.",
                    RIVERSIDE, 1, LocalTime.of(14, 0), 150, 8, 8, 0, 2, null),
            new DemoWorkshop("YOG-0417", "Beginners' yoga", "Priya Nair",
                    "A gentle first class. Mats provided; wear something comfortable.",
                    RIVERSIDE, 4, LocalTime.of(9, 0), 60, 20, 3, 0, 0,
                    "Instructor unwell. We will reschedule and phone everyone booked."),
            new DemoWorkshop("SOU-0405", "Sourdough baking", "Tom Becker",
                    "Feed a starter, shape a loaf and take your dough home to bake.",
                    NORTHSIDE, -6, LocalTime.of(10, 0), 180, 10, 8, 1, 0, null),
            new DemoWorkshop("GTR-0420", "Guitar for beginners", "Luis Romero",
                    "Your first chords and a simple strumming pattern. Guitars provided.",
                    CENTRAL, 8, LocalTime.of(18, 30), 90, 10, 2, 0, 0, null),
            new DemoWorkshop("KNT-0421", "Knitting circle", "Margaret Doyle",
                    "Cast on, knit and purl. All levels welcome; needles and yarn provided.",
                    NORTHSIDE, 9, LocalTime.of(11, 0), 90, 15, 0, 0, 0, null),
            new DemoWorkshop("PHO-0418", "Smartphone photography", "Kenji Watanabe",
                    "Light, framing and editing with the phone you already have.",
                    RIVERSIDE, 5, LocalTime.of(13, 0), 120, 12, 5, 1, 0, null),
            new DemoWorkshop("CV-0419", "CV and interview skills", "Grace Mensah",
                    "Bring your CV for one-to-one feedback and practise common questions.",
                    CENTRAL, 6, LocalTime.of(10, 0), 120, 20, 11, 0, 0, null),
            new DemoWorkshop("FIR-0408", "First aid essentials", "Rachel Adams",
                    "CPR, the recovery position and everyday injuries. Certificate on completion.",
                    CENTRAL, -2, LocalTime.of(9, 30), 180, 18, 18, 0, 0, null));

    private static final List<String> FIRST_NAMES = List.of(
            "Olivia", "Noah", "Amelia", "Arjun", "Isla", "Mateo", "Freya", "Kwame", "Sofia", "Liam",
            "Chloe", "Yusuf", "Hannah", "Oscar", "Leila", "Ethan", "Maya", "Finn", "Zara", "Jonah",
            "Aisha", "Callum", "Elena", "Rory");

    private static final List<String> LAST_NAMES = List.of(
            "Bennett", "Khan", "O'Connor", "Patel", "Nguyen", "Hughes", "Mensah", "Kowalski", "Rossi",
            "Clarke", "Ahmed", "Murphy", "Fischer", "Evans", "Sato", "Turner", "Lopez", "Walsh");

    private static final List<String> CANCEL_REASONS = List.of(
            "Can't make this date any more.",
            "Booked by mistake.",
            "Feeling unwell.",
            "Moved to a later workshop.");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final StaffDirectory staff;
    private final Clock clock;
    private final ZoneId zone;

    DemoWorkshopSeeder(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            StaffDirectory staff,
            Clock clock,
            SeatwiseProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.staff = staff;
        this.clock = clock;
        this.zone = Objects.requireNonNullElse(properties.centreTimezone(), ZoneId.of("Europe/London"));
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM workshop", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Demo workshops: the catalogue already has {} workshops, nothing to do", existing);
            return;
        }
        Optional<StaffSummary> manager = staff.findByEmail(MANAGER_EMAIL);
        if (manager.isEmpty()) {
            log.warn("Demo workshops: skipped, the demo Manager {} doesn't exist yet (restart once it does)",
                    MANAGER_EMAIL);
            return;
        }
        UUID managerId = manager.get().id();
        UUID staffId = staff.findByEmail(STAFF_EMAIL).map(StaffSummary::id).orElse(managerId);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            LocalDate today = LocalDate.ofInstant(now, zone);
            for (int w = 0; w < WORKSHOPS.size(); w++) {
                seed(WORKSHOPS.get(w), w, today, now, managerId, staffId);
            }
        });
        log.info("Demo workshops: seeded {} workshops with registrations", WORKSHOPS.size());
    }

    private void seed(DemoWorkshop d, int index, LocalDate today, Instant now, UUID managerId, UUID staffId) {
        UUID workshopId = fixedId("workshop:" + d.code());
        Instant startsAt = today.plusDays(d.dayOffset()).atTime(d.start()).atZone(zone).toInstant();
        Instant endsAt = startsAt.plus(Duration.ofMinutes(d.minutes()));
        Instant earliest = startsAt.isBefore(now) ? startsAt : now;
        Instant createdAt = earliest.minus(Duration.ofDays(14));
        boolean cancelledWorkshop = d.workshopCancelReason() != null;
        Instant workshopCancelledAt = cancelledWorkshop ? now.minus(Duration.ofDays(1)) : null;

        jdbc.update("""
                INSERT INTO workshop (id, code, title, description, instructor, location_id, starts_at, ends_at,
                                      capacity, seats_taken, lifecycle, cancelled_at, cancelled_by, cancellation_reason,
                                      created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT DO NOTHING""",
                workshopId, d.code(), d.title(), d.description(), d.instructor(), d.locationId(),
                utc(startsAt), utc(endsAt), d.capacity(), d.active(),
                cancelledWorkshop ? "CANCELLED" : "SCHEDULED",
                utc(workshopCancelledAt), cancelledWorkshop ? managerId : null, d.workshopCancelReason(),
                utc(createdAt), managerId,
                utc(cancelledWorkshop ? workshopCancelledAt : createdAt), managerId);

        // Cancelled rows first, then the seat holders, then the waitlist (queue order).
        Instant firstBooking = earliest.minus(Duration.ofDays(10));
        int people = d.cancelled() + d.active() + d.waitlisted();
        for (int i = 0; i < people; i++) {
            String status = i < d.cancelled() ? "CANCELLED" : i < d.cancelled() + d.active() ? "ACTIVE" : "WAITLISTED";
            Instant registeredAt = firstBooking.plus(Duration.ofHours(7L * i));
            boolean cancelled = status.equals("CANCELLED");
            String first = FIRST_NAMES.get((index * 7 + i) % FIRST_NAMES.size());
            String last = LAST_NAMES.get((index * 5 + i) % LAST_NAMES.size());
            String email = (first + "." + last.replace("'", "")).toLowerCase(Locale.ROOT) + "@example.com";
            UUID registeredBy = i % 2 == 0 ? staffId : managerId;
            jdbc.update("""
                    INSERT INTO registration (id, workshop_id, attendee_name, attendee_email, status, registered_at,
                                              registered_by, promoted_at, cancelled_at, cancelled_by, cancellation_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                    ON CONFLICT DO NOTHING""",
                    fixedId("registration:" + d.code() + ":" + i), workshopId, first + " " + last, email, status,
                    utc(registeredAt), registeredBy,
                    cancelled ? utc(registeredAt.plus(Duration.ofDays(1))) : null,
                    cancelled ? registeredBy : null,
                    cancelled ? CANCEL_REASONS.get((index + i) % CANCEL_REASONS.size()) : null);
        }
    }

    private static UUID fixedId(String name) {
        return UUID.nameUUIDFromBytes(("seatwise-demo:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // The PostgreSQL driver binds OffsetDateTime to timestamptz; it has no Instant binding.
    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
