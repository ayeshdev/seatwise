package com.seatwise.support;

import com.seatwise.common.security.StaffAuthenticationToken;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Staff rows (for the actor foreign keys) and a signed-in security context, without Keycloak. */
public final class TestStaff {

    private TestStaff() {}

    /** Test-only reset of the catalogue tables; the application itself never deletes anything. */
    public static void wipeCatalogue(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE registration, workshop, staff_account CASCADE");
    }

    public static StaffPrincipal insert(JdbcTemplate jdbc, String fullName, StaffRole role) {
        UUID id = UUID.randomUUID();
        String email = fullName.toLowerCase(Locale.ROOT).replace(' ', '.') + "." + id.toString().substring(0, 8)
                + "@example.com";
        jdbc.update("""
                INSERT INTO staff_account (id, email, full_name, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, now(), now(), 0)""", id, email, fullName, role.name());
        return new StaffPrincipal(id, email, fullName, role);
    }

    /** Sets the current thread's security context, as the JWT converter would for a request. */
    public static void signIn(StaffPrincipal staff) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(staff.id().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new StaffAuthenticationToken(jwt, staff));
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
