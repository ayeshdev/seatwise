package com.seatwise.accounts.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.security.StaffRole;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AdminBootstrapRunnerTest {

    private static final String EMAIL = "admin@example.com";

    private final StaffAccountService accounts = mock(StaffAccountService.class);
    private final IdentityProvisioner identity = mock(IdentityProvisioner.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);
    private final AdminBootstrapRunner runner = new AdminBootstrapRunner(accounts, identity, properties());

    @Test
    void linkedExistingKeycloakUserIsEnabledEvenWhenTheRowDidNotChange() {
        // Arrange: someone disabled the Keycloak user by hand; the staff row is already a fine Admin.
        UUID keycloakId = UUID.randomUUID();
        when(accounts.hasActiveAdmin()).thenReturn(false);
        when(identity.findUserIdByEmail(EMAIL)).thenReturn(Optional.of(keycloakId));
        when(accounts.ensureSystemAccount(keycloakId, EMAIL, "Centre Administrator", StaffRole.ADMIN))
                .thenReturn(false);

        // Act
        runner.run(args);

        // Assert
        verify(identity, never()).createUser(anyString(), anyString(), anyString(), anyBoolean());
        verify(identity).setEnabled(keycloakId, true);
    }

    @Test
    void newlyCreatedKeycloakUserIsNotTouchedAgain() {
        // Arrange
        UUID keycloakId = UUID.randomUUID();
        when(accounts.hasActiveAdmin()).thenReturn(false);
        when(identity.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(identity.createUser(EMAIL, "Centre Administrator", "Secret#Pass123", true)).thenReturn(keycloakId);
        when(accounts.ensureSystemAccount(keycloakId, EMAIL, "Centre Administrator", StaffRole.ADMIN))
                .thenReturn(false);

        // Act
        runner.run(args);

        // Assert
        verify(identity, never()).setEnabled(any(), anyBoolean());
    }

    @Test
    void recoveredRowEnablesTheKeycloakUser() {
        // Arrange: no linked user, but the existing row was demoted and is now restored.
        UUID keycloakId = UUID.randomUUID();
        when(accounts.hasActiveAdmin()).thenReturn(false);
        when(identity.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(identity.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(keycloakId);
        when(accounts.ensureSystemAccount(eq(keycloakId), anyString(), anyString(), eq(StaffRole.ADMIN)))
                .thenReturn(true);

        // Act
        runner.run(args);

        // Assert
        verify(identity).setEnabled(keycloakId, true);
    }

    @Test
    void nothingHappensWhenAnActiveAdminExists() {
        // Arrange
        when(accounts.hasActiveAdmin()).thenReturn(true);

        // Act
        runner.run(args);

        // Assert
        verify(identity, never()).findUserIdByEmail(anyString());
    }

    private static SeatwiseProperties properties() {
        return new SeatwiseProperties(
                null,
                null,
                new SeatwiseProperties.Bootstrap(true, EMAIL, "Secret#Pass123", "Centre Administrator", true),
                null,
                null,
                ZoneId.of("Europe/London"));
    }
}
