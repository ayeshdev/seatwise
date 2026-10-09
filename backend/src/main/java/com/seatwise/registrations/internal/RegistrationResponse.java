package com.seatwise.registrations.internal;

import com.seatwise.accounts.StaffRef;
import com.seatwise.accounts.StaffSummary;
import com.seatwise.registrations.RegistrationStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The API's {@code Registration} shape (architecture section 9, "Payload shapes"). */
public record RegistrationResponse(
        UUID id,
        UUID workshopId,
        String attendeeName,
        String attendeeEmail,
        RegistrationStatus status,
        Instant registeredAt,
        StaffRef registeredBy,
        Instant promotedAt,
        Integer waitlistPosition,
        Instant cancelledAt,
        StaffRef cancelledBy,
        String cancellationReason) {

    /** {@code waitlistPosition} is 1-based and must be null unless the row is WAITLISTED. */
    static RegistrationResponse from(RegistrationEntity r, Integer waitlistPosition, Map<UUID, StaffSummary> staff) {
        return new RegistrationResponse(
                r.getId(),
                r.getWorkshopId(),
                r.getAttendeeName(),
                r.getAttendeeEmail(),
                r.getStatus(),
                r.getRegisteredAt(),
                StaffRef.resolve(r.getRegisteredBy(), staff),
                r.getPromotedAt(),
                r.getStatus() == RegistrationStatus.WAITLISTED ? waitlistPosition : null,
                r.getCancelledAt(),
                StaffRef.resolve(r.getCancelledBy(), staff),
                r.getCancellationReason());
    }
}
