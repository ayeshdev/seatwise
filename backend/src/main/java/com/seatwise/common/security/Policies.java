package com.seatwise.common.security;

/**
 * The permission matrix (architecture section 8) as SpEL for
 * {@code @PreAuthorize}. Controllers reference these constants rather than
 * inline expressions, so the matrix reads the same in code as in the docs and
 * a policy change is one line here plus its {@code AccessMatrixTest} rows.
 *
 * <p>Admin is deliberately absent from the workshop policies: the brief says
 * Admins manage accounts, not the catalogue.
 */
public final class Policies {

    /** Create accounts, set roles, deactivate, reset passwords. */
    public static final String CAN_MANAGE_STAFF = "hasRole('ADMIN')";

    /** Add, edit and cancel workshops. */
    public static final String CAN_EDIT_WORKSHOPS = "hasRole('MANAGER')";

    /** Register and cancel attendees. */
    public static final String CAN_BOOK = "hasAnyRole('MANAGER','STAFF')";

    /** View workshops, locations, registrations and their history. */
    public static final String CAN_VIEW_CATALOGUE = "hasAnyRole('MANAGER','STAFF')";

    /** Audit events about staff accounts. */
    public static final String CAN_VIEW_ACCOUNT_AUDIT = "hasRole('ADMIN')";

    /** Audit events about workshops and registrations. */
    public static final String CAN_VIEW_WORKSHOP_AUDIT = "hasAnyRole('MANAGER','STAFF')";

    /** Any active staff member, whatever the role (e.g. own profile). */
    public static final String ANY_STAFF = "isAuthenticated()";

    private Policies() {}
}
