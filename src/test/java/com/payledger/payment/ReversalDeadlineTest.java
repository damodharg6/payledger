package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * T+1 is about calendar days in India, not "24 hours from now".
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class ReversalDeadlineTest {

    @Test
    void lateEveningDebitIsDueBeforeMidnightOfTheNextDayInIst() {
        // 13 Nov 23:50 IST = 13 Nov 18:20 UTC  →  due before 15 Nov 00:00 IST = 14 Nov 18:30 UTC
        assertThat(ReversalDeadline.tPlusOne(Instant.parse("2026-11-13T18:20:00Z")))
                .isEqualTo(Instant.parse("2026-11-14T18:30:00Z"));
    }

    @Test
    void justAfterIstMidnightCountsAsTheNextDay() {
        // 14 Nov 00:10 IST = 13 Nov 18:40 UTC (still the 13th in UTC!)  →  due before 16 Nov 00:00 IST
        assertThat(ReversalDeadline.tPlusOne(Instant.parse("2026-11-13T18:40:00Z")))
                .isEqualTo(Instant.parse("2026-11-15T18:30:00Z"));
    }

    @Test
    void deadlineIsAlwaysBetweenOneAndTwoDaysAway() {
        Instant start = Instant.parse("2026-11-13T00:00:00Z");
        for (int minutes = 0; minutes < 48 * 60; minutes += 17) {
            Instant debited = start.plus(Duration.ofMinutes(minutes));
            Duration window = Duration.between(debited, ReversalDeadline.tPlusOne(debited));
            assertThat(window).isGreaterThan(Duration.ofDays(1)).isLessThanOrEqualTo(Duration.ofDays(2));
        }
    }
}
