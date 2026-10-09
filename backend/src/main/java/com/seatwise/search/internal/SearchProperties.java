package com.seatwise.search.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How the search module uses Meilisearch. The connection itself
 * ({@code seatwise.search.url}, {@code seatwise.search.master-key}) is read from
 * {@code SeatwiseProperties.Search}, so the production secrets guard sees it;
 * these are the module's own knobs under the same prefix.
 * {@code seatwise.search.reconcile-cron} (when the nightly full re-index runs,
 * centre timezone) is read directly by {@code SearchIndexBootstrap}'s
 * {@code @Scheduled}.
 *
 * @param enabled false runs PostgreSQL-only search: no client, indexer or
 *     bootstrap, every response is {@code searchMode: "fallback"} (the test profile)
 * @param timeout how long a list request waits for Meilisearch before it
 *     answers from PostgreSQL instead
 * @param circuitOpenFor after a failure or timeout, Meilisearch is skipped for
 *     this long, so a down engine doesn't add {@code timeout} to every request
 */
@ConfigurationProperties("seatwise.search")
record SearchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500ms") Duration timeout,
        @DefaultValue("30s") Duration circuitOpenFor) {}
