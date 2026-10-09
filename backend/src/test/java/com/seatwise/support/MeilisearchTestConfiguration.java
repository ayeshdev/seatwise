package com.seatwise.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A Meilisearch container for the tests that opt into the index. Import it
 * next to {@link TestcontainersConfiguration} and also set
 * {@code seatwise.search.enabled=true} as a test property: the test profile
 * switches the index off, and that switch is read while the context is being
 * defined, before this registrar can supply anything.
 *
 * <p>The container is a bean, so it starts with the context and stops when
 * the context closes; a test that stops it on purpose should dirty the context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MeilisearchTestConfiguration {

    public static final String MASTER_KEY = "test-master-key-for-meilisearch-it";

    private static final int PORT = 7700;

    // Same version as compose.yaml.
    @Bean
    public GenericContainer<?> meilisearchContainer() {
        return new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.43.0"))
                .withEnv("MEILI_MASTER_KEY", MASTER_KEY)
                .withEnv("MEILI_ENV", "development")
                .withEnv("MEILI_NO_ANALYTICS", "true")
                .withExposedPorts(PORT)
                .waitingFor(Wait.forHttp("/health").forPort(PORT).forStatusCode(200));
    }

    @Bean
    public DynamicPropertyRegistrar meilisearchProperties(GenericContainer<?> meilisearchContainer) {
        return registry -> {
            registry.add("seatwise.search.url",
                    () -> "http://" + meilisearchContainer.getHost() + ":" + meilisearchContainer.getMappedPort(PORT));
            registry.add("seatwise.search.master-key", () -> MASTER_KEY);
        };
    }
}
