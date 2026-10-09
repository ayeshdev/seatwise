package com.seatwise.search.internal;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Builds the {@code workshops} index from scratch (architecture section 7a,
 * "Full rebuild"): creates it if needed, applies its settings, derives the
 * scoped runtime key and re-indexes every workshop. Runs once the application
 * is ready and again every night, which also heals any update the
 * {@link WorkshopIndexer} lost. The data set is small, so a full rebuild is
 * cheap.
 *
 * <p>Failures are logged, never thrown: until the next successful run, list
 * requests simply fall back to PostgreSQL.
 */
class SearchIndexBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);
    private static final String INDEX_EXISTS = "index_already_exists";

    private final MeilisearchClients clients;
    private final WorkshopIndexer indexer;

    SearchIndexBootstrap(MeilisearchClients clients, WorkshopIndexer indexer) {
        this.clients = clients;
        this.indexer = indexer;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        rebuild("startup");
    }

    @Scheduled(cron = "${seatwise.search.reconcile-cron:0 30 3 * * *}", zone = "${seatwise.centre-timezone:Europe/London}")
    void nightlyReconcile() {
        rebuild("nightly reconcile");
    }

    /** Settings, key and documents; returns false (after logging why) if anything failed. */
    boolean rebuild(String reason) {
        long started = System.nanoTime();
        try {
            applySettings();
            clients.runtime();
            int count = indexer.reindexAll();
            log.info("Search index rebuilt ({}): {} workshops in {} ms", reason, count,
                    (System.nanoTime() - started) / 1_000_000);
            return true;
        } catch (RuntimeException e) {
            log.warn("Search index rebuild ({}) failed; workshop search answers from PostgreSQL until it "
                    + "succeeds: {}", reason, e.toString());
            return false;
        }
    }

    private void applySettings() {
        Client admin = clients.admin();
        try {
            Task created = MeiliTasks.awaitFinished(admin,
                    admin.createIndex(WorkshopDocuments.INDEX, WorkshopDocuments.PRIMARY_KEY));
            // On later starts the index exists and this task fails with index_already_exists: fine.
            if (created.getStatus() != TaskStatus.SUCCEEDED
                    && !INDEX_EXISTS.equals(MeiliTasks.errorCode(created))) {
                throw new IllegalStateException("Could not create the search index: " + MeiliTasks.errorCode(created));
            }
        } catch (MeilisearchApiException e) {
            if (!INDEX_EXISTS.equals(e.getCode())) {
                throw e;
            }
        }
        MeiliTasks.awaitSuccess(admin, admin.index(WorkshopDocuments.INDEX).updateSettings(WorkshopDocuments.settings()));
    }
}
