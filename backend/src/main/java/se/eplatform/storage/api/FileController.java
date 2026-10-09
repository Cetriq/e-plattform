package se.eplatform.storage.api;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.service.AuditService;
import se.eplatform.auth.dto.AuthResponse.UserInfo;
import se.eplatform.cases.service.CaseAccessService;
import se.eplatform.common.security.CurrentUser;
import se.eplatform.common.validation.FileValidationService;
import se.eplatform.common.validation.FileValidationService.ValidationResult;
import se.eplatform.storage.api.dto.AttachmentDTO;
import se.eplatform.storage.domain.Attachment;
import se.eplatform.storage.service.FileStorageService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * File uploads and downloads.
 *
 * A file can be read by the user who uploaded it, by the owner of the case it
 * belongs to, and by staff. The acting user is always taken from the login.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final int MAX_URL_EXPIRY_MINUTES = 60;

    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;
    private final AuditService auditService;
    private final CaseAccessService caseAccess;

    public FileController(
            FileStorageService fileStorageService,
            FileValidationService fileValidationService,
            AuditService auditService,
            CaseAccessService caseAccess) {
        this.fileStorageService = fileStorageService;
        this.fileValidationService = fileValidationService;
        this.auditService = auditService;
        this.caseAccess = caseAccess;
    }

    /**
     * Upload a file.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caseId", required = false) UUID caseId,
            @RequestParam(value = "queryDefinitionId", required = false) UUID queryDefinitionId) {

        UUID userId = CurrentUser.requireId();
        if (caseId != null) {
            caseAccess.requireOwner(caseId);
        }

        // Validate file before upload
        ValidationResult validationResult = fileValidationService.validate(file);
        if (!validationResult.isValid()) {
            auditService.logFile(AuditAction.INVALID_INPUT, null,
                    "File upload rejected: " + validationResult.message() +
                    " (filename: " + file.getOriginalFilename() + ")");
            return ResponseEntity.badRequest().body(new ErrorResponse(validationResult.message()));
        }

        Attachment attachment = fileStorageService.uploadFile(file, userId, caseId, queryDefinitionId);

        auditService.logFile(AuditAction.FILE_UPLOAD, attachment.getId().toString(),
                "Uploaded file: " + attachment.getOriginalFilename() +
                " (" + formatBytes(attachment.getFileSize()) + ")");

        return ResponseEntity.ok(AttachmentDTO.from(attachment));
    }

    public record ErrorResponse(String error) {}

    /**
     * Download a file by attachment ID.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        Attachment attachment = requireReadable(id);

        InputStream inputStream = fileStorageService.downloadFile(id);

        auditService.logFile(AuditAction.FILE_DOWNLOAD, id.toString(),
                "Downloaded file: " + attachment.getOriginalFilename());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(attachment.getFileSize()))
                .body(new InputStreamResource(inputStream));
    }

    /**
     * Get attachment metadata.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AttachmentDTO> getAttachment(@PathVariable UUID id) {
        return ResponseEntity.ok(AttachmentDTO.from(requireReadable(id)));
    }

    /**
     * Get a pre-signed download URL for direct access.
     */
    @GetMapping("/{id}/url")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "60") int expiryMinutes) {

        requireReadable(id);
        expiryMinutes = Math.max(1, Math.min(expiryMinutes, MAX_URL_EXPIRY_MINUTES));
        String url = fileStorageService.generateDownloadUrl(id, expiryMinutes);
        return ResponseEntity.ok(new DownloadUrlResponse(url, expiryMinutes));
    }

    /**
     * Get all attachments for a case.
     */
    @GetMapping("/case/{caseId}")
    public List<AttachmentDTO> getAttachmentsForCase(@PathVariable UUID caseId) {
        caseAccess.requireRead(caseId);
        return fileStorageService.getAttachmentsForCase(caseId).stream()
                .map(AttachmentDTO::from)
                .toList();
    }

    /**
     * Get attachments for a specific field in a case.
     */
    @GetMapping("/case/{caseId}/field/{queryDefinitionId}")
    public List<AttachmentDTO> getAttachmentsForField(
            @PathVariable UUID caseId,
            @PathVariable UUID queryDefinitionId) {

        caseAccess.requireRead(caseId);
        return fileStorageService.getAttachmentsForField(caseId, queryDefinitionId).stream()
                .map(AttachmentDTO::from)
                .toList();
    }

    /**
     * Delete an attachment (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable UUID id) {
        UUID userId = CurrentUser.requireId();
        requireUploadedBy(id, userId);
        fileStorageService.deleteAttachment(id, userId);

        auditService.logFile(AuditAction.FILE_DELETE, id.toString(),
                "Deleted attachment by user: " + userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Link an attachment to a case.
     */
    @PostMapping("/{id}/link")
    public ResponseEntity<AttachmentDTO> linkToCase(
            @PathVariable UUID id,
            @RequestParam UUID caseId) {

        requireUploadedBy(id, CurrentUser.requireId());
        caseAccess.requireOwner(caseId);
        Attachment attachment = fileStorageService.linkToCase(id, caseId);
        return ResponseEntity.ok(AttachmentDTO.from(attachment));
    }

    /**
     * Get storage usage for a user.
     */
    @GetMapping("/usage/{userId}")
    public ResponseEntity<StorageUsageResponse> getStorageUsage(@PathVariable UUID userId) {
        UserInfo user = CurrentUser.require();
        if (!CurrentUser.isStaff(user) && !user.id().equals(userId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Du kan bara se din egen lagring");
        }
        long bytesUsed = fileStorageService.getStorageUsedByUser(userId);
        return ResponseEntity.ok(new StorageUsageResponse(bytesUsed, formatBytes(bytesUsed)));
    }

    // Access checks

    /**
     * Load an attachment the current user may read, or 404.
     */
    private Attachment requireReadable(UUID id) {
        UserInfo user = CurrentUser.require();
        return fileStorageService.getAttachment(id)
                .filter(a -> CurrentUser.isStaff(user)
                        || a.getUploadedBy().toString().equals(user.id())
                        || (a.getCaseId() != null && caseAccess.isOwner(a.getCaseId(), user)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Filen hittades inte"));
    }

    private void requireUploadedBy(UUID attachmentId, UUID userId) {
        fileStorageService.getAttachment(attachmentId)
                .filter(a -> a.getUploadedBy().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Filen hittades inte"));
    }

    // Response records

    public record DownloadUrlResponse(String url, int expiryMinutes) {}

    public record StorageUsageResponse(long bytesUsed, String formatted) {}

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
