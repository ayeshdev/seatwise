package com.seatwise.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.seatwise.common.security.AccountInactiveException;
import com.seatwise.common.security.AuthenticationUnavailableException;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class StaffAuthenticationConverterTest {

    private final StaffAccountRepository repository = mock(StaffAccountRepository.class);
    private final StaffAuthenticationConverter converter = new StaffAuthenticationConverter(repository);

    @Test
    void activeAccountGetsExactlyOneRoleAuthorityAndAStaffPrincipal() {
        // Arrange
        UUID id = UUID.randomUUID();
        StaffAccountEntity account =
                new StaffAccountEntity(id, "Morgan@Example.com", "Morgan Reyes", StaffRole.MANAGER, null, Instant.EPOCH);
        when(repository.findById(id)).thenReturn(Optional.of(account));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwtFor(id.toString()));

        // Assert
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MANAGER");
        assertThat(token.getPrincipal())
                .isEqualTo(new StaffPrincipal(id, "morgan@example.com", "Morgan Reyes", StaffRole.MANAGER));
        assertThat(token.getName()).isEqualTo(id.toString());
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    void roleClaimsInTheTokenAreIgnored() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(
                new StaffAccountEntity(id, "sam@example.com", "Sam Taylor", StaffRole.STAFF, null, Instant.EPOCH)));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(id.toString())
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("ADMIN")))
                .claim("scope", "openid admin")
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_STAFF");
    }

    @Test
    void inactiveAccountIsRefused() {
        // Arrange
        UUID id = UUID.randomUUID();
        StaffAccountEntity account =
                new StaffAccountEntity(id, "sam@example.com", "Sam Taylor", StaffRole.STAFF, null, Instant.EPOCH);
        account.setActive(false, null, Instant.EPOCH);
        when(repository.findById(id)).thenReturn(Optional.of(account));

        // Act / Assert
        assertThatThrownBy(() -> converter.convert(jwtFor(id.toString())))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void unknownAccountIsRefused() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> converter.convert(jwtFor(id.toString())))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void subjectThatIsNotAUuidIsRefused() {
        // Act / Assert
        assertThatThrownBy(() -> converter.convert(jwtFor("service-account-something")))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void databaseOutageIsAnAuthenticationUnavailableFailureNotAnInactiveAccount() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenThrow(new DataAccessResourceFailureException("connection refused"));

        // Act / Assert
        assertThatThrownBy(() -> converter.convert(jwtFor(id.toString())))
                .isInstanceOf(AuthenticationUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    private static Jwt jwtFor(String subject) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
    }
}
