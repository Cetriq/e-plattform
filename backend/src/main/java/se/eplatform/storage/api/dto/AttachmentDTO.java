package se.eplatform.storage.api.dto;

import se.eplatform.storage.domain.Attachment;

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
        String downloadUrl
) {
    public static AttachmentDTO from(Attachment attachment) {
        return new AttachmentDTO(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                formatFileSize(attachment.getFileSize()),
                attachment.getCaseEntity() != null ? attachment.getCaseEntity().getId() : null,
                attachment.getQueryDefinitionId(),
                attachment.getUploadedBy(),
                attachment.getUploadedAt(),
                attachment.isImage(),
                attachment.isPdf(),
                "/api/v1/files/" + attachment.getId() + "/download"
        );
    }

    private static String formatFileSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
