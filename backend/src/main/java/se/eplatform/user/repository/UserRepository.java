package se.eplatform.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.user.domain.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

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

    /**
     * Search by name/e-mail and filter by role. Pass an empty string to skip a filter.
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:query = '' OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:role = '' OR EXISTS (SELECT r FROM u.roles r WHERE r.name = :role))")
    Page<User> searchWithRole(@Param("query") String query, @Param("role") String role, Pageable pageable);
}
