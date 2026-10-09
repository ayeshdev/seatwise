package com.seatwise.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared PostgreSQL for integration tests. Import it with
 * {@code @Import(TestcontainersConfiguration.class)}; @ServiceConnection wires
 * the datasource so no URL or credentials are configured by hand.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same major version as production so SQL behaves identically in tests.
    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
