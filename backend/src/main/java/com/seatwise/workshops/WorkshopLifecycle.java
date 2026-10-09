package com.seatwise.workshops;

/**
 * The only stored part of a workshop's status ({@code workshop.lifecycle}).
 * Everything else staff see is derived, see {@link WorkshopStatus}.
 */
public enum WorkshopLifecycle {
    SCHEDULED,
    CANCELLED
}
