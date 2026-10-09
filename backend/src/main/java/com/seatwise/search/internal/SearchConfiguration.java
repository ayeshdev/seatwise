package com.seatwise.search.internal;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.workshops.WorkshopCatalogue;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires workshop search. {@link FallbackWorkshopSearch} is always the
 * {@link WorkshopSearch} the endpoint gets; the Meilisearch beans exist only
 * while {@code seatwise.search.enabled} is true (the default; the test profile
 * turns it off so tests that don't opt in never need a search engine).
 */
@Configuration(proxyBeanMethods = false)
class SearchConfiguration {

    @Bean
    @Primary
    FallbackWorkshopSearch workshopSearch(
            JpaWorkshopSearch database, ObjectProvider<MeiliWorkshopSearch> index, SearchProperties properties) {
        return new FallbackWorkshopSearch(database, index.getIfAvailable(), properties.timeout(),
                properties.circuitOpenFor(), System::nanoTime);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "seatwise.search", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableScheduling
    static class IndexConfiguration {

        @Bean
        MeilisearchClients meilisearchClients(SeatwiseProperties properties) {
            SeatwiseProperties.Search search = properties.search();
            return new MeilisearchClients(search == null ? null : search.url(), search == null ? null : search.masterKey());
        }

        @Bean
        MeiliWorkshopSearch meiliWorkshopSearch(
                MeilisearchClients clients, WorkshopCatalogue catalogue, Clock clock, SeatwiseProperties properties) {
            ZoneId centreZone = Objects.requireNonNullElse(properties.centreTimezone(), ZoneId.of("Europe/London"));
            return new MeiliWorkshopSearch(clients, catalogue, clock, centreZone);
        }

        @Bean
        WorkshopIndexer workshopIndexer(WorkshopCatalogue catalogue, MeilisearchClients clients) {
            return new WorkshopIndexer(catalogue, clients);
        }

        @Bean
        SearchIndexBootstrap searchIndexBootstrap(MeilisearchClients clients, WorkshopIndexer indexer) {
            return new SearchIndexBootstrap(clients, indexer);
        }
    }
}
