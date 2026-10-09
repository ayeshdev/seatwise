package com.seatwise.registrations.internal;

import com.seatwise.registrations.RegistrationStatus;
import com.seatwise.workshops.WaitlistCounter;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Answers the workshops module's {@link WaitlistCounter} port from this module's own table. */
@Component
class RegistrationWaitlistCounter implements WaitlistCounter {

    private final RegistrationRepository repository;

    RegistrationWaitlistCounter(RegistrationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public long waitlistCount(UUID workshopId) {
        return repository.countByWorkshopIdAndStatus(workshopId, RegistrationStatus.WAITLISTED);
    }
}
