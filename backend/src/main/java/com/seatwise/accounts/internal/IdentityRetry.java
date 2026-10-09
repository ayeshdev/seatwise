package com.seatwise.accounts.internal;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries a Keycloak call while it reports IDENTITY_UNAVAILABLE. Used only by
 * the startup seeders: on a fresh {@code docker compose up} the API can be
 * ready a few seconds before Keycloak has imported its realm. Request-time
 * calls never retry; the Admin just sees the 503 and tries again.
 */
final class IdentityRetry {

    private static final Logger log = LoggerFactory.getLogger(IdentityRetry.class);

    static final int ATTEMPTS = 10;
    static final Duration BACKOFF = Duration.ofSeconds(3);

    private IdentityRetry() {}

    static <T> T call(String what, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (DomainException e) {
                if (e.code() != ErrorCode.IDENTITY_UNAVAILABLE || attempt >= ATTEMPTS) {
                    throw e;
                }
                log.info("{}: Keycloak not reachable yet (attempt {}/{}), retrying in {}s",
                        what, attempt, ATTEMPTS, BACKOFF.toSeconds());
                sleep();
            }
        }
    }

    static void run(String what, Runnable action) {
        call(what, () -> {
            action.run();
            return null;
        });
    }

    private static void sleep() {
        try {
            Thread.sleep(BACKOFF);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "Interrupted while waiting for Keycloak");
        }
    }
}
