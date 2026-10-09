package com.seatwise.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the {@code prod} profile with any of the development
 * secrets that ship in application.yml and compose.yaml. Those values are
 * public (they are in the repository), so running with them would be an
 * unauthenticated door. Failing the startup is deliberate: a loud crash on
 * deploy is better than a quietly guessable admin password.
 *
 * <p>The message names the setting and its environment variable, never the
 * value.
 */
@Component
@Profile("prod")
class ProductionSecretsGuard {

    static final String DEV_PROVISIONER_SECRET = "dev-provisioner-secret";
    static final String DEV_ADMIN_PASSWORD = "Admin#Seatwise1";
    static final String DEV_SEARCH_MASTER_KEY = "dev-search-master-key-change-me";

    ProductionSecretsGuard(SeatwiseProperties properties) {
        verify(properties);
    }

    static void verify(SeatwiseProperties properties) {
        List<String> problems = new ArrayList<>();
        check(problems, "seatwise.keycloak.provisioner-secret (SEATWISE_PROVISIONER_SECRET)",
                properties.keycloak() == null ? null : properties.keycloak().provisionerSecret(),
                DEV_PROVISIONER_SECRET);
        check(problems, "seatwise.bootstrap.admin-password (SEATWISE_BOOTSTRAP_ADMIN_PASSWORD)",
                properties.bootstrap() == null ? null : properties.bootstrap().adminPassword(),
                DEV_ADMIN_PASSWORD);
        check(problems, "seatwise.search.master-key (SEATWISE_SEARCH_MASTER_KEY)",
                properties.search() == null ? null : properties.search().masterKey(),
                DEV_SEARCH_MASTER_KEY);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with the prod profile: " + String.join("; ", problems)
                            + ". Set real secrets in the environment.");
        }
    }

    private static void check(List<String> problems, String setting, String value, String devDefault) {
        if (value == null || value.isBlank()) {
            problems.add(setting + " is not set");
        } else if (value.equals(devDefault)) {
            problems.add(setting + " is still the development default");
        }
    }
}
