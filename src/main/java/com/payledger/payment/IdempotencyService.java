package com.payledger.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes {@code POST /api/v1/payments} safe to retry.
 * <ol>
 *   <li>{@link #claim}: INSERT the key in its own short transaction and commit at once, so any
 *       duplicate can see it. The PRIMARY KEY guarantees only one request wins.</li>
 *   <li>The winner creates the payment and {@link #attachPayment links} it to the key, talks to
 *       the bank, then {@link #complete} stores the result.</li>
 *   <li>A duplicate that arrives later gets the stored result (replay); one that arrives while
 *       the winner is still running gets 409; one with a different body gets 422.</li>
 * </ol>
 * A plain "SELECT, then INSERT if missing" check would not be safe: two threads can both see
 * "missing" before either inserts. That race is exactly how customers get charged twice.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository keys;
    private final Clock clock;
    private final Duration staleAfter;

    public IdempotencyService(IdempotencyKeyRepository keys, Clock clock,
                              @Value("${payledger.idempotency.stale-after:PT5M}") Duration staleAfter) {
        this.keys = keys;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    /** What the caller should do with this request. */
    public record Claim(boolean replay, UUID paymentId, int responseCode) {
        static Claim newRequest() {
            return new Claim(false, null, 0);
        }

        static Claim replayOf(UUID paymentId, int responseCode) {
            return new Claim(true, paymentId, responseCode);
        }
    }

    /**
     * Claims the key for this request, or explains why it cannot.
     *
     * @return a new claim (go ahead) or a replay (return the stored result)
     * @throws IdempotencyKeyReusedException       same key, different body (422)
     * @throws IdempotencyRequestInProgressException same key, first request still running (409)
     */
    @Transactional
    public Claim claim(UUID merchantId, String key, String requestHash) {
        Instant now = clock.instant();
        if (keys.insertIfAbsent(merchantId, key, requestHash, now) == 1) {
            return Claim.newRequest();
        }

        IdempotencyKey existing = keys.findById(new IdempotencyKeyId(merchantId, key)).orElse(null);
        if (existing == null) {
            // The first request failed and released the key a moment ago; the client may retry.
            throw new IdempotencyRequestInProgressException(key);
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(key);
        }
        if (existing.getResponseCode() != null) {
            return Claim.replayOf(existing.getPaymentId(), existing.getResponseCode());
        }

        Instant cutoff = now.minus(staleAfter);
        boolean stale = existing.getCreatedAt().isBefore(cutoff);
        if (existing.getPaymentId() != null) {
            // The payment exists but its request never finished (e.g. the server crashed mid bank call).
            // Never create a second one: once stale, return the existing payment as it is now.
            if (stale) {
                log.warn("Idempotency key {} was never completed; replaying payment {}", key, existing.getPaymentId());
                return Claim.replayOf(existing.getPaymentId(), PaymentService.CREATED);
            }
            throw new IdempotencyRequestInProgressException(key);
        }
        if (stale && keys.deleteStale(merchantId, key, cutoff) == 1
                && keys.insertIfAbsent(merchantId, key, requestHash, now) == 1) {
            log.warn("Took over stale idempotency key {} for merchant {} (claimed at {})",
                    key, merchantId, existing.getCreatedAt());
            return Claim.newRequest();
        }
        throw new IdempotencyRequestInProgressException(key);
    }

    /**
     * Links the new payment to the key. Joins the caller's transaction, so the payment and the
     * link commit together. The key stays "in progress" (no response code) until {@link #complete}.
     */
    @Transactional
    public void attachPayment(UUID merchantId, String key, UUID paymentId) {
        if (keys.attachPayment(merchantId, key, paymentId) != 1) {
            throw new IllegalStateException("Idempotency key " + key + " was not claimed");
        }
    }

    /** Stores the final result, which turns later duplicates into replays. */
    @Transactional
    public void complete(UUID merchantId, String key, UUID paymentId, int responseCode) {
        if (keys.complete(merchantId, key, paymentId, responseCode) != 1) {
            throw new IllegalStateException("Idempotency key " + key + " was not claimed");
        }
    }

    /** Releases a claim whose request failed before a payment existed. */
    @Transactional
    public void release(UUID merchantId, String key) {
        keys.deleteUnfinished(merchantId, key);
    }

    /**
     * SHA-256 of the request's meaningful fields in a fixed order. Hashing the fields instead of
     * the raw JSON means extra spaces or a different key order do not count as a "different body".
     */
    public static String requestHash(CreatePaymentRequest request) {
        String canonical = request.amountPaise() + "|" + request.currencyOrDefault() + "|" + request.customerRef().trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
