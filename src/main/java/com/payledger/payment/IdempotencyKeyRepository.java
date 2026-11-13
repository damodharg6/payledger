package com.payledger.payment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link IdempotencyKey}. The important method is {@link #insertIfAbsent}:
 * the PRIMARY KEY (merchant_id, idempotency_key) decides the winner of a race, not Java code.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {

    /**
     * Tries to claim the key. Returns 1 if this call inserted the row (we own the request),
     * 0 if the row already exists. When two transactions insert the same key at once,
     * PostgreSQL makes the second one wait until the first commits, then returns 0 for it.
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (merchant_id, idempotency_key, request_hash, created_at)
            VALUES (:merchantId, :key, :requestHash, :now)
            ON CONFLICT (merchant_id, idempotency_key) DO NOTHING""", nativeQuery = true)
    int insertIfAbsent(@Param("merchantId") UUID merchantId, @Param("key") String key,
                       @Param("requestHash") String requestHash, @Param("now") Instant now);

    /** Links the payment created for this key; the request is still in progress. */
    @Modifying
    @Query(value = """
            UPDATE idempotency_keys SET payment_id = :paymentId
            WHERE merchant_id = :merchantId AND idempotency_key = :key AND payment_id IS NULL""", nativeQuery = true)
    int attachPayment(@Param("merchantId") UUID merchantId, @Param("key") String key, @Param("paymentId") UUID paymentId);

    /** Records the result, which turns later duplicates into replays. */
    @Modifying
    @Query(value = """
            UPDATE idempotency_keys SET payment_id = :paymentId, response_code = :responseCode
            WHERE merchant_id = :merchantId AND idempotency_key = :key""", nativeQuery = true)
    int complete(@Param("merchantId") UUID merchantId, @Param("key") String key,
                 @Param("paymentId") UUID paymentId, @Param("responseCode") int responseCode);

    /** Frees a claim whose request failed before creating a payment, so the client can retry. */
    @Modifying
    @Query(value = """
            DELETE FROM idempotency_keys
            WHERE merchant_id = :merchantId AND idempotency_key = :key AND payment_id IS NULL""", nativeQuery = true)
    int deleteUnfinished(@Param("merchantId") UUID merchantId, @Param("key") String key);

    /**
     * Frees a claim abandoned by a crashed request (no payment, older than the cutoff). The
     * conditions in the WHERE clause make this safe if two requests try it at the same time:
     * only one DELETE can match.
     */
    @Modifying
    @Query(value = """
            DELETE FROM idempotency_keys
            WHERE merchant_id = :merchantId AND idempotency_key = :key
              AND payment_id IS NULL AND response_code IS NULL AND created_at < :cutoff""", nativeQuery = true)
    int deleteStale(@Param("merchantId") UUID merchantId, @Param("key") String key, @Param("cutoff") Instant cutoff);
}
