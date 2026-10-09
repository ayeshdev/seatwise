package com.seatwise.search.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.seatwise.search.internal.MeiliWorkshopSearch.IndexHits;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Which search answers, and the circuit that keeps a down index from slowing every request. */
class FallbackWorkshopSearchTest {

    private static final Duration TIMEOUT = Duration.ofMillis(200);
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofSeconds(30);

    private final JpaWorkshopSearch database = mock(JpaWorkshopSearch.class);
    private final MeiliWorkshopSearch index = mock(MeiliWorkshopSearch.class);
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final WorkshopSearchCriteria criteria =
            new WorkshopSearchCriteria(null, null, Set.of(), null, false, null, 0, 20, null);
    private final IndexHits hits = new IndexHits(List.of(UUID.randomUUID()), 1);
    private final WorkshopSearchResult fromIndex = new WorkshopSearchResult(List.of(), 0, 20, 1, "index");
    private final WorkshopSearchResult fromDatabase = new WorkshopSearchResult(List.of(), 0, 20, 1, "fallback");

    private final FallbackWorkshopSearch search =
            new FallbackWorkshopSearch(database, index, TIMEOUT, CIRCUIT_OPEN_FOR, nanos::get);

    @AfterEach
    void stopWorkers() {
        search.destroy();
    }

    @Test
    void answersFromTheIndexWhileItIsUp() {
        // Arrange
        when(index.queryIndex(criteria)).thenReturn(hits);
        when(index.hydrate(criteria, hits)).thenReturn(fromIndex);

        // Act
        WorkshopSearchResult result = search.search(criteria);

        // Assert
        assertThat(result.searchMode()).isEqualTo("index");
        verify(database, never()).search(any());
    }

    @Test
    void anIndexErrorFallsBackAndSkipsTheIndexUntilTheCircuitCloses() {
        // Arrange
        when(index.queryIndex(criteria)).thenThrow(new MeilisearchCommunicationException("connection refused"));
        when(database.search(criteria)).thenReturn(fromDatabase);

        // Act + Assert: the failure falls back ...
        assertThat(search.search(criteria).searchMode()).isEqualTo("fallback");
        // ... and for 30 s the index isn't even asked.
        nanos.addAndGet(CIRCUIT_OPEN_FOR.minusSeconds(1).toNanos());
        assertThat(search.search(criteria).searchMode()).isEqualTo("fallback");
        verify(index, times(1)).queryIndex(criteria);

        // After that the next request tries the index again, and succeeds.
        doReturn(hits).when(index).queryIndex(criteria);
        when(index.hydrate(criteria, hits)).thenReturn(fromIndex);
        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        assertThat(search.search(criteria).searchMode()).isEqualTo("index");
        verify(index, times(2)).queryIndex(criteria);
    }

    @Test
    void aSlowIndexIsAbandonedAfterTheTimeout() {
        // Arrange
        when(index.queryIndex(criteria)).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return hits;
        });
        when(database.search(criteria)).thenReturn(fromDatabase);

        // Act
        long started = System.nanoTime();
        WorkshopSearchResult result = search.search(criteria);
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        // Assert
        assertThat(result.searchMode()).isEqualTo("fallback");
        assertThat(took).isLessThan(Duration.ofSeconds(2));
        assertThat(search.search(criteria).searchMode()).as("circuit open").isEqualTo("fallback");
        verify(index, times(1)).queryIndex(criteria);
    }

    @Test
    void withTheIndexDisabledEverySearchUsesPostgres() {
        // Arrange
        FallbackWorkshopSearch databaseOnly =
                new FallbackWorkshopSearch(database, null, TIMEOUT, CIRCUIT_OPEN_FOR, nanos::get);
        when(database.search(criteria)).thenReturn(fromDatabase);

        // Act
        WorkshopSearchResult result = databaseOnly.search(criteria);

        // Assert
        assertThat(result.searchMode()).isEqualTo("fallback");
        databaseOnly.destroy();
    }
}
