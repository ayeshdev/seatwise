package com.seatwise.search.internal;

import com.seatwise.search.internal.MeiliWorkshopSearch.IndexHits;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * The {@link WorkshopSearch} the endpoint uses (architecture section 7a,
 * "Degraded mode"): the Meilisearch index when it answers in time, otherwise
 * the PostgreSQL search, with {@code searchMode} saying which one answered.
 * Staff can always find workshops; they only lose typo tolerance.
 *
 * <p>Only the Meilisearch call runs under the timeout, on a small worker
 * pool; hydrating the page from PostgreSQL happens on the request thread like
 * any other query. After a failure or timeout the index is skipped for
 * {@code circuitOpenFor} (a simple circuit breaker), so a down engine costs
 * one slow request per period, not one per request. The first request after
 * that period tries the index again.
 */
class FallbackWorkshopSearch implements WorkshopSearch, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FallbackWorkshopSearch.class);

    private final JpaWorkshopSearch database;
    private final MeiliWorkshopSearch index;
    private final long timeoutNanos;
    private final long circuitOpenNanos;
    private final LongSupplier nanoTime;
    private final ThreadPoolExecutor workers;
    private volatile long skipIndexUntil;
    private volatile boolean circuitOpen;

    /**
     * @param index null when the index is disabled: every search uses PostgreSQL
     * @param nanoTime a monotonic clock ({@code System::nanoTime}); not the
     *     application clock, which tests move around in business time
     */
    FallbackWorkshopSearch(JpaWorkshopSearch database, MeiliWorkshopSearch index, Duration timeout,
            Duration circuitOpenFor, LongSupplier nanoTime) {
        this.database = database;
        this.index = index;
        this.timeoutNanos = timeout.toNanos();
        this.circuitOpenNanos = circuitOpenFor.toNanos();
        this.nanoTime = nanoTime;
        this.skipIndexUntil = nanoTime.getAsLong();
        // A hung call keeps its worker until the client's own socket timeout;
        // the circuit stops new calls meanwhile, and a full pool means "fall back".
        this.workers = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), runnable -> {
            Thread thread = new Thread(runnable, "search-query");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public WorkshopSearchResult search(WorkshopSearchCriteria criteria) {
        if (index == null || skippingIndex()) {
            return database.search(criteria);
        }
        IndexHits hits = queryIndex(criteria);
        if (hits == null) {
            return database.search(criteria);
        }
        closeCircuit();
        return index.hydrate(criteria, hits);
    }

    /** The index's hits, or null if it failed, timed out or had no free worker. */
    private IndexHits queryIndex(WorkshopSearchCriteria criteria) {
        Future<IndexHits> call;
        try {
            call = workers.submit(() -> index.queryIndex(criteria));
        } catch (RejectedExecutionException e) {
            log.warn("Search workers busy; answering from PostgreSQL");
            return null;
        }
        try {
            return call.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            openCircuit("no answer within " + Duration.ofNanos(timeoutNanos).toMillis() + " ms");
        } catch (ExecutionException e) {
            openCircuit(String.valueOf(e.getCause()));
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private boolean skippingIndex() {
        return circuitOpen && nanoTime.getAsLong() - skipIndexUntil < 0;
    }

    private void openCircuit(String reason) {
        skipIndexUntil = nanoTime.getAsLong() + circuitOpenNanos;
        if (!circuitOpen) {
            circuitOpen = true;
            log.warn("Meilisearch unavailable ({}); workshop search uses PostgreSQL for the next {} s", reason,
                    circuitOpenNanos / 1_000_000_000);
        } else {
            log.debug("Meilisearch still unavailable ({})", reason);
        }
    }

    private void closeCircuit() {
        if (circuitOpen) {
            circuitOpen = false;
            log.info("Meilisearch is answering again; workshop search uses the index");
        }
    }

    @Override
    public void destroy() {
        workers.shutdownNow();
    }
}
