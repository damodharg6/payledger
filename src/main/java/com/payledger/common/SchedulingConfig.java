package com.payledger.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs. Set {@code payledger.scheduling.enabled=false} to switch
 * them off (integration tests do, and call the jobs directly so timing never makes them flaky).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "payledger.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
