package com.seatwise.workshops;

import java.util.UUID;

/**
 * The only way {@code workshop.seats_taken} changes (architecture section 7).
 * Every method must run inside the caller's transaction (they are
 * {@code MANDATORY}), so a seat claim commits or rolls back together with the
 * registration row it belongs to.
 *
 * <p>Locking contract: each method takes the workshop's row lock, and it is
 * held until the caller commits. Registrations rely on this: every change to a
 * registration's status happens while holding its workshop's row lock, which
 * serializes cancellations, waitlist promotions and waitlist joins per workshop.
 */
public interface SeatInventory {

    /**
     * One atomic conditional {@code UPDATE}: takes a seat only if the workshop
     * is scheduled, hasn't started and has a seat left. Concurrent claims on the
     * same workshop queue on the row lock and re-check the condition against the
     * committed row, so the 21st claim on a 20-seat workshop gets {@code false}.
     */
    boolean tryClaimSeat(UUID workshopId);

    /** Gives one seat back ({@code seats_taken - 1}, never below zero). */
    void releaseSeat(UUID workshopId);

    /**
     * Locks the workshop row ({@code SELECT ... FOR NO KEY UPDATE}) and says whether it
     * is bookable and has a seat. Because the lock is held until commit, the
     * answer stays true for the rest of the caller's transaction: a FULL answer
     * can't be invalidated by a concurrent cancellation, and an OPEN answer
     * means the next {@link #tryClaimSeat} succeeds.
     */
    BookingAvailability availabilityOf(UUID workshopId);
}
