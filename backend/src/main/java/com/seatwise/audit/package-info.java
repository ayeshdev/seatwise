/**
 * Append-only audit trail, fed by events published from the other modules.
 */
@ApplicationModule(
        displayName = "Audit",
        allowedDependencies = {"accounts", "workshops", "registrations", "common"})
package com.seatwise.audit;

import org.springframework.modulith.ApplicationModule;
