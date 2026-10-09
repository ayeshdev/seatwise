package com.seatwise.accounts.internal;

import java.util.Optional;
import java.util.UUID;

/**
 * The identity provider's user store, as far as Seatwise needs it. Keycloak
 * holds passwords and the "can sign in" switch; the database holds roles.
 *
 * <p>Failures surface as {@code DomainException}: EMAIL_IN_USE when the
 * identity already exists, VALIDATION_FAILED for a rejected password, and
 * IDENTITY_UNAVAILABLE when the provider can't be reached.
 */
public interface IdentityProvisioner {

    /** Creates an enabled user (username = email) and returns its id, which becomes the staff account id. */
    UUID createUser(String email, String fullName, String password, boolean temporaryPassword);

    Optional<UUID> findUserIdByEmail(String email);

    /** Disabling stops new sign-ins; existing tokens are cut off by the database flag. */
    void setEnabled(UUID userId, boolean enabled);

    void resetPassword(UUID userId, String password, boolean temporaryPassword);

    /** Compensation for a create whose database insert failed. A missing user is not an error. */
    void deleteUser(UUID userId);
}
