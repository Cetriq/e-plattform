package se.eplatform.cases.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.domain.Priority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<Case, UUID> {

    /**
     * Find case by ID with flow and status eagerly loaded.
     */
    @EntityGraph(attributePaths = {"flow", "flow.steps", "status"})
    @Query("SELECT c FROM Case c WHERE c.id = :id")
    Optional<Case> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Find case by reference number.
     */
    Optional<Case> findByReferenceNumber(String referenceNumber);

    /**
     * Find all cases for a user (as owner).
     */
    @Query("SELECT DISTINCT c FROM Case c " +
           "JOIN c.owners o " +
           "LEFT JOIN FETCH c.flow f " +
           "LEFT JOIN FETCH f.steps " +
           "LEFT JOIN FETCH c.status " +
           "WHERE o.id = :userId " +
           "ORDER BY c.createdAt DESC")
    Page<Case> findByOwnerId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find all cases created by a user.
     */
    Page<Case> findByCreatedByIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find cases by flow.
     */
    Page<Case> findByFlowId(UUID flowId, Pageable pageable);

    /**
     * Find cases by status.
     */
    Page<Case> findByStatusId(UUID statusId, Pageable pageable);

    /**
     * Find cases by priority.
     */
    Page<Case> findByPriority(Priority priority, Pageable pageable);

    /**
     * Find submitted cases (not drafts) with flow eagerly loaded.
     */
    @EntityGraph(attributePaths = {"flow", "flow.steps", "status"})
    @Query("SELECT c FROM Case c WHERE c.submittedAt IS NOT NULL")
    Page<Case> findAllSubmitted(Pageable pageable);

    /**
     * Find draft cases for a user.
     */
    @Query("SELECT c FROM Case c WHERE c.createdBy.id = :userId AND c.submittedAt IS NULL " +
           "ORDER BY c.updatedAt DESC")
    List<Case> findDraftsByUserId(@Param("userId") UUID userId);

    /**
     * Count cases by flow.
     */
    long countByFlowId(UUID flowId);

    /**
     * Count cases by status.
     */
    long countByStatusId(UUID statusId);

    /**
     * Find cases submitted within a date range.
     */
    @Query("SELECT c FROM Case c WHERE c.submittedAt BETWEEN :start AND :end")
    List<Case> findSubmittedBetween(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Search cases by reference number or description.
     */
    @Query("SELECT c FROM Case c WHERE " +
           "LOWER(c.referenceNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.userDescription) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Case> search(@Param("query") String query, Pageable pageable);

    /**
     * Find case with all related data for manager view.
     * Note: Both steps and statusDefinitions are now Sets (not Lists) to avoid MultipleBagFetchException.
     */
    @Query("SELECT DISTINCT c FROM Case c " +
           "LEFT JOIN FETCH c.flow f " +
           "LEFT JOIN FETCH f.steps " +
           "LEFT JOIN FETCH f.statusDefinitions " +
           "LEFT JOIN FETCH c.status " +
           "LEFT JOIN FETCH c.createdBy " +
           "LEFT JOIN FETCH c.queryInstances qi " +
           "LEFT JOIN FETCH qi.queryDefinition " +
           "WHERE c.id = :id")
    Optional<Case> findByIdWithAllData(@Param("id") UUID id);
}
