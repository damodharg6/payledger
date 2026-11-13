package com.payledger.ledger;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One business event in the ledger (a payment, reversal or refund). It groups the
 * {@link LedgerEntry} rows whose debits must equal their credits.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Immutable
@Table(name = "ledger_transactions")
public class LedgerTransaction extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LedgerTransactionType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected LedgerTransaction() {
    }

    public LedgerTransaction(UUID id, UUID paymentId, LedgerTransactionType type, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.type = type;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public LedgerTransactionType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
