package com.payledger.merchant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link DashboardUser}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface DashboardUserRepository extends JpaRepository<DashboardUser, UUID> {

    Optional<DashboardUser> findByEmail(String email);
}
