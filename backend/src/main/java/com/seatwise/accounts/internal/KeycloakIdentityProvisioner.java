package com.seatwise.accounts.internal;

import com.seatwise.common.config.SeatwiseProperties;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Talks to the Keycloak Admin REST API as the confidential client
 * {@code seatwise-provisioner}, whose service account only has the
 * realm-management user roles. Uses the client-credentials grant; the token is
 * cached until shortly before it expires.
 */
@Component
class KeycloakIdentityProvisioner implements IdentityProvisioner {

    private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityProvisioner.class);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {};

    // Refresh early so a token never expires between "check" and "use".
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    static final String PASSWORD_REJECTED =
            "Password must be at least 10 characters and must not be the email address";

    private final RestClient http;
    private final SeatwiseProperties.Keycloak config;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedTokenExpiresAt = Instant.EPOCH;

    KeycloakIdentityProvisioner(SeatwiseProperties properties, Clock clock) {
        this.config = properties.keycloak();
        this.clock = clock;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        // Strictly encode URI variables: the default leaves '+' in a query value
        // as-is, which Keycloak decodes as a space and then misses the user.
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory(stripTrailingSlash(config.adminBaseUrl()));
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        this.http = RestClient.builder()
                .uriBuilderFactory(uriFactory)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public UUID createUser(String email, String fullName, String password, boolean temporaryPassword) {
        String[] names = splitName(fullName);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", email);
        user.put("email", email);
        user.put("emailVerified", true);
        user.put("enabled", true);
        // Keycloak 26's default user profile requires both first and last name.
        user.put("firstName", names[0]);
        user.put("lastName", names[1]);
        user.put("credentials", List.of(credential(password, temporaryPassword)));

        URI location = admin(() -> http.post()
                .uri("/admin/realms/{realm}/users", config.realm())
                .headers(this::bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation());
        if (location == null) {
            log.error("Keycloak created a user but returned no Location header");
            throw unavailable();
        }
        String path = location.getPath();
        try {
            return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        } catch (IllegalArgumentException notAUuid) {
            log.error("Keycloak created a user but its Location header has no user id: {}", location);
            throw unavailable();
        }
    }

    @Override
    public Optional<UUID> findUserIdByEmail(String email) {
        List<Map<String, Object>> users = admin(() -> http.get()
                .uri("/admin/realms/{realm}/users?email={email}&exact=true", config.realm(), email)
                .headers(this::bearer)
                .retrieve()
                .body(JSON_ARRAY));
        if (users == null) {
            return Optional.empty();
        }
        try {
            return users.stream()
                    .filter(u -> email.equalsIgnoreCase(String.valueOf(u.get("email"))))
                    .map(u -> UUID.fromString(String.valueOf(u.get("id"))))
                    .findFirst();
        } catch (IllegalArgumentException notAUuid) {
            log.error("Keycloak returned a user whose id is not a UUID");
            throw unavailable();
        }
    }

    @Override
    public void setEnabled(UUID userId, boolean enabled) {
        // Read-modify-write the full representation: with Keycloak's user
        // profile a partial PUT can be validated as if other fields were removed.
        Map<String, Object> user = admin(() -> http.get()
                .uri("/admin/realms/{realm}/users/{id}", config.realm(), userId)
                .headers(this::bearer)
                .retrieve()
                .body(JSON_OBJECT));
        if (user == null) {
            throw unavailable();
        }
        Map<String, Object> updated = new LinkedHashMap<>(user);
        updated.put("enabled", enabled);
        admin(() -> http.put()
                .uri("/admin/realms/{realm}/users/{id}", config.realm(), userId)
                .headers(this::bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updated)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void resetPassword(UUID userId, String password, boolean temporaryPassword) {
        admin(() -> http.put()
                .uri("/admin/realms/{realm}/users/{id}/reset-password", config.realm(), userId)
                .headers(this::bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(credential(password, temporaryPassword))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void deleteUser(UUID userId) {
        try {
            admin(() -> http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", config.realm(), userId)
                    .headers(this::bearer)
                    .retrieve()
                    .toBodilessEntity());
        } catch (DomainException e) {
            if (e.code() != ErrorCode.NOT_FOUND) {
                throw e;
            }
        }
    }

    /**
     * Runs an Admin API call and maps every failure to a domain error. A 401
     * usually means our cached token was revoked (e.g. Keycloak restarted), so
     * the token is dropped and the call retried once.
     */
    private <T> T admin(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                invalidateToken();
                try {
                    return call.get();
                } catch (RestClientException retry) {
                    throw translate(retry);
                }
            }
            throw translate(e);
        } catch (RestClientException e) {
            throw translate(e);
        }
    }

    private DomainException translate(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            String body = response.getResponseBodyAsString();
            if (status == HttpStatus.CONFLICT.value()) {
                return new DomainException(
                        ErrorCode.EMAIL_IN_USE, "A sign-in account with that email address already exists.");
            }
            if (status == HttpStatus.BAD_REQUEST.value()) {
                if (body.toLowerCase(Locale.ROOT).contains("password")) {
                    return new DomainException(ErrorCode.VALIDATION_FAILED, PASSWORD_REJECTED);
                }
                log.warn("Keycloak rejected a user request with 400: {}", body);
                return new DomainException(
                        ErrorCode.VALIDATION_FAILED, "The sign-in service rejected these account details.");
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                // Only deleteUser treats this as normal; elsewhere it is an
                // inconsistency between Keycloak and the database worth logging.
                log.warn("Keycloak user not found ({} {})", status, body);
                return new DomainException(
                        ErrorCode.NOT_FOUND, "The sign-in account for this staff member could not be found.");
            }
            log.error("Keycloak Admin API answered {}: {}", status, body);
            return unavailable();
        }
        if (e instanceof ResourceAccessException) {
            log.warn("Keycloak Admin API unreachable: {}", e.getMessage());
            return unavailable();
        }
        log.error("Keycloak Admin API call failed", e);
        return unavailable();
    }

    private static DomainException unavailable() {
        return new DomainException(
                ErrorCode.IDENTITY_UNAVAILABLE,
                "The sign-in service can't be reached right now, so nothing was changed. Please try again shortly.");
    }

    private void bearer(HttpHeaders headers) {
        headers.setBearerAuth(accessToken());
    }

    private synchronized void invalidateToken() {
        cachedToken = null;
        cachedTokenExpiresAt = Instant.EPOCH;
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedTokenExpiresAt)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.provisionerClientId());
        form.add("client_secret", config.provisionerSecret());
        Map<String, Object> token;
        try {
            token = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", config.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // 404 = no such realm at that address (realm not imported, or the admin
            // base URL points at the wrong service); 400/401 = client or secret rejected.
            String hint = status == 404
                    ? "realm '" + config.realm() + "' not found at " + config.adminBaseUrl()
                            + " - check the realm was imported and SEATWISE_KEYCLOAK_ADMIN_BASE"
                    : "check SEATWISE_PROVISIONER_SECRET and the seatwise-provisioner client";
            log.error("Keycloak refused the provisioner token request ({}): {}", status, hint);
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Keycloak token endpoint unreachable: {}", e.getMessage());
            throw unavailable();
        }
        if (token == null || !(token.get("access_token") instanceof String accessToken)) {
            log.error("Keycloak token response had no access_token");
            throw unavailable();
        }
        long expiresIn = token.get("expires_in") instanceof Number n ? n.longValue() : 60;
        cachedToken = accessToken;
        cachedTokenExpiresAt = now.plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        return cachedToken;
    }

    private static Map<String, Object> credential(String password, boolean temporary) {
        return Map.of("type", "password", "value", password, "temporary", temporary);
    }

    /** "Morgan Reyes" → [Morgan, Reyes]; a single word is used for both names. */
    static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, trimmed};
        }
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
