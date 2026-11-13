package com.payledger.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every {@code payledger.reversals.interval} (default 30 s), returns money to customers who were
 * debited for a payment that failed: the RBI T+1 auto-reversal.
 * <p>
 * Reversals run as soon as possible, not at the deadline: the deadline is the latest allowed
 * time, not a target. Each payment is claimed with {@code FOR UPDATE SKIP LOCKED}, so any number
 * of app instances can run this job at once without reversing the same payment twice.
 * <p>
 * Counters for Grafana/alerts: {@code payledger.reversals{outcome=on_time|late|failed}}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Component
@EnableConfigurationProperties(ReversalProperties.class)
public class AutoReversalJob {

    private static final Logger log = LoggerFactory.getLogger(AutoReversalJob.class);

    /** What one run did. */
    public record Summary(int reversed, int late, int failed, long stillOverdue) {
    }

    private final ReversalService reversals;
    private final ReversalProperties props;
    private final Counter onTime;
    private final Counter late;
    private final Counter failed;

    public AutoReversalJob(ReversalService reversals, ReversalProperties props, MeterRegistry meters) {
        this.reversals = reversals;
        this.props = props;
        this.onTime = meters.counter("payledger.reversals", "outcome", "on_time");
        this.late = meters.counter("payledger.reversals", "outcome", "late");
        this.failed = meters.counter("payledger.reversals", "outcome", "failed");
    }

    @Scheduled(fixedDelayString = "${payledger.reversals.interval:PT30S}",
               initialDelayString = "${payledger.reversals.initial-delay:PT10S}")
    public Summary run() {
        Set<UUID> failedThisRun = new HashSet<>();
        int reversedCount = 0;
        int lateCount = 0;
        for (int i = 0; i < props.batchSize(); i++) {
            try {
                Optional<ReversalResult> result = reversals.reverseNext(failedThisRun);
                if (result.isEmpty()) {
                    break;  // nothing left that this worker can claim
                }
                reversedCount++;
                if (result.get().late()) {
                    lateCount++;
                    late.increment();
                } else {
                    onTime.increment();
                }
            } catch (ReversalFailedException e) {
                failedThisRun.add(e.paymentId());  // skip it for the rest of this run; retried next run
                failed.increment();
                log.error("Could not reverse payment {}; will retry next run", e.paymentId(), e);
            } catch (RuntimeException e) {
                log.error("Auto-reversal run stopped early", e);  // e.g. database unavailable
                break;
            }
        }
        long overdue = reversals.countOverdue();
        if (overdue > 0) {
            log.warn("T+1 ALERT: {} debited-but-failed payment(s) are past their reversal deadline", overdue);
        }
        if (reversedCount > 0 || !failedThisRun.isEmpty()) {
            log.info("Auto-reversal run: {} reversed ({} late), {} failed", reversedCount, lateCount, failedThisRun.size());
        }
        return new Summary(reversedCount, lateCount, failedThisRun.size(), overdue);
    }
}
