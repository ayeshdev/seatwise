package com.seatwise.accounts.internal;

import com.seatwise.common.security.StaffRole;
import java.util.UUID;

/** {@code GET /api/v1/me}: who is signed in, with the role the API will enforce. */
public record MeResponse(UUID id, String email, String fullName, StaffRole role) {}
