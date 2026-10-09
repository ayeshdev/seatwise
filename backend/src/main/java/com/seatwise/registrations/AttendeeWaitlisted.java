package com.seatwise.registrations;

import java.time.Instant;
import java.util.UUID;

/** The workshop was full and the attendee joined its waitlist; no seat was taken. */
public record AttendeeWaitlisted(
        UUID registrationId,
        UUID workshopId,
        String attendeeName,
        String attendeeEmail,
        UUID actorId,
        Instant occurredAt) {}
