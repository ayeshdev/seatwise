package com.seatwise.search.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatwise.workshops.WorkshopStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The criteria as a Meilisearch filter (architecture section 7a, "Query translation"). */
class MeiliFilterTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    // Monday 2026-10-12 09:00 BST.
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");
    private static final long NOW_S = NOW.getEpochSecond();

    @Test
    void noCriteriaMeansNoFilter() {
        assertThat(filter(criteria(null, null, Set.of(), null, false))).isNull();
    }

    @Test
    void datesAreLocalMidnightsAndToIsInclusive() {
        // Act
        String filter = filter(criteria(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), Set.of(), null, false));

        // Assert: 2026-10-12T00:00+01:00 and 2026-10-19T00:00+01:00.
        assertThat(filter).isEqualTo("startsAt >= " + Instant.parse("2026-10-11T23:00:00Z").getEpochSecond()
                + " AND startsAt < " + Instant.parse("2026-10-18T23:00:00Z").getEpochSecond());
    }

    @Test
    void severalStatusesAreOrCombinedAgainstNow() {
        // Act
        String filter = filter(criteria(null, null, Set.of(WorkshopStatus.FULL, WorkshopStatus.OPEN), null, false));

        // Assert
        assertThat(filter).isEqualTo("((lifecycle = \"SCHEDULED\" AND startsAt > " + NOW_S + " AND seatsLeft > 0)"
                + " OR (lifecycle = \"SCHEDULED\" AND startsAt > " + NOW_S + " AND seatsLeft = 0))");
    }

    @Test
    void everyStatusHasItsCondition() {
        assertThat(filter(criteria(null, null, Set.of(WorkshopStatus.IN_PROGRESS), null, false))).isEqualTo(
                "((lifecycle = \"SCHEDULED\" AND startsAt <= " + NOW_S + " AND endsAt > " + NOW_S + "))");
        assertThat(filter(criteria(null, null, Set.of(WorkshopStatus.COMPLETED), null, false)))
                .isEqualTo("((lifecycle = \"SCHEDULED\" AND endsAt <= " + NOW_S + "))");
        assertThat(filter(criteria(null, null, Set.of(WorkshopStatus.CANCELLED), null, false)))
                .isEqualTo("((lifecycle = \"CANCELLED\"))");
    }

    @Test
    void locationAndHasSeatsAreAndCombined() {
        // Arrange
        UUID location = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b02");

        // Act
        String filter = filter(criteria(null, null, Set.of(), location, true));

        // Assert
        assertThat(filter).isEqualTo("locationId = \"" + location + "\""
                + " AND (lifecycle = \"SCHEDULED\" AND startsAt > " + NOW_S + " AND seatsLeft > 0)");
    }

    private static String filter(WorkshopSearchCriteria criteria) {
        return MeiliWorkshopSearch.filter(criteria, NOW, LONDON);
    }

    private static WorkshopSearchCriteria criteria(
            LocalDate from, LocalDate to, Set<WorkshopStatus> statuses, UUID locationId, boolean hasSeats) {
        return new WorkshopSearchCriteria(from, to, statuses, locationId, hasSeats, null, 0, 20, null);
    }
}
