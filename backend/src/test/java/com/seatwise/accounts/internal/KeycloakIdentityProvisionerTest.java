package com.seatwise.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Keycloak Admin client against a local JDK HTTP server standing in for
 * Keycloak, so status mapping and URL encoding are checked on the wire.
 */
class KeycloakIdentityProvisionerTest {

    private static final String REALM = "seatwise";
    private static final String USERS_PATH = "/admin/realms/" + REALM + "/users";

    private HttpServer server;
    private KeycloakIdentityProvisioner provisioner;

    private volatile Reply usersReply = new Reply(201, "", null);
    private volatile Reply tokenReply = new Reply(200, "{\"access_token\":\"t\",\"expires_in\":300}", null);
    private final List<String> requestLines = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startFakeKeycloak() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/realms/" + REALM + "/protocol/openid-connect/token", exchange -> answer(exchange, tokenReply));
        server.createContext("/admin", exchange -> answer(exchange, usersReply));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        SeatwiseProperties properties = new SeatwiseProperties(
                null,
                new SeatwiseProperties.Keycloak(baseUrl, REALM, "seatwise-provisioner", "secret"),
                null,
                null,
                null,
                ZoneId.of("Europe/London"));
        provisioner = new KeycloakIdentityProvisioner(properties, Clock.systemUTC());
    }

    @AfterEach
    void stopFakeKeycloak() {
        server.stop(0);
    }

    @Test
    void createdUserIdIsParsedFromTheLocationHeader() {
        // Arrange
        UUID id = UUID.randomUUID();
        usersReply = new Reply(201, "", "http://keycloak/admin/realms/seatwise/users/" + id);

        // Act
        UUID created = provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true);

        // Assert
        assertThat(created).isEqualTo(id);
        assertThat(requestLines).contains("POST " + USERS_PATH);
    }

    @Test
    void missingLocationHeaderIsIdentityUnavailable() {
        // Arrange
        usersReply = new Reply(201, "", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
    }

    @Test
    void locationHeaderWithoutAUuidIsIdentityUnavailableNotARawException() {
        // Arrange
        usersReply = new Reply(201, "", "http://keycloak/admin/realms/seatwise/users/not-a-uuid");

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
    }

    @Test
    void conflictIsEmailInUse() {
        // Arrange
        usersReply = new Reply(409, "{\"errorMessage\":\"User exists with same username\"}", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_IN_USE));
    }

    @Test
    void passwordPolicyRejectionIsValidationFailedWithThePasswordMessage() {
        // Arrange
        usersReply = new Reply(400, "{\"error\":\"password_policy\",\"errorMessage\":\"Invalid password: minimum length 10.\"}", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "short", true))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.detail()).isEqualTo(KeycloakIdentityProvisioner.PASSWORD_REJECTED);
                });
    }

    @Test
    void otherBadRequestIsValidationFailedWithAGenericMessage() {
        // Arrange
        usersReply = new Reply(400, "{\"errorMessage\":\"Please specify first name\"}", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.detail()).isNotEqualTo(KeycloakIdentityProvisioner.PASSWORD_REJECTED);
                });
    }

    @Test
    void serverErrorIsIdentityUnavailable() {
        // Arrange
        usersReply = new Reply(503, "{\"error\":\"down\"}", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
    }

    @Test
    void refusedProvisionerCredentialsAreIdentityUnavailable() {
        // Arrange
        tokenReply = new Reply(401, "{\"error\":\"unauthorized_client\"}", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
    }

    @Test
    void unreachableKeycloakIsIdentityUnavailable() {
        // Arrange
        server.stop(0);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.createUser("dana@example.com", "Dana Lee", "Temporary#Pass1", true))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
    }

    @Test
    void plusInAnEmailIsPercentEncodedInTheLookupQuery() {
        // Arrange
        UUID id = UUID.randomUUID();
        usersReply = new Reply(200, "[{\"id\":\"" + id + "\",\"email\":\"dana+workshops@example.com\"}]", null);

        // Act
        Optional<UUID> found = provisioner.findUserIdByEmail("dana+workshops@example.com");

        // Assert: a bare '+' would reach Keycloak as a space and miss the user.
        assertThat(found).contains(id);
        assertThat(requestLines).anySatisfy(line -> {
            assertThat(line).contains("email=dana%2Bworkshops%40example.com");
            assertThat(line).contains("exact=true");
        });
    }

    @Test
    void lookupIgnoresUsersWhoseEmailOnlyPartiallyMatches() {
        // Arrange
        usersReply = new Reply(200, "[{\"id\":\"" + UUID.randomUUID() + "\",\"email\":\"other@example.com\"}]", null);

        // Act / Assert
        assertThat(provisioner.findUserIdByEmail("dana@example.com")).isEmpty();
    }

    @Test
    void deletingAUserKeycloakDoesNotKnowIsNotAnError() {
        // Arrange
        usersReply = new Reply(404, "{\"error\":\"User not found\"}", null);

        // Act / Assert
        assertThatCode(() -> provisioner.deleteUser(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void deletingAUserFailsWithIdentityUnavailableOnServerError() {
        // Arrange
        usersReply = new Reply(500, "", null);

        // Act / Assert
        assertThatThrownBy(() -> provisioner.deleteUser(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDENTITY_UNAVAILABLE));
    }

    // ---- fake server ----

    private record Reply(int status, String body, String location) {}

    private void answer(HttpExchange exchange, Reply reply) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        requestLines.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath()
                + (query == null ? "" : "?" + query));
        exchange.getRequestBody().readAllBytes();
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (reply.location() != null) {
            exchange.getResponseHeaders().add("Location", reply.location());
        }
        // A zero length means "no body" to the JDK server.
        exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
