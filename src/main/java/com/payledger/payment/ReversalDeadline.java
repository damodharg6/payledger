package com.payledger.payment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The RBI "T+1" deadline for returning money from a failed transaction.
 * <p>
 * T is the calendar day (in India, IST) on which the customer was debited. The money must be back
 * by the end of the next day, T+1. We store the deadline as the first instant of day T+2 in IST:
 * a reversal is on time if it happens <i>before</i> that instant.
 * <p>
 * Example: debited 13 Nov 2026 at 23:50 IST → reverse before 15 Nov 2026 00:00 IST
 * (= 14 Nov 2026 18:30 UTC).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public final class ReversalDeadline {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private ReversalDeadline() {
    }

    public static Instant tPlusOne(Instant debitedAt) {
        LocalDate t = LocalDate.ofInstant(debitedAt, IST);
        return t.plusDays(2).atStartOfDay(IST).toInstant();
    }
}
