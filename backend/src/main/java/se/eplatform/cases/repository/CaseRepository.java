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
     * The cases staff may reach (CaseAccessPolicy), as a condition on the alias
     * "c". Parameters: orgId (the active organisation), staffId (the user) and
     * clearance (0 NORMAL, 1 RESTRICTED, 2 CONFIDENTIAL). Single reads, lists,
     * searches and counts all use this condition, so they cannot disagree.
     * The staff role is checked by CaseAccessPolicy before any of them run.
     */
    String STAFF_ACCESS =
            "c.organizationId = :orgId " +
            "AND (CASE c.confidentiality WHEN 'NORMAL' THEN 0 WHEN 'RESTRICTED' THEN 1 ELSE 2 END) <= :clearance " +
            "AND (c.flow.accessScope = 'ORGANIZATION' " +
            "     OR c.assignedTo.id = :staffId " +
            "     OR (c.flow.accessScope = 'GROUP' AND c.handlingGroupId IN " +
            "         (SELECT g.id FROM User u JOIN u.groups g WHERE u.id = :staffId))) ";

    /** Whether staff may reach the case (see {@link #STAFF_ACCESS}). */
    @Query("SELECT COUNT(c) > 0 FROM Case c WHERE c.id = :caseId AND " + STAFF_ACCESS)
    boolean isVisibleToStaff(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId,
                             @Param("staffId") UUID staffId, @Param("clearance") int clearance);

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
     * Whether the user created the case or is one of its owners.
     */
    @Query("SELECT COUNT(c) > 0 FROM Case c LEFT JOIN c.owners o " +
           "WHERE c.id = :caseId AND (c.createdBy.id = :userId OR o.id = :userId)")
    boolean isOwnedBy(@Param("caseId") UUID caseId, @Param("userId") UUID userId);

    /**
     * Ids of the cases created by any of the given users.
     */
    @Query("SELECT c.id FROM Case c WHERE c.createdBy.id IN :userIds")
    List<UUID> findIdsByCreatedByIn(@Param("userIds") List<UUID> userIds);

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
     * Submitted cases, optionally limited to those assigned to a user
     * (assignedTo set) or to unassigned ones (unassignedOnly).
     */
    @EntityGraph(attributePaths = {"flow", "flow.steps", "status", "assignedTo"})
    @Query("SELECT c FROM Case c LEFT JOIN c.assignedTo a WHERE c.submittedAt IS NOT NULL " +
           "AND (:assignedTo IS NULL OR a.id = :assignedTo) " +
           "AND (:unassignedOnly = false OR a IS NULL) AND " + STAFF_ACCESS)
    Page<Case> findSubmitted(@Param("assignedTo") UUID assignedTo,
                             @Param("unassignedOnly") boolean unassignedOnly,
                             @Param("orgId") UUID orgId, @Param("staffId") UUID staffId,
                             @Param("clearance") int clearance,
                             Pageable pageable);

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

    // ---------- Statistics for one organisation ----------

    long countByOrganizationId(UUID organizationId);

    @Query("SELECT COUNT(c) FROM Case c WHERE c.organizationId = :orgId AND c.status.name = :statusName")
    long countByStatusName(@Param("orgId") UUID orgId, @Param("statusName") String statusName);

    long countByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant timestamp);

    List<Case> findByOrganizationIdAndCreatedAtAfter(UUID organizationId, Instant timestamp);

    @Query("SELECT c FROM Case c WHERE c.organizationId = :orgId AND c.status.name = :statusName")
    List<Case> findByStatusName(@Param("orgId") UUID orgId, @Param("statusName") String statusName);

    @Query("SELECT c.status.name, COUNT(c) FROM Case c WHERE c.organizationId = :orgId GROUP BY c.status.name")
    List<Object[]> countGroupByStatus(@Param("orgId") UUID orgId);

    @Query("SELECT c.flow.id, c.flow.name, COUNT(c) FROM Case c WHERE c.organizationId = :orgId " +
           "GROUP BY c.flow.id, c.flow.name ORDER BY COUNT(c) DESC")
    List<Object[]> countGroupByFlow(@Param("orgId") UUID orgId);

    /**
     * Search the cases staff may reach by reference number or description.
     */
    @EntityGraph(attributePaths = {"flow", "flow.steps", "status", "assignedTo"})
    @Query("SELECT c FROM Case c WHERE (" +
           "LOWER(c.referenceNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.userDescription) LIKE LOWER(CONCAT('%', :query, '%'))) AND " + STAFF_ACCESS)
    Page<Case> search(@Param("query") String query,
                      @Param("orgId") UUID orgId, @Param("staffId") UUID staffId,
                      @Param("clearance") int clearance,
                      Pageable pageable);

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
