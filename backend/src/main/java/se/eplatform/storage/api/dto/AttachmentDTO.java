package se.eplatform.storage.api.dto;

import se.eplatform.storage.domain.Attachment;
import se.eplatform.storage.service.FileStorageService;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Attachment entity.
 */
public record AttachmentDTO(
        UUID id,
        String originalFilename,
        String contentType,
        Long fileSize,
        String fileSizeFormatted,
        UUID caseId,
        UUID queryDefinitionId,
        UUID uploadedBy,
        Instant uploadedAt,
        boolean isImage,
        boolean isPdf,
        /** Where the file is downloaded: the backend, or the frontend's Blob route. */
        String downloadUrl,
        /** Path in the private Vercel Blob store, or null for files kept by the backend. */
        String blobPathname
) {
    public static AttachmentDTO from(Attachment attachment) {
        return new AttachmentDTO(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                formatFileSize(attachment.getFileSize()),
                attachment.getCaseEntity() != null ? attachment.getCaseEntity().getId() : attachment.getCaseId(),
                attachment.getQueryDefinitionId(),
                attachment.getUploadedBy(),
                attachment.getUploadedAt(),
                attachment.isImage(),
                attachment.isPdf(),
                isBlob(attachment)
                        ? "/api/blob/files/" + attachment.getId()
                        : "/api/v1/files/" + attachment.getId() + "/download",
                isBlob(attachment) ? attachment.getStoredFilename() : null
        );
    }

    private static boolean isBlob(Attachment attachment) {
        return FileStorageService.VERCEL_BLOB_BUCKET.equals(attachment.getBucket());
    }

    private static String formatFileSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
