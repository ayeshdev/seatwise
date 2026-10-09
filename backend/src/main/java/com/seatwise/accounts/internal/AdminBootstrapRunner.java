package com.seatwise.accounts.internal;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.security.StaffRole;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * FR-AUTH-02: there is no public sign-up, so on startup the first Admin is
 * created from configuration if no active Admin exists. Idempotent: an
 * existing Keycloak user with that email is linked rather than recreated (its
 * password is left alone), and nothing happens once any active Admin exists.
 *
 * <p>A failure is logged, not fatal: the API stays up, and a restart (once
 * Keycloak is reachable) completes the bootstrap.
 */
@Component
@Order(AdminBootstrapRunner.ORDER)
@ConditionalOnProperty(name = "seatwise.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
class AdminBootstrapRunner implements ApplicationRunner {

    static final int ORDER = 0;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final StaffAccountService accounts;
    private final IdentityProvisioner identity;
    private final SeatwiseProperties.Bootstrap config;

    AdminBootstrapRunner(StaffAccountService accounts, IdentityProvisioner identity, SeatwiseProperties properties) {
        this.accounts = accounts;
        this.identity = identity;
        this.config = properties.bootstrap();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.hasActiveAdmin()) {
            log.info("Admin bootstrap: an active Admin exists, nothing to do");
            return;
        }
        String email = config.adminEmail();
        log.info("Admin bootstrap: no active Admin found, provisioning {}", email);
        try {
            AtomicBoolean linkedExisting = new AtomicBoolean();
            UUID id = IdentityRetry.call("Admin bootstrap", () -> {
                Optional<UUID> existing = identity.findUserIdByEmail(email);
                linkedExisting.set(existing.isPresent());
                existing.ifPresent(found -> log.info("Admin bootstrap: linking existing Keycloak user {}", found));
                return existing.orElseGet(() -> identity.createUser(
                        email, config.adminFullName(), config.adminPassword(), config.passwordTemporary()));
            });
            boolean recovered = accounts.ensureSystemAccount(id, email, config.adminFullName(), StaffRole.ADMIN);
            if (recovered || linkedExisting.get()) {
                // A linked Keycloak user may have been disabled by hand (it is not
                // ours to assume otherwise), and a recovered row may have been
                // deactivated; in both cases the Admin must be able to sign in.
                IdentityRetry.run("Admin bootstrap", () -> identity.setEnabled(id, true));
            }
            log.info("Admin bootstrap: {} is an active Admin (temporary password: {})",
                    email, config.passwordTemporary());
        } catch (DomainException e) {
            log.error("Admin bootstrap FAILED for {}: {} ({}). Nobody can manage accounts until this succeeds; "
                    + "restart the API once Keycloak is reachable.", email, e.detail(), e.code());
        }
    }
}
