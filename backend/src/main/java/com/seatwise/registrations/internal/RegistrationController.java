package com.seatwise.registrations.internal;

import com.seatwise.common.security.Policies;
import com.seatwise.registrations.RegistrationStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Booking and cancelling attendees, and a workshop's registration history (FR-REG). */
@RestController
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/workshops/{id}/registrations")
    @PreAuthorize(Policies.CAN_VIEW_CATALOGUE)
    public List<RegistrationResponse> history(
            @PathVariable("id") UUID workshopId,
            @RequestParam(name = "status", required = false) RegistrationStatus status) {
        return service.history(workshopId, status);
    }

    @PostMapping("/api/v1/workshops/{id}/registrations")
    @PreAuthorize(Policies.CAN_BOOK)
    public ResponseEntity<RegistrationResponse> register(
            @PathVariable("id") UUID workshopId, @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(workshopId, request));
    }

    @PostMapping("/api/v1/registrations/{id}/cancel")
    @PreAuthorize(Policies.CAN_BOOK)
    public CancelResult cancel(
            @PathVariable("id") UUID registrationId,
            @Valid @RequestBody(required = false) CancelRegistrationRequest request) {
        return service.cancel(registrationId, request);
    }
}
