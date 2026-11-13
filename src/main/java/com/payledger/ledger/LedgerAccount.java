package com.payledger.ledger;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A bucket that money moves in and out of. {@code balancePaise} is a cache for fast reads;
 * the source of truth is always the sum of this account's {@link LedgerEntry} rows.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20, updatable = false)
    private OwnerType ownerType;

    @Column(name = "owner_ref", nullable = false, length = 100, updatable = false)
    private String ownerRef;

    @Column(nullable = false, length = 120)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected LedgerAccount() {
    }

    public LedgerAccount(UUID id, OwnerType ownerType, String ownerRef, String name, String currency, Instant createdAt) {
        this.id = id;
        this.ownerType = ownerType;
        this.ownerRef = ownerRef;
        this.name = name;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public String getOwnerRef() {
        return ownerRef;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalancePaise() {
        return balancePaise;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
