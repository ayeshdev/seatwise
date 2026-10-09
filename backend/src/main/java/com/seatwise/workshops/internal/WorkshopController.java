package com.seatwise.workshops.internal;

import com.seatwise.common.security.Policies;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Workshop detail and Manager-only editing (FR-WS). The list/search endpoint
 * {@code GET /api/v1/workshops} belongs to the search module.
 */
@RestController
@RequestMapping("/api/v1/workshops")
public class WorkshopController {

    private final WorkshopService service;

    public WorkshopController(WorkshopService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize(Policies.CAN_VIEW_CATALOGUE)
    public WorkshopResponse get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(Policies.CAN_EDIT_WORKSHOPS)
    public ResponseEntity<WorkshopResponse> create(@Valid @RequestBody WorkshopRequest request) {
        WorkshopResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Policies.CAN_EDIT_WORKSHOPS)
    public WorkshopResponse update(@PathVariable("id") UUID id, @Valid @RequestBody WorkshopRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(Policies.CAN_EDIT_WORKSHOPS)
    public WorkshopResponse cancel(
            @PathVariable("id") UUID id, @Valid @RequestBody(required = false) CancelWorkshopRequest request) {
        return service.cancel(id, request);
    }
}
