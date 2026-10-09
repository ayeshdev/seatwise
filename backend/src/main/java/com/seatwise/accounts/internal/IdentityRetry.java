package com.seatwise.accounts.internal;

import com.seatwise.common.error.DomainException;
import com.seatwise.common.error.ErrorCode;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries a Keycloak call while it reports IDENTITY_UNAVAILABLE. Two uses:
 * the startup seeders (on a fresh {@code docker compose up} the API can be
 * ready a few seconds before Keycloak has imported its realm), and the
 * compensating delete after a failed account create, where giving up on the
 * first blip would leave an orphan user. Ordinary request-time calls never
 * retry; the Admin just sees the 503 and tries again.
 */
final class IdentityRetry {

    private static final Logger log = LoggerFactory.getLogger(IdentityRetry.class);

    /** Startup seeding: patient, because Keycloak may still be booting. */
    static final int ATTEMPTS = 10;
    static final Duration BACKOFF = Duration.ofSeconds(3);

    /** Compensation runs while an Admin waits on the request, so it is short. */
    static final int COMPENSATION_ATTEMPTS = 3;
    static final Duration COMPENSATION_BACKOFF = Duration.ofMillis(500);

    private IdentityRetry() {}

    static <T> T call(String what, Supplier<T> action) {
        return call(what, ATTEMPTS, BACKOFF, action);
    }

    static <T> T call(String what, int attempts, Duration backoff, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (DomainException e) {
                if (e.code() != ErrorCode.IDENTITY_UNAVAILABLE || attempt >= attempts) {
                    throw e;
                }
                log.info("{}: Keycloak not reachable yet (attempt {}/{}), retrying in {}ms",
                        what, attempt, attempts, backoff.toMillis());
                sleep(backoff);
            }
        }
    }

    static void run(String what, Runnable action) {
        call(what, () -> {
            action.run();
            return null;
        });
    }

    static void runForCompensation(String what, Runnable action) {
        call(what, COMPENSATION_ATTEMPTS, COMPENSATION_BACKOFF, () -> {
            action.run();
            return null;
        });
    }

    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DomainException(ErrorCode.IDENTITY_UNAVAILABLE, "Interrupted while waiting for Keycloak");
        }
    }
}
