package se.eplatform.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.eplatform.user.domain.OrganizationMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    /** Oldest first, so the first one is the default active organisation. */
    List<OrganizationMembership> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<OrganizationMembership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    long countByOrganizationId(UUID organizationId);
}
