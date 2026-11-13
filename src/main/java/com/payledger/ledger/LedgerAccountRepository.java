package com.payledger.ledger;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link LedgerAccount}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByOwnerTypeAndOwnerRefAndCurrency(OwnerType ownerType, String ownerRef, String currency);

    /**
     * Only the id, on purpose: the account entity should first enter the persistence context
     * through {@link #lockById}, so its balance is read <i>after</i> the lock is taken.
     */
    @Query("select a.id from LedgerAccount a where a.ownerType = :ownerType and a.ownerRef = :ownerRef and a.currency = :currency")
    Optional<UUID> findIdByOwner(@Param("ownerType") OwnerType ownerType, @Param("ownerRef") String ownerRef,
                                 @Param("currency") String currency);

    /** {@code SELECT ... FOR UPDATE}: other transactions wanting this row wait until we commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LedgerAccount a where a.id = :id")
    Optional<LedgerAccount> lockById(@Param("id") UUID id);

    /** Creates a customer account unless it exists; safe when two payments for a new customer race. */
    @Modifying
    @Query(value = """
            INSERT INTO ledger_accounts (id, owner_type, owner_ref, name, currency, created_at)
            VALUES (:id, 'CUSTOMER', :ownerRef, :name, :currency, :now)
            ON CONFLICT (owner_type, owner_ref, currency) DO NOTHING""", nativeQuery = true)
    int insertCustomerIfAbsent(@Param("id") UUID id, @Param("ownerRef") String ownerRef, @Param("name") String name,
                               @Param("currency") String currency, @Param("now") Instant now);
}
