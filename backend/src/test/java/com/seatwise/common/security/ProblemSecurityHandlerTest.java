package com.seatwise.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

class ProblemSecurityHandlerTest {

    private final ProblemSecurityHandler handler = new ProblemSecurityHandler(JsonMapper.builder().build());

    @Test
    void authenticationServiceExceptionIsAServiceUnavailableProblem() throws Exception {
        // Arrange
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        handler.commence(new MockHttpServletRequest("GET", "/api/v1/me"), response,
                new AuthenticationServiceException("db down"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("SERVICE_UNAVAILABLE")
                .contains("Seatwise is having trouble right now. Please try again in a moment.")
                .doesNotContain("db down");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void oauth2AuthenticationFailureNamesInvalidToken() throws Exception {
        // Arrange
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        handler.commence(new MockHttpServletRequest("GET", "/api/v1/me"), response,
                new InvalidBearerTokenException("expired"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    void otherAuthenticationFailuresGetTheBareBearerChallenge() throws Exception {
        // Arrange
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        handler.commence(new MockHttpServletRequest("GET", "/api/v1/me"), response,
                new BadCredentialsException("no"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }
}
