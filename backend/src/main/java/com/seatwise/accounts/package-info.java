/**
 * Staff accounts and roles. Public API lives in this package; everything else
 * belongs in {@code accounts.internal}.
 */
@ApplicationModule(displayName = "Accounts", allowedDependencies = "common")
package com.seatwise.accounts;

import org.springframework.modulith.ApplicationModule;
