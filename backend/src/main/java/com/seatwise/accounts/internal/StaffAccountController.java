package com.seatwise.accounts.internal;

import com.seatwise.common.security.Policies;
import com.seatwise.common.security.StaffRole;
import com.seatwise.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Admin-only account management (FR-AUTH-03, -05, -06, -07, -09). */
@RestController
@RequestMapping("/api/v1/staff-accounts")
public class StaffAccountController {

    private final StaffAccountService service;

    public StaffAccountController(StaffAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Policies.CAN_MANAGE_STAFF)
    public PageResponse<StaffAccountResponse> list(
            @RequestParam(name = "role", required = false) StaffRole role,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(role, active, page, size);
    }

    @PostMapping
    @PreAuthorize(Policies.CAN_MANAGE_STAFF)
    public ResponseEntity<StaffAccountResponse> create(@Valid @RequestBody CreateStaffAccountRequest request) {
        StaffAccountResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Policies.CAN_MANAGE_STAFF)
    public StaffAccountResponse get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Policies.CAN_MANAGE_STAFF)
    public StaffAccountResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody UpdateStaffAccountRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize(Policies.CAN_MANAGE_STAFF)
    public ResponseEntity<Void> resetPassword(
            @PathVariable("id") UUID id, @Valid @RequestBody PasswordResetRequest request) {
        service.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
