package com.seatwise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatwise.accounts.StaffRef;
import com.seatwise.accounts.internal.StaffAccountService;
import com.seatwise.accounts.internal.StaffAuthenticationConverter;
import com.seatwise.common.security.ActorProvider;
import com.seatwise.common.security.SecurityConfig;
import com.seatwise.registrations.RegistrationStatus;
import com.seatwise.registrations.internal.CancelResult;
import com.seatwise.registrations.internal.RegistrationResponse;
import com.seatwise.registrations.internal.RegistrationService;
import com.seatwise.search.internal.WorkshopSearch;
import com.seatwise.search.internal.WorkshopSearchCriteria;
import com.seatwise.search.internal.WorkshopSearchResult;
import com.seatwise.search.internal.WorkshopSummaryResponse;
import com.seatwise.workshops.LocationView;
import com.seatwise.workshops.WorkshopQuery.WorkshopSort;
import com.seatwise.workshops.WorkshopStatus;
import com.seatwise.workshops.internal.WorkshopResponse;
import com.seatwise.workshops.internal.WorkshopService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The JSON contract of the catalogue endpoints (architecture section 9,
 * "Payload shapes"), which the desk already codes against, plus request
 * binding and the validation codes the web layer produces itself.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class CatalogueApiContractTest {

    private static final UUID ID = UUID.fromString("6f1d8e2a-4b3c-4d5e-8f90-a1b2c3d4e5f6");
    private static final Instant STARTS = Instant.parse("2026-10-17T09:30:00Z");
    private static final StaffRef MORGAN = new StaffRef(ID, "Morgan Reyes");
    private static final LocationView NORTHSIDE = new LocationView(ID, "Northside Studio");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkshopService workshopService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private WorkshopSearch workshopSearch;

    @MockitoBean
    private StaffAccountService staffAccountService;

    @MockitoBean
    private ActorProvider actorProvider;

    @MockitoBean
    private StaffAuthenticationConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void workshopDetailHasTheWorkshopShape() throws Exception {
        // Arrange
        when(workshopService.get(ID)).thenReturn(workshop());

        // Act / Assert
        mvc.perform(get("/api/v1/workshops/{id}", ID).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.code").value("POT-0412"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.location.id").value(ID.toString()))
                .andExpect(jsonPath("$.location.name").value("Northside Studio"))
                .andExpect(jsonPath("$.startsAt").value("2026-10-17T09:30:00Z"))
                .andExpect(jsonPath("$.capacity").value(12))
                .andExpect(jsonPath("$.seatsTaken").value(11))
                .andExpect(jsonPath("$.seatsLeft").value(1))
                .andExpect(jsonPath("$.waitlistCount").value(2))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.createdBy.fullName").value("Morgan Reyes"))
                .andExpect(jsonPath("$.updatedBy.id").value(ID.toString()));
    }

    @Test
    void scheduledWorkshopIsCreatedWithItsLocation() throws Exception {
        // Arrange
        when(workshopService.create(any())).thenReturn(workshop());

        // Act / Assert
        mvc.perform(post("/api/v1/workshops").with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"POT-0412","title":"Pottery","instructor":"Amara Silva",
                                 "locationId":"%s","startsAt":"2026-10-17T09:30:00Z",
                                 "endsAt":"2026-10-17T11:30:00Z","capacity":12}""".formatted(ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/workshops/" + ID)));
    }

    @Test
    void workshopBodyOutsideTheRulesListsTheFields() throws Exception {
        // Act / Assert
        mvc.perform(post("/api/v1/workshops").with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","title":"Pottery","instructor":"Amara Silva","locationId":"%s",
                                 "startsAt":"2026-10-17T09:30:00Z","endsAt":"2026-10-17T11:30:00Z",
                                 "capacity":501}""".formatted(ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'code')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'capacity')]").exists());
        verifyNoInteractions(workshopService);
    }

    @Test
    void searchResultIsAPageOfSummariesWithTheSearchMode() throws Exception {
        // Arrange
        WorkshopSummaryResponse summary = new WorkshopSummaryResponse(ID, "POT-0412", "Pottery", "Amara Silva",
                NORTHSIDE, STARTS, STARTS.plusSeconds(7200), 12, 12, 0, WorkshopStatus.FULL);
        when(workshopSearch.search(any())).thenReturn(new WorkshopSearchResult(List.of(summary), 0, 20, 1, "fallback"));

        // Act / Assert
        mvc.perform(get("/api/v1/workshops").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("POT-0412"))
                .andExpect(jsonPath("$.items[0].location.name").value("Northside Studio"))
                .andExpect(jsonPath("$.items[0].seatsLeft").value(0))
                .andExpect(jsonPath("$.items[0].status").value("FULL"))
                .andExpect(jsonPath("$.items[0].waitlistCount").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.searchMode").value("fallback"));
    }

    @Test
    void searchParametersBindToTheCriteria() throws Exception {
        // Arrange
        when(workshopSearch.search(any())).thenReturn(new WorkshopSearchResult(List.of(), 1, 5, 0, "fallback"));
        ArgumentCaptor<WorkshopSearchCriteria> criteria = ArgumentCaptor.forClass(WorkshopSearchCriteria.class);

        // Act
        mvc.perform(get("/api/v1/workshops").with(staff())
                        .param("from", "2026-10-12").param("to", "2026-10-18")
                        .param("status", "OPEN").param("status", "FULL")
                        .param("locationId", ID.toString()).param("hasSeats", "true").param("q", " pot ")
                        .param("page", "1").param("size", "5").param("sort", "title,desc"))
                .andExpect(status().isOk());

        // Assert
        verify(workshopSearch).search(criteria.capture());
        WorkshopSearchCriteria c = criteria.getValue();
        assertThat(c.from()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(c.to()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(c.statuses()).containsExactlyInAnyOrder(WorkshopStatus.OPEN, WorkshopStatus.FULL);
        assertThat(c.locationId()).isEqualTo(ID);
        assertThat(c.hasSeats()).isTrue();
        assertThat(c.q()).isEqualTo("pot");
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.size()).isEqualTo(5);
        assertThat(c.sort()).isEqualTo(WorkshopSort.TITLE_DESC);
    }

    @Test
    void searchDefaultsToTheFirstPageSoonestFirst() throws Exception {
        // Arrange
        when(workshopSearch.search(any())).thenReturn(new WorkshopSearchResult(List.of(), 0, 20, 0, "fallback"));
        ArgumentCaptor<WorkshopSearchCriteria> criteria = ArgumentCaptor.forClass(WorkshopSearchCriteria.class);

        // Act
        mvc.perform(get("/api/v1/workshops").with(staff())).andExpect(status().isOk());

        // Assert
        verify(workshopSearch).search(criteria.capture());
        assertThat(criteria.getValue()).isEqualTo(new WorkshopSearchCriteria(
                null, null, Set.of(), null, false, null, 0, 20, WorkshopSort.STARTS_AT_ASC));
    }

    @Test
    void badSearchParametersAreValidationFailures() throws Exception {
        mvc.perform(get("/api/v1/workshops").with(staff()).param("status", "SOLD_OUT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/workshops").with(staff()).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(get("/api/v1/workshops").with(staff()).param("sort", "seatsLeft"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        mvc.perform(get("/api/v1/workshops").with(staff()).param("from", "2026-10-18").param("to", "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        verifyNoInteractions(workshopSearch);
    }

    @Test
    void registerAnswersCreatedWithTheRegistrationShape() throws Exception {
        // Arrange
        when(registrationService.register(eq(ID), any())).thenReturn(registration(RegistrationStatus.WAITLISTED, 2));

        // Act / Assert
        mvc.perform(post("/api/v1/workshops/{id}/registrations", ID).with(staff())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendeeName":"Dana Lee","attendeeEmail":" dana@example.com ","joinWaitlistIfFull":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workshopId").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.registeredAt").value("2026-10-17T09:30:00Z"))
                .andExpect(jsonPath("$.registeredBy.fullName").value("Morgan Reyes"))
                .andExpect(jsonPath("$.promotedAt").value(nullValue()))
                .andExpect(jsonPath("$.waitlistPosition").value(2))
                .andExpect(jsonPath("$.cancelledAt").value(nullValue()))
                .andExpect(jsonPath("$.cancelledBy").value(nullValue()))
                .andExpect(jsonPath("$.cancellationReason").value(nullValue()));
    }

    @Test
    void registerWithABadEmailIsAValidationFailure() throws Exception {
        mvc.perform(post("/api/v1/workshops/{id}/registrations", ID).with(staff())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendeeName":"Dana Lee","attendeeEmail":"not-an-email"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attendeeEmail"));
        verifyNoInteractions(registrationService);
    }

    @Test
    void cancelNeedsNoBodyAndReturnsTheCancelledAndPromotedRows() throws Exception {
        // Arrange
        when(registrationService.cancel(eq(ID), isNull())).thenReturn(new CancelResult(
                registration(RegistrationStatus.CANCELLED, null), registration(RegistrationStatus.ACTIVE, null)));

        // Act / Assert
        mvc.perform(post("/api/v1/registrations/{id}/cancel", ID).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted.status").value("ACTIVE"));
    }

    @Test
    void locationsAreAPlainArray() throws Exception {
        // Arrange
        when(workshopService.activeLocations()).thenReturn(List.of(NORTHSIDE));

        // Act / Assert
        mvc.perform(get("/api/v1/locations").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Northside Studio"));
    }

    // ---- helpers ----

    private static WorkshopResponse workshop() {
        return new WorkshopResponse(ID, "POT-0412", "Pottery wheel basics", null, "Amara Silva", NORTHSIDE,
                STARTS, STARTS.plusSeconds(7200), 12, 11, 1, 2, WorkshopStatus.OPEN, 3,
                STARTS.minusSeconds(86_400), MORGAN, STARTS.minusSeconds(3600), MORGAN);
    }

    private static RegistrationResponse registration(RegistrationStatus status, Integer position) {
        return new RegistrationResponse(ID, ID, "Dana Lee", "dana@example.com", status, STARTS, MORGAN,
                null, position, null, null, null);
    }

    private static RequestPostProcessor staff() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"));
    }

    private static RequestPostProcessor manager() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }
}
