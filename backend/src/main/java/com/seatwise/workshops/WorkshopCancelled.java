package com.seatwise.workshops;

import java.time.Instant;
import java.util.UUID;

/** A Manager cancelled a workshop: no new bookings; existing registrations are kept as they are (A-6). */
public record WorkshopCancelled(UUID workshopId, String reason, UUID actorId, Instant occurredAt) {}
