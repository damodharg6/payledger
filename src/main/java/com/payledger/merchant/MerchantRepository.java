package com.payledger.merchant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link Merchant}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    /** Uses the unique index {@code ux_merchants_api_key_hash} from migration V2. */
    Optional<Merchant> findByApiKeyHash(String apiKeyHash);

    boolean existsByEmail(String email);
}
