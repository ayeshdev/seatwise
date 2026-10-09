package com.seatwise.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    // Injected instead of calling Instant.now() so tests can pin time.
    // Storage is always UTC; the centre timezone is applied only at the edges.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
