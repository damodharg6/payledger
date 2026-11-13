package com.payledger.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes payments whose outcome was unknown: the bank timed out and the first inquiry said
 * "pending", or the server crashed mid-flow. Every run asks the bank again about payments that
 * have been CREATED/PROCESSING for longer than {@code inquiry-after}.
 * <p>
 * Safe to run on several app instances at once: {@link PaymentTransitions#applyBankResult} locks
 * the payment and ignores results for payments that are already resolved.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Component
@EnableConfigurationProperties(PaymentProcessingProperties.class)
public class UnresolvedPaymentJob {

    private static final Logger log = LoggerFactory.getLogger(UnresolvedPaymentJob.class);

    private final PaymentTransitions transitions;
    private final PaymentProcessor processor;
    private final PaymentProcessingProperties props;
    private final Clock clock;

    public UnresolvedPaymentJob(PaymentTransitions transitions, PaymentProcessor processor,
                                PaymentProcessingProperties props, Clock clock) {
        this.transitions = transitions;
        this.processor = processor;
        this.props = props;
        this.clock = clock;
    }

    /** Returns how many payments reached a final status in this run. */
    @Scheduled(fixedDelayString = "${payledger.payments.inquiry-interval:PT15S}",
               initialDelayString = "${payledger.payments.inquiry-interval:PT15S}")
    public int run() {
        Instant cutoff = clock.instant().minus(props.inquiryAfter());
        List<UUID> ids = transitions.findUnresolved(cutoff, props.inquiryBatchSize());
        int resolved = 0;
        for (UUID id : ids) {
            try {
                PaymentStatus status = processor.resolve(id);
                if (status != PaymentStatus.PROCESSING) {
                    resolved++;
                    log.info("Inquiry resolved payment {} as {}", id, status);
                }
            } catch (RuntimeException e) {
                log.error("Inquiry for payment {} failed; will retry next run", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("Unresolved payment job: {} checked, {} resolved", ids.size(), resolved);
        }
        return resolved;
    }
}
