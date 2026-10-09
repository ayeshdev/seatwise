package com.seatwise.search.internal;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.DocumentsQuery;
import com.meilisearch.sdk.model.Results;
import com.meilisearch.sdk.model.TaskInfo;
import com.seatwise.common.web.PageResponse;
import com.seatwise.registrations.AttendeeRegistered;
import com.seatwise.registrations.RegistrationCancelled;
import com.seatwise.registrations.WaitlistPromoted;
import com.seatwise.workshops.WorkshopCancelled;
import com.seatwise.workshops.WorkshopCatalogue;
import com.seatwise.workshops.WorkshopQuery;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopScheduled;
import com.seatwise.workshops.WorkshopUpdated;
import com.seatwise.workshops.WorkshopView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the {@code workshops} index in step with PostgreSQL (architecture
 * section 7a, "Keeping the index in sync").
 *
 * <p>Listeners run after commit, so a rolled-back change never reaches the
 * index, and they only hand the workshop id to a single writer thread. That
 * thread re-reads the workshop from {@link WorkshopCatalogue} (the committed
 * state, never the event payload) and upserts its document. Because there is
 * one writer and it reads at execution time, the last write for a workshop
 * always carries its latest committed state, whatever order the commits and
 * events came in. A workshop already waiting in the queue isn't queued again:
 * the queued write will read the newer state anyway.
 *
 * <p>Nothing here can fail the caller: the booking has already committed.
 * Failures are logged, and the next change or the nightly reconcile repairs
 * the document.
 *
 * <p>{@code AttendeeWaitlisted} is not listened to: joining a waitlist changes
 * no indexed field.
 */
class WorkshopIndexer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WorkshopIndexer.class);

    private static final int CATALOGUE_PAGE = 100;
    private static final int INDEX_PAGE = 1000;
    private static final Duration REINDEX_WAIT = Duration.ofMinutes(5);

    private final WorkshopCatalogue catalogue;
    private final MeilisearchClients clients;
    private final ThreadPoolExecutor writer;
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();

    WorkshopIndexer(WorkshopCatalogue catalogue, MeilisearchClients clients) {
        this.catalogue = catalogue;
        this.clients = clients;
        // Bounded: if Meilisearch is down for long, the oldest updates are
        // dropped (and logged) instead of growing the heap; the reconcile heals.
        this.writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10_000),
                runnable -> {
                    Thread thread = new Thread(runnable, "search-indexer");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WorkshopScheduled event) {
        enqueue(event.workshopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WorkshopUpdated event) {
        enqueue(event.workshopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WorkshopCancelled event) {
        enqueue(event.workshopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AttendeeRegistered event) {
        enqueue(event.workshopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RegistrationCancelled event) {
        enqueue(event.workshopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WaitlistPromoted event) {
        enqueue(event.workshopId());
    }

    /** Queues one workshop for re-indexing; never throws. */
    void enqueue(UUID workshopId) {
        if (workshopId == null || !queued.add(workshopId)) {
            return;
        }
        try {
            writer.execute(() -> {
                queued.remove(workshopId);
                upsert(workshopId);
            });
        } catch (RejectedExecutionException e) {
            queued.remove(workshopId);
            log.warn("Search index update for workshop {} dropped (queue full or shutting down); "
                    + "the nightly reconcile repairs it", workshopId);
        } catch (RuntimeException e) {
            queued.remove(workshopId);
            log.warn("Could not queue the search index update for workshop {}", workshopId, e);
        }
    }

    /**
     * Rebuilds every document from the catalogue and removes documents of
     * workshops the catalogue doesn't know, then waits until Meilisearch has
     * applied it all. Runs on the writer thread, so it also waits for every
     * update queued before it. Returns the number of workshops indexed.
     */
    int reindexAll() {
        Future<Integer> rebuild = writer.submit(this::rebuild);
        try {
            return rebuild.get(REINDEX_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while re-indexing", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Re-indexing failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Re-indexing took longer than " + REINDEX_WAIT, e);
        }
    }

    private void upsert(UUID workshopId) {
        try {
            Index index = clients.runtime().index(WorkshopDocuments.INDEX);
            Optional<WorkshopView> workshop = catalogue.find(workshopId);
            if (workshop.isPresent()) {
                index.addDocuments(json(index, List.of(WorkshopDocuments.document(workshop.get()))),
                        WorkshopDocuments.PRIMARY_KEY);
            } else {
                index.deleteDocument(workshopId.toString());
            }
        } catch (RuntimeException e) {
            // One line per failure: while Meilisearch is down this fires on every booking.
            log.warn("Could not update the search index for workshop {}; the next change or the nightly "
                    + "reconcile retries: {}", workshopId, e.toString());
            log.debug("Search index update failure", e);
        }
    }

    private int rebuild() {
        Client client = clients.runtime();
        Index index = client.index(WorkshopDocuments.INDEX);
        Set<String> live = new HashSet<>();
        List<TaskInfo> tasks = new ArrayList<>();
        for (int page = 0; ; page++) {
            PageResponse<WorkshopView> batch = catalogue.search(new WorkshopQuery(
                    null, null, Set.of(), null, false, null, page, CATALOGUE_PAGE, WorkshopSort.STARTS_AT_ASC));
            if (!batch.items().isEmpty()) {
                List<Map<String, Object>> documents = batch.items().stream().map(WorkshopDocuments::document).toList();
                batch.items().forEach(w -> live.add(w.id().toString()));
                tasks.add(index.addDocuments(json(index, documents), WorkshopDocuments.PRIMARY_KEY));
            }
            if ((long) (page + 1) * CATALOGUE_PAGE >= batch.totalItems()) {
                break;
            }
        }
        for (TaskInfo task : tasks) {
            MeiliTasks.awaitSuccess(client, task);
        }
        List<String> orphans = new ArrayList<>();
        for (int offset = 0; ; offset += INDEX_PAGE) {
            Results<IndexedId> indexed = index.getDocuments(new DocumentsQuery()
                    .setFields(new String[] {WorkshopDocuments.PRIMARY_KEY})
                    .setOffset(offset)
                    .setLimit(INDEX_PAGE), IndexedId.class);
            for (IndexedId doc : indexed.getResults()) {
                if (!live.contains(doc.id())) {
                    orphans.add(doc.id());
                }
            }
            if (offset + INDEX_PAGE >= indexed.getTotal()) {
                break;
            }
        }
        // Rare (workshops are never deleted), so one by one; the batch
        // delete-by-ids call is deprecated in the SDK.
        for (String orphan : orphans) {
            MeiliTasks.awaitSuccess(client, index.deleteDocument(orphan));
        }
        return live.size();
    }

    private static String json(Index index, List<Map<String, Object>> documents) {
        return index.getConfig().getJsonHandler().encode(documents);
    }

    @Override
    public void destroy() throws InterruptedException {
        writer.shutdown();
        if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
            writer.shutdownNow();
        }
    }

    /** Just the primary key of an indexed document. */
    record IndexedId(String id) {}
}
