package se.eplatform.flow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.FlowStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowRepository extends JpaRepository<Flow, UUID> {

    /**
     * Find all published and enabled flows with type and category eagerly loaded.
     */
    @Query("SELECT DISTINCT f FROM Flow f " +
           "LEFT JOIN FETCH f.type " +
           "LEFT JOIN FETCH f.category " +
           "WHERE f.status = 'PUBLISHED' AND f.enabled = true " +
           "AND (f.publishDate IS NULL OR f.publishDate <= CURRENT_TIMESTAMP) " +
           "AND (f.unpublishDate IS NULL OR f.unpublishDate > CURRENT_TIMESTAMP)")
    List<Flow> findAllAccessible();

    /**
     * Find all published flows with pagination.
     */
    Page<Flow> findByStatusAndEnabledTrue(FlowStatus status, Pageable pageable);

    /**
     * Find flows by type.
     */
    List<Flow> findByTypeIdAndStatus(UUID typeId, FlowStatus status);

    /**
     * Find flows by category.
     */
    List<Flow> findByCategoryIdAndStatus(UUID categoryId, FlowStatus status);

    /**
     * Find all versions of a flow family.
     */
    List<Flow> findByFamilyIdOrderByVersionDesc(UUID familyId);

    /**
     * Find the latest version of a flow family.
     */
    Optional<Flow> findFirstByFamilyIdOrderByVersionDesc(UUID familyId);

    /**
     * Find the latest published version of a flow family.
     */
    @Query("SELECT f FROM Flow f WHERE f.family.id = :familyId AND f.status = 'PUBLISHED' " +
           "ORDER BY f.version DESC LIMIT 1")
    Optional<Flow> findLatestPublishedByFamilyId(@Param("familyId") UUID familyId);

    /**
     * Search flows by name or description.
     */
    @Query("SELECT f FROM Flow f WHERE f.status = 'PUBLISHED' AND " +
           "(LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(f.shortDescription) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Flow> searchPublished(@Param("query") String query, Pageable pageable);

    /**
     * Check if a flow with the given name and version exists in a family.
     */
    boolean existsByFamilyIdAndVersion(UUID familyId, Integer version);

    /**
     * Count flows by status.
     */
    long countByStatus(FlowStatus status);

    /**
     * Find a flow by ID with basic relations eagerly loaded.
     */
    @Query("SELECT f FROM Flow f " +
           "LEFT JOIN FETCH f.type " +
           "LEFT JOIN FETCH f.category " +
           "LEFT JOIN FETCH f.family " +
           "WHERE f.id = :id")
    Optional<Flow> findByIdWithBasicRelations(@Param("id") UUID id);

    /**
     * Find all flows with basic relations for admin listing.
     */
    @Query("SELECT f FROM Flow f " +
           "LEFT JOIN FETCH f.type " +
           "LEFT JOIN FETCH f.category " +
           "LEFT JOIN FETCH f.family")
    Page<Flow> findAllWithRelations(Pageable pageable);
}
