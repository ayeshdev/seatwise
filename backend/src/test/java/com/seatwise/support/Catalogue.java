package com.seatwise.support;

import com.seatwise.registrations.internal.RegisterRequest;
import com.seatwise.workshops.internal.WorkshopRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Request builders and the fixed location ids seeded by V2__workshops.sql. */
public final class Catalogue {

    public static final UUID NORTHSIDE = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b01");
    public static final UUID RIVERSIDE = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b02");
    public static final UUID CENTRAL = UUID.fromString("3f9a2c4e-1b7d-4e8a-9c01-5d2e6f7a8b03");

    private Catalogue() {}

    /** A two-hour workshop at Northside. */
    public static WorkshopRequest workshop(String code, Instant startsAt, int capacity) {
        return workshop(code, "Workshop " + code, "Alex Instructor", NORTHSIDE, startsAt, capacity);
    }

    public static WorkshopRequest workshop(
            String code, String title, String instructor, UUID locationId, Instant startsAt, int capacity) {
        return new WorkshopRequest(code, title, null, instructor, locationId, startsAt,
                startsAt.plus(Duration.ofHours(2)), capacity, null);
    }

    /** The same request as an edit: other fields unchanged, the given capacity and loaded version. */
    public static WorkshopRequest edit(WorkshopRequest original, int capacity, long version) {
        return new WorkshopRequest(original.code(), original.title(), original.description(), original.instructor(),
                original.locationId(), original.startsAt(), original.endsAt(), capacity, version);
    }

    public static RegisterRequest attendee(int n) {
        return new RegisterRequest("Attendee " + n, "attendee" + n + "@example.com", false);
    }

    public static RegisterRequest attendee(String name, String email, boolean joinWaitlistIfFull) {
        return new RegisterRequest(name, email, joinWaitlistIfFull);
    }
}
