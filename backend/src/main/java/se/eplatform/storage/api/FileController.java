package se.eplatform.storage.api;

import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.web.AuditContext;
import se.eplatform.audit.web.Audited;
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
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * File uploads and downloads.
 *
 * A file can be read by the user who uploaded it, by the owner of the case it
 * belongs to, and by staff. The acting user is always taken from the login.
 */
@Tag(name = "Filer", description = "Uppladdning och nedladdning av bilagor")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final int MAX_URL_EXPIRY_MINUTES = 60;

    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;
    private final CaseAccessService caseAccess;

    public FileController(
            FileStorageService fileStorageService,
            FileValidationService fileValidationService,
            CaseAccessService caseAccess) {
        this.fileStorageService = fileStorageService;
        this.fileValidationService = fileValidationService;
        this.caseAccess = caseAccess;
    }

    /**
     * Upload a file.
     */
    @Audited(value = AuditAction.FILE_UPLOAD, entity = "FILE")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caseId", required = false) UUID caseId,
            @RequestParam(value = "queryDefinitionId", required = false) UUID queryDefinitionId) {

        UUID userId = CurrentUser.requireId();
        if (fileStorageService.isExternalStorage()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    "Filer laddas upp direkt till fillagringen i den här miljön"));
        }
        if (caseId != null) {
            caseAccess.requireOwner(caseId);
        }

        // Validate file before upload
        ValidationResult validationResult = fileValidationService.validate(file);
        if (!validationResult.isValid()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(validationResult.message()));
        }

        Attachment attachment = fileStorageService.uploadFile(file, userId, caseId, queryDefinitionId);
        AuditContext.entityId(attachment.getId());
        AuditContext.details(attachment.getContentType() + ", " + formatBytes(attachment.getFileSize()));


        return ResponseEntity.ok(AttachmentDTO.from(attachment));
    }

    public record ErrorResponse(String error) {}

    /**
     * Ask whether the logged-in user may upload files to a case. Used by the
     * frontend before it lets the browser upload straight to Vercel Blob.
     */
    @PostMapping("/upload-permission")
    public UploadPermission getUploadPermission(@RequestBody UploadPermissionRequest request) {
        if (request.caseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caseId krävs");
        }
        caseAccess.requireOwner(request.caseId());
        return new UploadPermission(
                blobPrefix(request.caseId()),
                FileStorageService.MAX_FILE_SIZE,
                List.copyOf(FileStorageService.ALLOWED_CONTENT_TYPES));
    }

    /**
     * Record a file that the browser uploaded to Vercel Blob.
     */
    @Audited(value = AuditAction.FILE_UPLOAD, entity = "FILE")
    @PostMapping("/register")
    public ResponseEntity<?> registerUploadedFile(@RequestBody RegisterFileRequest request) {
        UUID userId = CurrentUser.requireId();
        if (request.caseId() == null || request.pathname() == null || request.originalFilename() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pathname, originalFilename och caseId krävs");
        }
        caseAccess.requireOwner(request.caseId());

        if (!request.pathname().startsWith(blobPrefix(request.caseId())) || request.pathname().contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filen hör inte till ärendet");
        }
        if (request.contentType() == null || !FileStorageService.ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Filtypen är inte tillåten"));
        }
        if (request.fileSize() <= 0 || request.fileSize() > FileStorageService.MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Filen är för stor"));
        }

        Attachment attachment = fileStorageService.registerExternalFile(
                request.pathname(), request.originalFilename(), request.contentType(), request.fileSize(),
                userId, request.caseId(), request.queryDefinitionId());
        AuditContext.entityId(attachment.getId());
        AuditContext.details(attachment.getContentType() + ", " + formatBytes(attachment.getFileSize()));


        return ResponseEntity.ok(AttachmentDTO.from(attachment));
    }

    private static String blobPrefix(UUID caseId) {
        return "cases/" + caseId + "/";
    }

    public record UploadPermissionRequest(UUID caseId) {}

    public record UploadPermission(String pathnamePrefix, long maxFileSize, List<String> allowedContentTypes) {}

    public record RegisterFileRequest(
            String pathname,
            String originalFilename,
            String contentType,
            long fileSize,
            UUID caseId,
            UUID queryDefinitionId) {}

    /**
     * Download a file by attachment ID.
     */
    @Audited(value = AuditAction.FILE_DOWNLOAD, entity = "FILE")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        Attachment attachment = requireReadable(id);
        if (FileStorageService.VERCEL_BLOB_BUCKET.equals(attachment.getBucket())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Filen hämtas via " + AttachmentDTO.from(attachment).downloadUrl());
        }

        InputStream inputStream = fileStorageService.downloadFile(id);


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
    @Audited(value = AuditAction.FILE_VIEW, entity = "FILE")
    @GetMapping("/{id}")
    public ResponseEntity<AttachmentDTO> getAttachment(@PathVariable UUID id) {
        return ResponseEntity.ok(AttachmentDTO.from(requireReadable(id)));
    }

    /**
     * Get a pre-signed download URL for direct access.
     */
    @Audited(value = AuditAction.FILE_DOWNLOAD, entity = "FILE")
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
    @Audited(value = AuditAction.FILE_VIEW, entity = "CASE", idParam = "caseId")
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
    @Audited(value = AuditAction.FILE_VIEW, entity = "CASE", idParam = "caseId")
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
    @Audited(value = AuditAction.FILE_DELETE, entity = "FILE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable UUID id) {
        UUID userId = CurrentUser.requireId();
        requireUploadedBy(id, userId);
        fileStorageService.deleteAttachment(id, userId);


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
