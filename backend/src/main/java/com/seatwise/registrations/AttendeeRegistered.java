package com.seatwise.registrations;

import java.time.Instant;
import java.util.UUID;

/** An attendee took a seat (status ACTIVE) on a workshop. */
public record AttendeeRegistered(
        UUID registrationId,
        UUID workshopId,
        String attendeeName,
        String attendeeEmail,
        UUID actorId,
        Instant occurredAt) {}
