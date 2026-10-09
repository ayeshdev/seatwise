/**
 * Workshops, locations and the seat inventory. Public API lives in this
 * package; everything else belongs in {@code workshops.internal}.
 */
@ApplicationModule(displayName = "Workshops", allowedDependencies = {"accounts", "common"})
package com.seatwise.workshops;

import org.springframework.modulith.ApplicationModule;
