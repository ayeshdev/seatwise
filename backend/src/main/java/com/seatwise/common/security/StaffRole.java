package com.seatwise.common.security;

/**
 * The three staff roles. Stored by name in {@code staff_account.role} (the
 * CHECK constraint lists the same values) and granted as {@code ROLE_<name>}.
 */
public enum StaffRole {
    ADMIN,
    MANAGER,
    STAFF;

    /** The Spring Security authority for this role, as matched by {@code hasRole(...)}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
