package se.eplatform.storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.storage.domain.Attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Find attachment by stored filename.
     */
    Optional<Attachment> findByStoredFilename(String storedFilename);

    /**
     * Find non-deleted attachments for a case.
     */
    @Query("SELECT a FROM Attachment a WHERE a.caseEntity.id = :caseId AND a.deleted = false")
    List<Attachment> findByCaseId(@Param("caseId") UUID caseId);

    /**
     * Find non-deleted attachments for a specific query definition in a case.
     */
    @Query("SELECT a FROM Attachment a WHERE a.caseEntity.id = :caseId " +
           "AND a.queryDefinitionId = :queryDefId AND a.deleted = false")
    List<Attachment> findByCaseIdAndQueryDefinitionId(
            @Param("caseId") UUID caseId,
            @Param("queryDefId") UUID queryDefId);

    /**
     * Find all attachments uploaded by a user.
     */
    List<Attachment> findByUploadedByAndDeletedFalse(UUID uploadedBy);

    /**
     * Count total storage used by a user.
     */
    @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM Attachment a " +
           "WHERE a.uploadedBy = :userId AND a.deleted = false")
    Long sumFileSizeByUploadedBy(@Param("userId") UUID userId);

    /**
     * Find orphaned attachments (not linked to any case, older than threshold).
     */
    @Query("SELECT a FROM Attachment a WHERE a.caseEntity IS NULL " +
           "AND a.uploadedAt < :threshold AND a.deleted = false")
    List<Attachment> findOrphanedAttachments(@Param("threshold") java.time.Instant threshold);
}
