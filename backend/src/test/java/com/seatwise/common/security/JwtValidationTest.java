package com.seatwise.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Runs real signed tokens through the same signature check and validator
 * chain that {@code SecurityConfig.jwtDecoder} builds, with a local key
 * instead of Keycloak's JWKS.
 */
class JwtValidationTest {

    private static final String ISSUER = "http://localhost:8281/realms/seatwise";
    private static final String AUDIENCE = "seatwise-api";

    private static RSAPrivateKey privateKey;
    private static JwtDecoder decoder;

    @BeforeAll
    static void createKeyAndDecoder() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        nimbus.setJwtValidator(JwtValidation.validator(ISSUER, AUDIENCE));
        decoder = nimbus;
    }

    @Test
    void tokenWithTheRightIssuerAudienceAndLifetimeIsAccepted() throws Exception {
        // Arrange
        String subject = UUID.randomUUID().toString();
        String token = sign(claims(ISSUER, AUDIENCE, Instant.now().plus(Duration.ofMinutes(5))).subject(subject));

        // Act
        Jwt jwt = decoder.decode(token);

        // Assert
        assertThat(jwt.getSubject()).isEqualTo(subject);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        // Arrange
        String token = sign(claims("http://evil.example/realms/seatwise", AUDIENCE,
                Instant.now().plus(Duration.ofMinutes(5))));

        // Act / Assert
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenWithoutAnAudienceIsRejected() throws Exception {
        // Arrange
        JWTClaimsSet.Builder noAudience = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(5))));
        String token = sign(noAudience);

        // Act / Assert
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        // Arrange
        String token = sign(claims(ISSUER, "some-other-api", Instant.now().plus(Duration.ofMinutes(5))));

        // Act / Assert
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        // Arrange: well past the default 60 s clock skew.
        String token = sign(claims(ISSUER, AUDIENCE, Instant.now().minus(Duration.ofMinutes(10))));

        // Act / Assert
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private static JWTClaimsSet.Builder claims(String issuer, String audience, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(expiresAt.minus(Duration.ofMinutes(5))))
                .expirationTime(Date.from(expiresAt));
    }

    private static String sign(JWTClaimsSet.Builder claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
}
