package com.payledger.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for finishing payments whose bank outcome was unknown ({@code payledger.payments.*}).
 *
 * @param inquiryAfter     only inquire about payments untouched for at least this long, so we
 *                         never race a request that is still talking to the bank
 * @param inquiryBatchSize how many unresolved payments one job run handles
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ConfigurationProperties("payledger.payments")
public record PaymentProcessingProperties(Duration inquiryAfter, int inquiryBatchSize) {

    public PaymentProcessingProperties {
        inquiryAfter = inquiryAfter == null ? Duration.ofSeconds(30) : inquiryAfter;
        inquiryBatchSize = inquiryBatchSize <= 0 ? 50 : inquiryBatchSize;
    }
}
