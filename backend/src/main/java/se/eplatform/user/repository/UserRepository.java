package se.eplatform.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /** Active members of an organisation with any of the roles there. */
    @Query("SELECT DISTINCT u FROM User u JOIN u.memberships m JOIN m.roles r " +
           "WHERE m.organization.id = :organizationId AND r.name IN :roleNames AND u.active = true " +
           "ORDER BY u.firstName, u.lastName")
    List<User> findActiveWithAnyRole(@Param("organizationId") UUID organizationId,
                                     @Param("roleNames") Collection<String> roleNames);

    /**
     * Ids of the temporary citizen accounts created for demo visitors.
     */
    @Query("SELECT u.id FROM User u WHERE u.email LIKE CONCAT('%', :domain) AND u.createdAt < :before")
    List<UUID> findDemoUserIdsCreatedBefore(@Param("domain") String domain, @Param("before") Instant before);

    Optional<User> findByExternalId(String externalId);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByExternalId(String externalId);

    Page<User> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<User> findByActiveTrue(Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<User> search(@Param("query") String query, Pageable pageable);

    /** Search among the people an organisation holds data about: those with cases there, and its members. */
    @Query("SELECT u FROM User u WHERE (" +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) AND (" +
           "EXISTS (SELECT c FROM Case c WHERE c.createdBy = u AND c.organizationId = :org) OR " +
           "EXISTS (SELECT m FROM OrganizationMembership m WHERE m.user = u AND m.organization.id = :org))")
    Page<User> searchInOrganization(@Param("query") String query, @Param("org") UUID organizationId, Pageable pageable);

    /**
     * Members of an organisation, searched by name/e-mail and filtered by their
     * role there. Pass an empty string to skip a filter.
     */
    @Query("SELECT u FROM User u JOIN u.memberships m WHERE m.organization.id = :orgId AND " +
           "(:query = '' OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:role = '' OR EXISTS (SELECT r FROM m.roles r WHERE r.name = :role))")
    Page<User> searchMembers(@Param("orgId") UUID orgId, @Param("query") String query,
                             @Param("role") String role, Pageable pageable);
}
