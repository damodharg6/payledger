package com.payledger.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One debit or credit line. Append-only at two levels: {@link Immutable} stops Hibernate from
 * ever issuing an UPDATE, and the database trigger {@code ledger_entries_append_only} rejects
 * UPDATE and DELETE from anyone. Mistakes are corrected with new, reversing entries.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Convert(converter = EntryDirectionConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1, updatable = false)
    private EntryDirection direction;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected LedgerEntry() {
    }

    public LedgerEntry(UUID transactionId, UUID accountId, EntryDirection direction, long amountPaise, Instant createdAt) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive");
        }
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountPaise = amountPaise;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
