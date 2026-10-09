package com.seatwise.workshops;

/** Why a workshop can (or can't) take a booking right now; see {@link SeatInventory#availabilityOf}. */
public enum BookingAvailability {
    /** No workshop with that id. */
    NOT_FOUND,
    /** Cancelled, started or finished: bookings are closed for good. */
    NOT_OPEN,
    /** Bookable, but every seat is taken (the waitlist may still accept people). */
    FULL,
    /** Bookable with at least one seat left. */
    OPEN;

    /** Scheduled and not started, whether or not a seat is left. */
    public boolean isBookable() {
        return this == FULL || this == OPEN;
    }
}
