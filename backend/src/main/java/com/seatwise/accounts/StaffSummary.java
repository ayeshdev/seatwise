package com.seatwise.accounts;

import com.seatwise.common.security.StaffRole;
import java.util.UUID;

/**
 * Read-only view of a staff member for other modules, e.g. to show who
 * registered or cancelled a booking. Inactive accounts are still returned:
 * history must keep naming people after they leave.
 */
public record StaffSummary(UUID id, String fullName, String email, StaffRole role, boolean active) {}
