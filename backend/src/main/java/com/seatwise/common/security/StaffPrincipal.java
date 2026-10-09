package com.seatwise.common.security;

import java.io.Serializable;
import java.util.UUID;

/**
 * The signed-in staff member as loaded from {@code staff_account} for this
 * request, never from token claims, so a role change or deactivation applies
 * on the next request.
 */
public record StaffPrincipal(UUID id, String email, String fullName, StaffRole role) implements Serializable {}
