package com.seatwise.common.security;

import com.seatwise.common.config.SeatwiseProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * @param staffAuthenticationConverter maps the token's {@code sub} to the
     *     staff account and its role. It is supplied by the accounts module as
     *     a plain {@link Converter} bean, so {@code common} never imports a
     *     domain module.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> staffAuthenticationConverter,
            JsonMapper jsonMapper)
            throws Exception {
        ProblemSecurityHandler problems = new ProblemSecurityHandler(jsonMapper);
        http
                // Bearer tokens only, no cookies or sessions, so CSRF has nothing to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/api/v1/openapi.json",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        // Coarse URL rules in front of the method-level @PreAuthorize: a
                        // caller without the role is refused before request bodies are
                        // even validated, so a 400 never reveals an endpoint's shape.
                        .requestMatchers("/api/v1/staff-accounts", "/api/v1/staff-accounts/**")
                        .hasRole(StaffRole.ADMIN.name())
                        // Catalogue: Manager and Staff view; Admin is deliberately absent (A-1).
                        .requestMatchers(HttpMethod.GET, "/api/v1/workshops", "/api/v1/workshops/**", "/api/v1/locations")
                        .hasAnyRole(StaffRole.MANAGER.name(), StaffRole.STAFF.name())
                        // Booking comes before the Manager-only workshop rules so that
                        // POST /workshops/{id}/registrations is never caught by them.
                        .requestMatchers(HttpMethod.POST, "/api/v1/workshops/*/registrations", "/api/v1/registrations/*/cancel")
                        .hasAnyRole(StaffRole.MANAGER.name(), StaffRole.STAFF.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/workshops", "/api/v1/workshops/*/cancel")
                        .hasRole(StaffRole.MANAGER.name())
                        .requestMatchers(HttpMethod.PUT, "/api/v1/workshops/*")
                        .hasRole(StaffRole.MANAGER.name())
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(oauth -> oauth
                        // Token failures (and AccountInactiveException from the converter)
                        // are reported by this entry point, not the exceptionHandling one.
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(staffAuthenticationConverter)));
        return http.build();
    }

    // NimbusJwtDecoder fetches the JWKS on the first token, not at startup, so
    // the API boots (and passes readiness) even while Keycloak is still starting.
    @Bean
    public JwtDecoder jwtDecoder(SeatwiseProperties props) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(props.oidc().jwksUri()).build();
        // Issuer, audience and timestamps; built in JwtValidation so the exact chain is unit-tested.
        decoder.setJwtValidator(JwtValidation.validator(props.oidc().issuer(), props.oidc().audience()));
        return decoder;
    }
}
