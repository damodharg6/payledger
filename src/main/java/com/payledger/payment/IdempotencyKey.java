package com.payledger.payment;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Remembers one {@code Idempotency-Key} per merchant. The primary key is what makes retries safe:
 * when two requests with the same key race, the database lets only one INSERT win.
 * <p>
 * {@code requestHash} is the SHA-256 of the request body (same key + different body → 422).
 * {@code paymentId}/{@code responseCode} stay null while the first request is still running
 * (a duplicate arriving then gets 409).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey extends AssignedIdEntity<IdempotencyKeyId> {

    @EmbeddedId
    private IdempotencyKeyId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected IdempotencyKey() {
    }

    public IdempotencyKey(IdempotencyKeyId id, String requestHash, Instant createdAt) {
        this(id, requestHash, null, null, createdAt);
    }

    /** Full constructor, for tests that need a completed key. */
    IdempotencyKey(IdempotencyKeyId id, String requestHash, UUID paymentId, Integer responseCode, Instant createdAt) {
        this.id = id;
        this.requestHash = requestHash;
        this.paymentId = paymentId;
        this.responseCode = responseCode;
        this.createdAt = createdAt;
    }

    @Override
    public IdempotencyKeyId getId() {
        return id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
