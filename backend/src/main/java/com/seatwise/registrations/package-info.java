/**
 * Attendee registrations and the waitlist. A leaf module: nothing depends on
 * it, it only publishes events. It also implements the workshops module's
 * {@code WaitlistCounter} port, so that dependency points this way too.
 */
@ApplicationModule(displayName = "Registrations", allowedDependencies = {"workshops", "accounts", "common"})
package com.seatwise.registrations;

import org.springframework.modulith.ApplicationModule;
