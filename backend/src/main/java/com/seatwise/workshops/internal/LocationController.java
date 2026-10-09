package com.seatwise.workshops.internal;

import com.seatwise.common.security.Policies;
import com.seatwise.workshops.LocationView;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The centre's active locations, for the workshop form and the location filter. */
@RestController
public class LocationController {

    private final WorkshopService service;

    public LocationController(WorkshopService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/locations")
    @PreAuthorize(Policies.CAN_VIEW_CATALOGUE)
    public List<LocationView> list() {
        return service.activeLocations();
    }
}
