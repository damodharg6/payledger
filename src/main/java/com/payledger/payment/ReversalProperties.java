package com.payledger.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the auto-reversal job ({@code payledger.reversals.*}).
 *
 * @param batchSize most reversals one run performs (the next run continues)
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ConfigurationProperties("payledger.reversals")
public record ReversalProperties(int batchSize) {

    public ReversalProperties {
        batchSize = batchSize <= 0 ? 100 : batchSize;
    }
}
