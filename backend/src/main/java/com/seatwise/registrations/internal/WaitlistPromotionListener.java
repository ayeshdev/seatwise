package com.seatwise.registrations.internal;

import com.seatwise.workshops.FieldChange;
import com.seatwise.workshops.WorkshopUpdated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * When a Manager raises a workshop's capacity, the new seats go to the people
 * already waiting, in queue order, before anyone new can book them (FR-WL-02).
 * A plain {@code @EventListener} runs inside the edit's transaction, which
 * already holds the workshop's row lock, so this is all one atomic change.
 */
@Component
class WaitlistPromotionListener {

    private final RegistrationService registrations;

    WaitlistPromotionListener(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @EventListener
    void on(WorkshopUpdated event) {
        FieldChange capacity = event.changes().get("capacity");
        if (capacity != null
                && capacity.from() instanceof Integer from
                && capacity.to() instanceof Integer to
                && to > from) {
            registrations.fillFromWaitlist(event.workshopId(), event.actorId());
        }
    }
}
