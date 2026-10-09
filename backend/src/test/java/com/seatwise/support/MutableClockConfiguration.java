package com.seatwise.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock with a {@link MutableClock}, so tests can
 * walk a workshop from OPEN through IN_PROGRESS to COMPLETED. Import it next to
 * {@link TestcontainersConfiguration}; every service, the seat statements and
 * the derived status all read this one clock.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    /** A Monday morning in the centre's timezone (Europe/London, BST): 2026-10-12 09:00 local. */
    public static final Instant MONDAY_MORNING = Instant.parse("2026-10-12T08:00:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(MONDAY_MORNING);
    }
}
