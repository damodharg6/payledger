package com.payledger.bank;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Behaviour of the mock bank and the client-side timeout, from {@code payledger.bank.*} in
 * application.yml. The four rates are probabilities (not money, so {@code double} is fine) and
 * must add up to 1.
 *
 * @param successRate        share of charges that succeed
 * @param declineRate        share declined before any money moves
 * @param debitedFailureRate share where the customer is debited but the payment fails
 * @param timeoutRate        share where the bank answers too late (the real outcome is decided, just slow)
 * @param minLatency         fastest normal bank answer
 * @param maxLatency         slowest normal bank answer
 * @param timeout            how long PayLedger waits for the bank before giving up and inquiring
 * @param slowResponseDelay  how long a "timeout" charge takes (must be longer than {@code timeout})
 * @param inquiryPendingRate share of inquiries on random charges that answer "still pending"
 * @param seed               optional random seed for repeatable runs
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ConfigurationProperties("payledger.bank")
public record BankProperties(
        double successRate,
        double declineRate,
        double debitedFailureRate,
        double timeoutRate,
        Duration minLatency,
        Duration maxLatency,
        Duration timeout,
        Duration slowResponseDelay,
        double inquiryPendingRate,
        Long seed) {

    public BankProperties {
        for (double rate : new double[] {successRate, declineRate, debitedFailureRate, timeoutRate, inquiryPendingRate}) {
            if (rate < 0 || rate > 1) {
                throw new IllegalArgumentException("Bank rates must be between 0 and 1");
            }
        }
        double sum = successRate + declineRate + debitedFailureRate + timeoutRate;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("success + decline + debited-failure + timeout rates must add up to 1, got " + sum);
        }
        minLatency = minLatency == null ? Duration.ZERO : minLatency;
        maxLatency = maxLatency == null ? minLatency : maxLatency;
        if (maxLatency.compareTo(minLatency) < 0) {
            throw new IllegalArgumentException("max-latency must be >= min-latency");
        }
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        slowResponseDelay = slowResponseDelay == null ? timeout.multipliedBy(2) : slowResponseDelay;
    }
}
