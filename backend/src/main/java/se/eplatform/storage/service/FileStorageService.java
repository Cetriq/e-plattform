package se.eplatform.storage.service;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.storage.domain.Attachment;
import se.eplatform.storage.repository.AttachmentRepository;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/csv"
    );

    private final MinioClient minioClient;
    private final AttachmentRepository attachmentRepository;
    private final CaseRepository caseRepository;

    @Value("${minio.bucket.attachments}")
    private String attachmentsBucket;

    public FileStorageService(MinioClient minioClient,
                              AttachmentRepository attachmentRepository,
                              CaseRepository caseRepository) {
        this.minioClient = minioClient;
        this.attachmentRepository = attachmentRepository;
        this.caseRepository = caseRepository;
    }

    /**
     * Upload a file and create an attachment record.
     */
    @Transactional
    public Attachment uploadFile(MultipartFile file, UUID userId, UUID caseId, UUID queryDefinitionId) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();

        // Generate unique stored filename
        String storedFilename = generateStoredFilename(originalFilename);

        try (InputStream inputStream = file.getInputStream()) {
            // Calculate checksum
            String checksum = calculateChecksum(file.getBytes());

            // Upload to MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(attachmentsBucket)
                    .object(storedFilename)
                    .stream(file.getInputStream(), fileSize, -1)
                    .contentType(contentType)
                    .build());

            log.info("Uploaded file {} to MinIO bucket {}", storedFilename, attachmentsBucket);

            // Create attachment record
            Attachment attachment = new Attachment(
                    originalFilename,
                    storedFilename,
                    contentType,
                    fileSize,
                    attachmentsBucket,
                    userId
            );
            attachment.setChecksum(checksum);
            attachment.setQueryDefinitionId(queryDefinitionId);

            // Link to case if provided
            if (caseId != null) {
                Case caseEntity = caseRepository.findById(caseId)
                        .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
                attachment.setCaseEntity(caseEntity);
            }

            return attachmentRepository.save(attachment);

        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * Download a file by attachment ID.
     */
    public InputStream downloadFile(UUID attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        if (attachment.isDeleted()) {
            throw new IllegalStateException("Attachment has been deleted");
        }

        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(attachment.getBucket())
                    .object(attachment.getStoredFilename())
                    .build());
        } catch (Exception e) {
            log.error("Failed to download file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    /**
     * Get attachment metadata by ID.
     */
    public Optional<Attachment> getAttachment(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .filter(a -> !a.isDeleted());
    }

    /**
     * Get all attachments for a case.
     */
    public List<Attachment> getAttachmentsForCase(UUID caseId) {
        return attachmentRepository.findByCaseId(caseId);
    }

    /**
     * Get attachments for a specific field in a case.
     */
    public List<Attachment> getAttachmentsForField(UUID caseId, UUID queryDefinitionId) {
        return attachmentRepository.findByCaseIdAndQueryDefinitionId(caseId, queryDefinitionId);
    }

    /**
     * Generate a pre-signed URL for direct download.
     */
    public String generateDownloadUrl(UUID attachmentId, int expiryMinutes) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        if (attachment.isDeleted()) {
            throw new IllegalStateException("Attachment has been deleted");
        }

        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(attachment.getBucket())
                    .object(attachment.getStoredFilename())
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate download URL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * Soft delete an attachment.
     */
    @Transactional
    public void deleteAttachment(UUID attachmentId, UUID userId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        // Check ownership or admin
        if (!attachment.getUploadedBy().equals(userId)) {
            throw new IllegalStateException("Not authorized to delete this attachment");
        }

        attachment.markDeleted();
        attachmentRepository.save(attachment);

        log.info("Soft deleted attachment {} by user {}", attachmentId, userId);
    }

    /**
     * Permanently delete attachment and file from storage.
     * This should only be called by cleanup jobs.
     */
    @Transactional
    public void permanentlyDelete(UUID attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        try {
            // Delete from MinIO
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(attachment.getBucket())
                    .object(attachment.getStoredFilename())
                    .build());

            // Delete from database
            attachmentRepository.delete(attachment);

            log.info("Permanently deleted attachment {}", attachmentId);

        } catch (Exception e) {
            log.error("Failed to permanently delete attachment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to permanently delete attachment: " + e.getMessage(), e);
        }
    }

    /**
     * Link an existing attachment to a case.
     */
    @Transactional
    public Attachment linkToCase(UUID attachmentId, UUID caseId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        attachment.setCaseEntity(caseEntity);
        return attachmentRepository.save(attachment);
    }

    /**
     * Get total storage used by a user.
     */
    public long getStorageUsedByUser(UUID userId) {
        return attachmentRepository.sumFileSizeByUploadedBy(userId);
    }

    /**
     * Clean up orphaned attachments older than the specified hours.
     */
    @Transactional
    public int cleanupOrphanedAttachments(int hoursOld) {
        Instant threshold = Instant.now().minus(hoursOld, ChronoUnit.HOURS);
        List<Attachment> orphans = attachmentRepository.findOrphanedAttachments(threshold);

        for (Attachment orphan : orphans) {
            try {
                permanentlyDelete(orphan.getId());
            } catch (Exception e) {
                log.warn("Failed to cleanup orphan {}: {}", orphan.getId(), e.getMessage());
            }
        }

        log.info("Cleaned up {} orphaned attachments", orphans.size());
        return orphans.size();
    }

    // Private helper methods

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " +
                    (MAX_FILE_SIZE / 1024 / 1024) + " MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }
    }

    private String generateStoredFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return UUID.randomUUID().toString() + extension;
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to calculate checksum: {}", e.getMessage());
            return null;
        }
    }
}
