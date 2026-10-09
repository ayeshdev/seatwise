package com.seatwise;

import static com.seatwise.common.security.StaffRole.ADMIN;
import static com.seatwise.common.security.StaffRole.MANAGER;
import static com.seatwise.common.security.StaffRole.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.seatwise.accounts.internal.StaffAccountResponse;
import com.seatwise.accounts.internal.StaffAccountService;
import com.seatwise.accounts.StaffRef;
import com.seatwise.accounts.internal.StaffAuthenticationConverter;
import com.seatwise.audit.internal.AuditQueryService;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.common.security.SecurityConfig;
import com.seatwise.common.security.StaffPrincipal;
import com.seatwise.common.security.StaffRole;
import com.seatwise.common.web.PageResponse;
import com.seatwise.registrations.RegistrationStatus;
import com.seatwise.registrations.internal.CancelResult;
import com.seatwise.registrations.internal.RegistrationResponse;
import com.seatwise.registrations.internal.RegistrationService;
import com.seatwise.search.internal.WorkshopSearch;
import com.seatwise.search.internal.WorkshopSearchResult;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.internal.WorkshopResponse;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * FR-ACL-04: every endpoint x every role (plus anonymous) against the
 * permission matrix of architecture section 8, through the real
 * {@link SecurityConfig} and the real {@code @PreAuthorize} policies.
 *
 * <p>To add an endpoint: add one {@link #MATRIX} row and, if its controller
 * needs a new service, one {@code @MockitoBean} field. The completeness test
 * fails the build until both the policy annotation and the row exist.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class AccessMatrixTest {

    /** Path templates are written exactly as mapped; every {placeholder} is filled with this id. */
    private static final UUID SAMPLE_ID = UUID.fromString("0b9a3c1e-5d2f-4e8a-9c71-2f6d8e4b1a30");

    /** Endpoints that are deliberately reachable without signing in. */
    private static final Set<String> PUBLIC_API_PATHS = Set.of("/api/v1/openapi.json");

    private static final Set<StaffRole> EVERYONE = EnumSet.allOf(StaffRole.class);

    /** View the catalogue and book attendees. Admin is deliberately absent (SRS A-1). */
    private static final Set<StaffRole> DESK = Set.of(MANAGER, STAFF);

    private static final String WORKSHOP_BODY = """
            {"code":"POT-0412","title":"Pottery wheel basics","instructor":"Amara Silva",
             "locationId":"3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b01",
             "startsAt":"2030-01-07T10:00:00Z","endsAt":"2030-01-07T12:00:00Z","capacity":12""";

    private static final List<Row> MATRIX = List.of(
            // ---- accounts ----
            row(HttpMethod.GET, "/api/v1/me", null, EVERYONE),
            row(HttpMethod.GET, "/api/v1/staff-accounts", null, Set.of(ADMIN)),
            row(HttpMethod.POST, "/api/v1/staff-accounts", """
                    {"email":"new.person@example.com","fullName":"New Person",
                     "role":"STAFF","temporaryPassword":"Temporary#Pass1"}""", Set.of(ADMIN)),
            row(HttpMethod.GET, "/api/v1/staff-accounts/{id}", null, Set.of(ADMIN)),
            row(HttpMethod.PATCH, "/api/v1/staff-accounts/{id}", """
                    {"fullName":"Renamed Person","version":0}""", Set.of(ADMIN)),
            row(HttpMethod.POST, "/api/v1/staff-accounts/{id}/password-reset", """
                    {"temporaryPassword":"Temporary#Pass1"}""", Set.of(ADMIN)),
            // ---- workshops ----
            row(HttpMethod.GET, "/api/v1/locations", null, DESK),
            row(HttpMethod.GET, "/api/v1/workshops", null, DESK),
            row(HttpMethod.GET, "/api/v1/workshops/{id}", null, DESK),
            row(HttpMethod.POST, "/api/v1/workshops", WORKSHOP_BODY + "}", Set.of(MANAGER)),
            row(HttpMethod.PUT, "/api/v1/workshops/{id}", WORKSHOP_BODY + ",\"version\":0}", Set.of(MANAGER)),
            row(HttpMethod.POST, "/api/v1/workshops/{id}/cancel", """
                    {"reason":"Instructor unwell"}""", Set.of(MANAGER)),
            // ---- registrations ----
            row(HttpMethod.GET, "/api/v1/workshops/{id}/registrations", null, DESK),
            row(HttpMethod.POST, "/api/v1/workshops/{id}/registrations", """
                    {"attendeeName":"Dana Lee","attendeeEmail":"dana@example.com"}""", DESK),
            row(HttpMethod.POST, "/api/v1/registrations/{id}/cancel", """
                    {"reason":null}""", DESK),
            // ---- audit: every role may call it; which entity types each sees is
            // decided by the data, see AuditQueryServiceIT ----
            row(HttpMethod.GET, "/api/v1/audit-events", null, EVERYONE)
    );

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private StaffAccountService staffAccountService;

    @MockitoBean
    private WorkshopService workshopService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private WorkshopSearch workshopSearch;

    @MockitoBean
    private AuditQueryService auditQueryService;

    @MockitoBean
    private ActorProvider actorProvider;

    // The real converter needs the database; jwt() below bypasses it anyway.
    @MockitoBean
    private StaffAuthenticationConverter staffAuthenticationConverter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void stubServicesSoAllowedCallsSucceed() {
        StaffAccountResponse account = new StaffAccountResponse(
                SAMPLE_ID, "person@example.com", "Some Person", STAFF, true, Instant.EPOCH, Instant.EPOCH, 0);
        when(staffAccountService.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(account), 0, 20, 1));
        when(staffAccountService.get(any())).thenReturn(account);
        when(staffAccountService.create(any())).thenReturn(account);
        when(staffAccountService.update(any(), any())).thenReturn(account);
        when(actorProvider.current())
                .thenReturn(new StaffPrincipal(SAMPLE_ID, "person@example.com", "Some Person", STAFF));

        StaffRef someone = new StaffRef(SAMPLE_ID, "Some Person");
        LocationView northside = new LocationView(SAMPLE_ID, "Northside Studio");
        WorkshopResponse workshop = new WorkshopResponse(SAMPLE_ID, "POT-0412", "Pottery wheel basics", null,
                "Amara Silva", northside, Instant.EPOCH, Instant.EPOCH.plusSeconds(7200), 12, 0, 12, 0,
                WorkshopStatus.OPEN, 0, Instant.EPOCH, someone, Instant.EPOCH, someone);
        when(workshopService.get(any())).thenReturn(workshop);
        when(workshopService.create(any())).thenReturn(workshop);
        when(workshopService.update(any(), any())).thenReturn(workshop);
        when(workshopService.cancel(any(), any())).thenReturn(workshop);
        when(workshopService.activeLocations()).thenReturn(List.of(northside));
        when(workshopSearch.search(any())).thenReturn(new WorkshopSearchResult(List.of(), 0, 20, 0, "fallback"));
        RegistrationResponse registration = new RegistrationResponse(SAMPLE_ID, SAMPLE_ID, "Dana Lee",
                "dana@example.com", RegistrationStatus.ACTIVE, Instant.EPOCH, someone, null, null, null, null, null);
        when(registrationService.history(any(), any())).thenReturn(List.of(registration));
        when(registrationService.register(any(), any())).thenReturn(registration);
        when(registrationService.cancel(any(), any())).thenReturn(new CancelResult(registration, null));
        when(auditQueryService.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
    }

    static Stream<Arguments> matrix() {
        return MATRIX.stream().flatMap(row -> Arrays.stream(Caller.values()).map(caller -> Arguments.of(row, caller)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("matrix")
    void enforcesThePermissionMatrix(Row row, Caller caller) throws Exception {
        // Arrange
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(row.method(), row.concretePath());
        if (row.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(row.body());
        }
        if (caller.role != null) {
            request.with(jwt().authorities(new SimpleGrantedAuthority(caller.role.authority())));
        }

        // Act
        int status = mvc.perform(request).andReturn().getResponse().getStatus();

        // Assert
        if (caller.role == null) {
            assertThat(status).as("anonymous must be 401").isEqualTo(401);
        } else if (row.allowed().contains(caller.role)) {
            // 2xx rather than just "not 401/403": a typo in a row's body or path
            // would otherwise pass as an allowed 400 or 404.
            assertThat(status).as("%s is allowed", caller).isBetween(200, 299);
        } else {
            assertThat(status).as("%s is not allowed", caller).isEqualTo(403);
        }
        if (caller.role == null || !row.allowed().contains(caller.role)) {
            // A refused request must never reach a service.
            verifyNoInteractions(
                    staffAccountService, workshopService, registrationService, workshopSearch, auditQueryService);
        }
    }

    /**
     * The guard against "open by default": every mapped /api/ endpoint must
     * carry a method-level {@code @PreAuthorize} and have a matrix row, and
     * every row must point at a real endpoint (a typo would otherwise pass as
     * an "allowed" 404).
     */
    @Test
    void everyApiEndpointHasAPolicyAndAMatrixRow() {
        // Arrange
        Set<String> rows = MATRIX.stream().map(Row::key).collect(Collectors.toCollection(TreeSet::new));
        Set<String> endpoints = new TreeSet<>();
        List<String> problems = new ArrayList<>();

        // Act
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/") || PUBLIC_API_PATHS.contains(pattern)) {
                    continue;
                }
                String where = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
                if (!AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)) {
                    problems.add(where + " (" + pattern + ") has no @PreAuthorize");
                }
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    problems.add(where + " (" + pattern + ") accepts every HTTP method; declare one");
                }
                for (RequestMethod method : methods) {
                    String key = method.name() + " " + pattern;
                    endpoints.add(key);
                    if (!rows.contains(key)) {
                        problems.add(where + ": no AccessMatrixTest row for " + key);
                    }
                }
            }
        });
        rows.stream()
                .filter(key -> !endpoints.contains(key))
                .forEach(key -> problems.add("AccessMatrixTest row for unmapped endpoint " + key));

        // Assert
        assertThat(endpoints).as("the scan found the API controllers").isNotEmpty();
        assertThat(problems).isEmpty();
    }

    enum Caller {
        ADMIN(StaffRole.ADMIN),
        MANAGER(StaffRole.MANAGER),
        STAFF(StaffRole.STAFF),
        ANONYMOUS(null);

        private final StaffRole role;

        Caller(StaffRole role) {
            this.role = role;
        }
    }

    record Row(HttpMethod method, String path, String body, Set<StaffRole> allowed) {

        String key() {
            return method.name() + " " + path;
        }

        String concretePath() {
            return path.replaceAll("\\{[^}]+}", SAMPLE_ID.toString());
        }

        @Override
        public String toString() {
            return key();
        }
    }

    private static Row row(HttpMethod method, String path, String body, Set<StaffRole> allowed) {
        return new Row(method, path, body, allowed);
    }
}
