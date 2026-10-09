package com.seatwise.workshops;

import java.time.Instant;

/**
 * The status staff see (architecture section 6). It is derived from the
 * lifecycle, the clock and the seat count every time it is read, so it can
 * never go stale the way a stored "FULL" or "COMPLETED" flag would. The search
 * filters translate the same rules into SQL predicates.
 */
public enum WorkshopStatus {
    OPEN,
    FULL,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static WorkshopStatus derive(
            WorkshopLifecycle lifecycle, Instant startsAt, Instant endsAt, int capacity, int seatsTaken, Instant now) {
        if (lifecycle == WorkshopLifecycle.CANCELLED) {
            return CANCELLED;
        }
        if (!endsAt.isAfter(now)) {
            return COMPLETED;
        }
        if (!startsAt.isAfter(now)) {
            return IN_PROGRESS;
        }
        return seatsTaken >= capacity ? FULL : OPEN;
    }
}
