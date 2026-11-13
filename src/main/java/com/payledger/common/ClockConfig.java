package com.payledger.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single {@link Clock} for the whole app. Services ask this clock for "now" instead
 * of calling {@code Instant.now()}, so tests can freeze or move time (needed for T+1 deadlines).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
