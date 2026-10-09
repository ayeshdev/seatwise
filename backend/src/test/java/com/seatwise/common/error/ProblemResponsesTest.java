package com.seatwise.common.error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatwise.accounts.internal.StaffAccountService;
import com.seatwise.accounts.internal.StaffAuthenticationConverter;
import com.seatwise.common.security.AccountInactiveException;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.common.security.SecurityConfig;
import com.seatwise.common.security.StaffAuthenticationToken;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The problem+json contract: every refusal, from the filter chain or from MVC,
 * has a stable {@code code}. Uses the real filter chain with a stubbed token
 * decoder, so the converter -> principal -> ActorProvider path is exercised too.
 */
@WebMvcTest
@Import({SecurityConfig.class, ActorProvider.class})
class ProblemResponsesTest {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
    private static final UUID ID = UUID.fromString("6f1d8e2a-4b3c-4d5e-8f90-a1b2c3d4e5f6");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StaffAccountService staffAccountService;

    @MockitoBean
    private StaffAuthenticationConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void missingTokenIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenForADeactivatedAccountIsRefusedWithAccountInactive() throws Exception {
        // Arrange
        Jwt token = jwtFor(ID);
        when(jwtDecoder.decode("inactive-user")).thenReturn(token);
        when(converter.convert(eq(token))).thenThrow(new AccountInactiveException("inactive"));

        // Act / Assert
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer inactive-user"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void validTokenReachesTheControllerAsTheStaffPrincipal() throws Exception {
        // Arrange
        Jwt token = jwtFor(ID);
        StaffPrincipal sam = new StaffPrincipal(ID, "sam@example.com", "Sam Taylor", StaffRole.STAFF);
        when(jwtDecoder.decode("staff-user")).thenReturn(token);
        when(converter.convert(eq(token))).thenReturn(new StaffAuthenticationToken(token, sam));

        // Act / Assert
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer staff-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.fullName").value("Sam Taylor"))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts").with(staff()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidBodyListsTheFields() throws Exception {
        mvc.perform(post("/api/v1/staff-accounts")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","fullName":"","role":"STAFF","temporaryPassword":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'temporaryPassword')]").exists());
    }

    @Test
    void pageSizeAboveTheLimitIsAValidationFailure() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts").param("size", "500").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void unreadableBodyIsAValidationFailure() throws Exception {
        mvc.perform(post("/api/v1/staff-accounts")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void domainExceptionKeepsItsCodeStatusAndDetail() throws Exception {
        // Arrange
        when(staffAccountService.get(ID)).thenThrow(new DomainException(ErrorCode.NOT_FOUND, "No such account."));

        // Act / Assert
        mvc.perform(get("/api/v1/staff-accounts/{id}", ID).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value(ErrorCode.NOT_FOUND.title()))
                .andExpect(jsonPath("$.detail").value("No such account."));
    }

    @Test
    void namedConstraintViolationMapsToItsCodeWithoutLeakingSql() throws Exception {
        // Arrange
        PSQLException driverError = new PSQLException(new ServerErrorMessage(
                "SERROR\0C23505\0Mduplicate key value violates unique constraint \"uq_staff_account_email\"\0"
                        + "nuq_staff_account_email\0"));
        when(staffAccountService.create(any())).thenThrow(
                new DataIntegrityViolationException("could not execute statement [insert into ...]", driverError));

        // Act / Assert
        mvc.perform(post("/api/v1/staff-accounts")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@example.com","fullName":"A","role":"STAFF","temporaryPassword":"Temporary#Pass1"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("insert"))));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedFailureIsAGenericInternalError() throws Exception {
        // Arrange
        when(staffAccountService.get(ID)).thenThrow(new IllegalStateException("secret internals"));

        // Act / Assert
        mvc.perform(get("/api/v1/staff-accounts/{id}", ID).with(admin()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor staff() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"));
    }

    private static Jwt jwtFor(UUID id) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(id.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
