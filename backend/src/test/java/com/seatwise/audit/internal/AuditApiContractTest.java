package com.seatwise.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatwise.accounts.StaffRef;
import com.seatwise.accounts.internal.StaffAuthenticationConverter;
import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import com.seatwise.common.security.SecurityConfig;
import com.seatwise.common.security.StaffRole;
import com.seatwise.common.web.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The JSON contract of {@code GET /api/v1/audit-events} (architecture section
 * 9, {@code AuditEvent}), which the desk already codes against, plus request
 * binding and how the role rule's refusal reaches the client.
 */
@WebMvcTest(AuditEventController.class)
@Import(SecurityConfig.class)
class AuditApiContractTest {

    private static final UUID ID = UUID.fromString("6f1d8e2a-4b3c-4d5e-8f90-a1b2c3d4e5f6");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditQueryService service;

    @MockitoBean
    private StaffAuthenticationConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anEventHasTheAuditEventShape() throws Exception {
        // Arrange
        AuditEventResponse edit = new AuditEventResponse(42, Instant.parse("2026-10-17T09:30:00Z"),
                new StaffRef(ID, "Morgan Reyes"), AuditEntityType.WORKSHOP, ID, ID, AuditAction.UPDATED,
                "Changed capacity from 12 to 16", Map.of("capacity", new AuditChange(12, 16)));
        AuditEventResponse seeded = new AuditEventResponse(41, Instant.parse("2026-10-17T09:00:00Z"), null,
                AuditEntityType.WORKSHOP, ID, ID, AuditAction.CREATED, "Scheduled POT-0412 Pottery",
                Map.of("description", new AuditChange(null, "Clay provided")));
        AuditEventResponse reset = new AuditEventResponse(40, Instant.parse("2026-10-17T08:00:00Z"), null,
                AuditEntityType.STAFF_ACCOUNT, ID, null, AuditAction.PASSWORD_RESET,
                "Set a new temporary password", Map.of());
        when(service.list(any())).thenReturn(new PageResponse<>(List.of(edit, seeded, reset), 0, 20, 3));

        // Act / Assert
        mvc.perform(get("/api/v1/audit-events").with(as(StaffRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.items[0].occurredAt").value("2026-10-17T09:30:00Z"))
                .andExpect(jsonPath("$.items[0].actor.id").value(ID.toString()))
                .andExpect(jsonPath("$.items[0].actor.fullName").value("Morgan Reyes"))
                .andExpect(jsonPath("$.items[0].entityType").value("WORKSHOP"))
                .andExpect(jsonPath("$.items[0].entityId").value(ID.toString()))
                .andExpect(jsonPath("$.items[0].workshopId").value(ID.toString()))
                .andExpect(jsonPath("$.items[0].action").value("UPDATED"))
                .andExpect(jsonPath("$.items[0].summary").value("Changed capacity from 12 to 16"))
                .andExpect(jsonPath("$.items[0].changes.capacity.from").value(12))
                .andExpect(jsonPath("$.items[0].changes.capacity.to").value(16))
                .andExpect(jsonPath("$.items[1].actor").value(nullValue()))
                .andExpect(jsonPath("$.items[1].changes.description.from").value(nullValue()))
                .andExpect(jsonPath("$.items[1].changes.description.to").value("Clay provided"))
                .andExpect(jsonPath("$.items[2].workshopId").value(nullValue()))
                .andExpect(jsonPath("$.items[2].changes").isMap())
                .andExpect(jsonPath("$.items[2].changes").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void parametersBindToTheQuery() throws Exception {
        // Arrange
        when(service.list(any())).thenReturn(new PageResponse<>(List.of(), 2, 50, 0));
        ArgumentCaptor<AuditQuery> query = ArgumentCaptor.forClass(AuditQuery.class);

        // Act
        mvc.perform(get("/api/v1/audit-events").with(as(StaffRole.STAFF))
                        .param("entityType", "REGISTRATION").param("entityId", ID.toString())
                        .param("workshopId", ID.toString())
                        .param("from", "2026-10-12").param("to", "2026-10-18")
                        .param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        // Assert
        verify(service).list(query.capture());
        assertThat(query.getValue()).isEqualTo(new AuditQuery(AuditEntityType.REGISTRATION, ID, ID,
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), 2, 50));
    }

    @Test
    void defaultsAreTheFirstPageOfTwentyWithNoFilters() throws Exception {
        // Arrange
        when(service.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
        ArgumentCaptor<AuditQuery> query = ArgumentCaptor.forClass(AuditQuery.class);

        // Act
        mvc.perform(get("/api/v1/audit-events").with(as(StaffRole.ADMIN))).andExpect(status().isOk());

        // Assert
        verify(service).list(query.capture());
        assertThat(query.getValue()).isEqualTo(new AuditQuery(null, null, null, null, null, 0, 20));
    }

    @Test
    void aPageSizeOverOneHundredIsRefused() throws Exception {
        // Act / Assert
        mvc.perform(get("/api/v1/audit-events").with(as(StaffRole.MANAGER)).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void anUnknownEntityTypeIsRefused() throws Exception {
        // Act / Assert
        mvc.perform(get("/api/v1/audit-events").with(as(StaffRole.MANAGER)).param("entityType", "LOCATION"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void theRoleRuleRefusalIsAForbiddenProblem() throws Exception {
        // Arrange: the service decides, e.g. an Admin asking for workshop events.
        when(service.list(any())).thenThrow(new DomainException(
                ErrorCode.FORBIDDEN, "Admins can see account activity only, not workshops or bookings."));

        // Act / Assert
        mvc.perform(get("/api/v1/audit-events").with(as(StaffRole.ADMIN)).param("entityType", "WORKSHOP"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value(
                        "Admins can see account activity only, not workshops or bookings."));
    }

    @Test
    void anonymousCallersAreRefused() throws Exception {
        // Act / Assert
        mvc.perform(get("/api/v1/audit-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    private static RequestPostProcessor as(StaffRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
