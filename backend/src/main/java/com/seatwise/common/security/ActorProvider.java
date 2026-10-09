package com.seatwise.common.security;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is acting on this request. Services ask this instead of taking an actor
 * id from the client, so "created by" and "cancelled by" can't be spoofed.
 */
@Component
public class ActorProvider {

    /** The current staff member; fails with UNAUTHENTICATED outside an authenticated request. */
    public StaffPrincipal current() {
        return find().orElseThrow(() -> new DomainException(
                ErrorCode.UNAUTHENTICATED, "Please sign in to continue."));
    }

    public UUID currentStaffId() {
        return current().id();
    }

    /** Empty for system work (startup seeding) that has no signed-in user. */
    public Optional<StaffPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal staff) {
            return Optional.of(staff);
        }
        return Optional.empty();
    }
}
