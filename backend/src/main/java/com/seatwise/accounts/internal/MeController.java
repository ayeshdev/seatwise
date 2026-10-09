package com.seatwise.accounts.internal;

import com.seatwise.common.security.ActorProvider;
import com.seatwise.common.security.Policies;
import com.seatwise.common.security.StaffPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own profile (FR-AUTH-10); the UI builds its role-aware navigation from it. */
@RestController
public class MeController {

    private final ActorProvider actors;

    public MeController(ActorProvider actors) {
        this.actors = actors;
    }

    @GetMapping("/api/v1/me")
    @PreAuthorize(Policies.ANY_STAFF)
    public MeResponse me() {
        // Already loaded from the database by the authentication converter for
        // this request, so no second lookup is needed.
        StaffPrincipal me = actors.current();
        return new MeResponse(me.id(), me.email(), me.fullName(), me.role());
    }
}
