package com.seatwise.common.security;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatwise.accounts.internal.StaffAccountService;
import com.seatwise.accounts.internal.StaffAuthenticationConverter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * How the real filter chain answers when authentication can't be completed:
 * a refused token is a 401 that says so (RFC 6750), a database outage while
 * loading the staff row is a retryable 503.
 */
@WebMvcTest
@Import({SecurityConfig.class, ActorProvider.class})
class AuthenticationFailureResponsesTest {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StaffAccountService staffAccountService;

    @MockitoBean
    private StaffAuthenticationConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void refusedTokenIsAnInvalidTokenChallenge() throws Exception {
        // Arrange
        when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("expired"));

        // Act / Assert
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void requestWithoutATokenGetsTheBareBearerChallenge() throws Exception {
        // Act / Assert
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void databaseOutageWhileLoadingTheStaffRowIsAServiceUnavailableProblem() throws Exception {
        // Arrange
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(jwtDecoder.decode("good-token")).thenReturn(token);
        when(converter.convert(eq(token)))
                .thenThrow(new AuthenticationUnavailableException("db down", new RuntimeException("boom")));

        // Act / Assert
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer good-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail")
                        .value("Seatwise is having trouble right now. Please try again in a moment."));
    }
}
