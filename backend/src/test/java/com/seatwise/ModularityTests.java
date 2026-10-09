package com.seatwise;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    // Fails the build on any cross-module access that bypasses a module's root-package API.
    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(SeatwiseApplication.class).verify();
    }
}
