package com.seatwise.common.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for every {@code seatwise.*} setting. All values come from
 * environment variables via application.yml so one image runs everywhere.
 */
@ConfigurationProperties("seatwise")
public record SeatwiseProperties(
        Oidc oidc, Keycloak keycloak, Bootstrap bootstrap, Search search, ZoneId centreTimezone) {

    /** Meilisearch connection; the index is derived data and can be rebuilt from PostgreSQL. */
    public record Search(String url, String masterKey) {}

    /** Token validation settings: issuer is the public URL, JWKS may be an internal one. */
    public record Oidc(String issuer, String jwksUri, String audience) {}

    /** Service-account access to the Keycloak Admin REST API. */
    public record Keycloak(String adminBaseUrl, String realm, String provisionerClientId, String provisionerSecret) {}

    /** First-Admin seed; the password is only temporary outside dev/demo. */
    public record Bootstrap(String adminEmail, String adminPassword, String adminFullName, boolean passwordTemporary) {}
}
