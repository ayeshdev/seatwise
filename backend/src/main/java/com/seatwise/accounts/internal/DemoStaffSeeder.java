package com.seatwise.accounts.internal;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.security.StaffRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demo profile only: a Manager and a Staff login so reviewers can try every
 * role straight away. Passwords are not temporary. Runs after the Admin
 * bootstrap and shares its switch, since both need a reachable Keycloak.
 */
@Component
@Profile("demo")
@Order(AdminBootstrapRunner.ORDER + 10)
@ConditionalOnProperty(name = "seatwise.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
class DemoStaffSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStaffSeeder.class);

    private record DemoUser(String email, String fullName, StaffRole role, String password) {}

    private final StaffAccountService accounts;
    private final IdentityProvisioner identity;
    private final List<DemoUser> users;

    DemoStaffSeeder(StaffAccountService accounts, IdentityProvisioner identity, SeatwiseProperties properties) {
        this.accounts = accounts;
        this.identity = identity;
        this.users = List.of(
                new DemoUser("manager@seatwise.local", "Morgan Reyes", StaffRole.MANAGER,
                        properties.demo().managerPassword()),
                new DemoUser("staff@seatwise.local", "Sam Taylor", StaffRole.STAFF,
                        properties.demo().staffPassword()));
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DemoUser user : users) {
            try {
                seed(user);
            } catch (DomainException e) {
                log.error("Demo seeding FAILED for {}: {} ({})", user.email(), e.detail(), e.code());
            }
        }
    }

    private void seed(DemoUser user) {
        if (accounts.existsByEmail(user.email())) {
            log.info("Demo seeding: {} already exists", user.email());
            return;
        }
        UUID id = IdentityRetry.call("Demo seeding", () -> {
            Optional<UUID> existing = identity.findUserIdByEmail(user.email());
            if (existing.isPresent()) {
                // Left over from an earlier run with a wiped database: its
                // password is unknown, so set the configured one again.
                identity.resetPassword(existing.get(), user.password(), false);
                return existing.get();
            }
            return identity.createUser(user.email(), user.fullName(), user.password(), false);
        });
        accounts.ensureSystemAccount(id, user.email(), user.fullName(), user.role());
        log.info("Demo seeding: {} ready as {}", user.email(), user.role());
    }
}
