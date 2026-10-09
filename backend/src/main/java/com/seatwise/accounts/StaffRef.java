package com.seatwise.accounts;

import java.util.Map;
import java.util.UUID;

/**
 * The {@code {id, fullName}} shape the API uses wherever it names who did
 * something (created by, registered by, cancelled by).
 */
public record StaffRef(UUID id, String fullName) {

    public static StaffRef of(StaffSummary staff) {
        return new StaffRef(staff.id(), staff.fullName());
    }

    /**
     * Resolves {@code id} against a batch lookup from {@link StaffDirectory#findAllById}.
     * Accounts are never deleted, so a miss means bad data; the id is still shown
     * rather than failing a whole history list. Null in, null out.
     */
    public static StaffRef resolve(UUID id, Map<UUID, StaffSummary> staff) {
        if (id == null) {
            return null;
        }
        StaffSummary found = staff.get(id);
        return found == null ? new StaffRef(id, "Unknown staff member") : of(found);
    }
}
