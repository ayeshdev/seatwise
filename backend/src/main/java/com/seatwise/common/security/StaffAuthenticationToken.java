package com.seatwise.common.security;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * A JWT authentication whose principal is the {@link StaffPrincipal} loaded
 * from the database. It carries exactly one authority, {@code ROLE_<role>};
 * no scopes or token claims are turned into authorities, because Keycloak only
 * authenticates and the database alone authorizes.
 *
 * <p>Lives in {@code common} so {@link ActorProvider} can read it without
 * {@code common} depending on the accounts module that creates it.
 */
public class StaffAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

    private static final long serialVersionUID = 1L;

    private final StaffPrincipal staff;

    public StaffAuthenticationToken(Jwt jwt, StaffPrincipal staff) {
        super(jwt, staff, jwt, List.of(new SimpleGrantedAuthority(staff.role().authority())));
        this.staff = staff;
        setAuthenticated(true);
    }

    public StaffPrincipal staff() {
        return staff;
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }

    @Override
    public String getName() {
        return staff.id().toString();
    }
}
