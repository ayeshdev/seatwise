package com.seatwise.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ProductionSecretsGuardTest {

    @Test
    void realSecretsAreAccepted() {
        // Arrange
        SeatwiseProperties properties = properties("real-provisioner", "Str0ng&Unique!", "real-master-key");

        // Act / Assert
        assertThatCode(() -> ProductionSecretsGuard.verify(properties)).doesNotThrowAnyException();
    }

    @Test
    void devProvisionerSecretIsRefused() {
        // Arrange
        SeatwiseProperties properties = properties("dev-provisioner-secret", "Str0ng&Unique!", "real-master-key");

        // Act / Assert
        assertThatThrownBy(() -> ProductionSecretsGuard.verify(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEATWISE_PROVISIONER_SECRET")
                .hasMessageContaining("development default");
    }

    @Test
    void devAdminPasswordIsRefused() {
        // Arrange
        SeatwiseProperties properties = properties("real-provisioner", "Admin#Seatwise1", "real-master-key");

        // Act / Assert
        assertThatThrownBy(() -> ProductionSecretsGuard.verify(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEATWISE_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void devSearchMasterKeyIsRefused() {
        // Arrange
        SeatwiseProperties properties =
                properties("real-provisioner", "Str0ng&Unique!", "dev-search-master-key-change-me");

        // Act / Assert
        assertThatThrownBy(() -> ProductionSecretsGuard.verify(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEATWISE_SEARCH_MASTER_KEY");
    }

    @Test
    void blankSecretIsRefusedAndEveryOffenderIsListed() {
        // Arrange
        SeatwiseProperties properties = properties(" ", "Admin#Seatwise1", "");

        // Act / Assert
        assertThatThrownBy(() -> ProductionSecretsGuard.verify(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEATWISE_PROVISIONER_SECRET) is not set")
                .hasMessageContaining("SEATWISE_BOOTSTRAP_ADMIN_PASSWORD")
                .hasMessageContaining("SEATWISE_SEARCH_MASTER_KEY");
    }

    @Test
    void messageNeverRepeatsTheSecretValue() {
        // Arrange
        SeatwiseProperties properties = properties("dev-provisioner-secret", "Admin#Seatwise1", "real-master-key");

        // Act / Assert
        assertThatThrownBy(() -> ProductionSecretsGuard.verify(properties)).satisfies(e -> {
            assertThat(e.getMessage()).doesNotContain("dev-provisioner-secret");
            assertThat(e.getMessage()).doesNotContain("Admin#Seatwise1");
        });
    }

    private static SeatwiseProperties properties(String provisionerSecret, String adminPassword, String masterKey) {
        return new SeatwiseProperties(
                new SeatwiseProperties.Oidc("http://issuer", "http://jwks", "seatwise-api"),
                new SeatwiseProperties.Keycloak("http://kc", "seatwise", "seatwise-provisioner", provisionerSecret),
                new SeatwiseProperties.Bootstrap(true, "admin@example.com", adminPassword, "Admin", true),
                new SeatwiseProperties.Demo("m", "s"),
                new SeatwiseProperties.Search("http://search", masterKey),
                ZoneId.of("Europe/London"));
    }
}
