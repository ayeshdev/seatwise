/**
 * Attendee registrations and the waitlist. A leaf module: nothing depends on
 * it, it only publishes events.
 */
@ApplicationModule(displayName = "Registrations", allowedDependencies = {"workshops", "accounts", "common"})
package com.seatwise.registrations;

import org.springframework.modulith.ApplicationModule;
