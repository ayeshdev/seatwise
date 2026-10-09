package com.seatwise.workshops.internal;

import com.seatwise.workshops.BookingAvailability;
import com.seatwise.workshops.SeatInventory;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic seat statements (architecture section 7). "Now" comes from the
 * {@link Clock} bean rather than SQL {@code now()}, so the claim, the derived
 * status and the tests all agree on one clock.
 */
@Component
class JpaSeatInventory implements SeatInventory {

    private static final Logger log = LoggerFactory.getLogger(JpaSeatInventory.class);

    private final WorkshopRepository repository;
    private final Clock clock;

    JpaSeatInventory(WorkshopRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryClaimSeat(UUID workshopId) {
        return repository.claimSeat(workshopId, clock.instant()) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSeat(UUID workshopId) {
        if (repository.releaseSeat(workshopId) == 0) {
            // Only possible if the count and the ACTIVE rows disagree already.
            log.error("Seat release on workshop {} found no seat to release", workshopId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BookingAvailability availabilityOf(UUID workshopId) {
        return repository.lockAndClassify(workshopId, clock.instant())
                .map(BookingAvailability::valueOf)
                .orElse(BookingAvailability.NOT_FOUND);
    }
}
