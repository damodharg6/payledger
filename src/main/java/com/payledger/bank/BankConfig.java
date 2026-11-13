package com.payledger.bank;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code payledger.bank.*} to {@link BankProperties}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BankProperties.class)
public class BankConfig {
}
