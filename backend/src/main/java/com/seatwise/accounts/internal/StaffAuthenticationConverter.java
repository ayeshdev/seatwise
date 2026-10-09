package com.seatwise.accounts.internal;

import com.seatwise.common.security.AccountInactiveException;
import com.seatwise.common.security.StaffAuthenticationToken;
import com.seatwise.common.security.StaffPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a valid Keycloak token into a Seatwise authentication. Keycloak has
 * proved who the caller is; this decides what they may do by loading their
 * {@code staff_account} row on every request. That's one primary-key lookup,
 * and it is what makes a role change or deactivation apply immediately instead
 * of when the token expires.
 *
 * <p>Picked up by {@code SecurityConfig} as a plain {@link Converter} bean,
 * which keeps {@code common} free of any dependency on this module.
 */
@Component
public class StaffAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final StaffAccountRepository repository;

    public StaffAuthenticationConverter(StaffAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        StaffAccountEntity account = parseId(jwt.getSubject())
                .flatMap(repository::findById)
                .filter(StaffAccountEntity::isActive)
                // Same answer for "no row" and "deactivated": either way there is
                // no active account, and we don't reveal which.
                .orElseThrow(() -> new AccountInactiveException("No active staff account for this token"));
        StaffPrincipal principal = new StaffPrincipal(
                account.getId(), account.getEmail(), account.getFullName(), account.getRole());
        return new StaffAuthenticationToken(jwt, principal);
    }

    private static Optional<UUID> parseId(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
