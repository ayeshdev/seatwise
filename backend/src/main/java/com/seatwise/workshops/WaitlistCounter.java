package com.seatwise.workshops;

import java.util.UUID;

/**
 * A port the workshop detail needs but the workshops module can't answer:
 * waitlisted rows live in the registrations module's table, and workshops must
 * not depend on registrations (registrations already depends on workshops).
 * The registrations module implements this interface, so the dependency still
 * points from registrations to workshops and no module reads another's table.
 */
public interface WaitlistCounter {

    /** How many people are currently waitlisted for the workshop. */
    long waitlistCount(UUID workshopId);
}
