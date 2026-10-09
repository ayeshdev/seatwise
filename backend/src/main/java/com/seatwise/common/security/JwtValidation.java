package com.seatwise.common.security;

import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * The token checks applied after the signature is verified: timestamps and
 * issuer, plus the audience. Kept out of {@code SecurityConfig} so a test can
 * run the exact chain the decoder uses.
 */
final class JwtValidation {

    private JwtValidation() {}

    static OAuth2TokenValidator<Jwt> validator(String issuer, String requiredAudience) {
        // createDefaultWithIssuer already covers the timestamp check.
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator(requiredAudience));
    }

    // Keycloak puts "aud" in an access token only through the audience mapper,
    // so a token minted for another client of the realm is refused here.
    private static OAuth2TokenValidator<Jwt> audienceValidator(String requiredAudience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "The required audience is missing", null);
        return jwt -> {
            List<String> audience = jwt.getAudience();
            return audience != null && audience.contains(requiredAudience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(error);
        };
    }
}
