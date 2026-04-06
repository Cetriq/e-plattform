package se.eplatform.common.validation;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Service for validating uploaded files.
 * Uses Apache Tika for reliable MIME type detection based on file content,
 * not just file extension (which can be spoofed).
 */
@Service
public class FileValidationService {

    private static final Logger log = LoggerFactory.getLogger(FileValidationService.class);

    private final Tika tika = new Tika();

    @Value("${eplatform.upload.max-file-size:52428800}")  // 50MB default
    private long maxFileSize;

    @Value("${eplatform.upload.max-filename-length:255}")
    private int maxFilenameLength;

    /**
     * Allowed MIME types for general file uploads.
     * This is a whitelist approach - only allow known safe types.
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            // Documents
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "text/plain",
            "text/csv",
            "application/rtf",

            // Images
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/tiff",
            "image/svg+xml",

            // Archives (be careful with these - might want to scan contents)
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",

            // Other
            "application/xml",
            "text/xml"
    );

    /**
     * Dangerous file extensions that should never be allowed,
     * regardless of MIME type detection.
     */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".bat", ".cmd", ".com", ".msi", ".scr",
            ".js", ".vbs", ".wsf", ".wsh", ".ps1", ".psm1",
            ".jar", ".class", ".war",
            ".php", ".phtml", ".php3", ".php4", ".php5", ".phps",
            ".asp", ".aspx", ".cer", ".csr",
            ".htaccess", ".htpasswd",
            ".sh", ".bash", ".zsh", ".csh",
            ".py", ".pyc", ".pyo",
            ".rb", ".erb",
            ".pl", ".pm", ".cgi"
    );

    /**
     * Validate an uploaded file.
     * Returns a ValidationResult with success/failure and error message.
     */
    public ValidationResult validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ValidationResult.failure("Ingen fil uppladdad");
        }

        String filename = file.getOriginalFilename();

        // Check filename
        ValidationResult filenameResult = validateFilename(filename);
        if (!filenameResult.isValid()) {
            return filenameResult;
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            return ValidationResult.failure(
                    "Filen är för stor. Maximal filstorlek är " + (maxFileSize / 1024 / 1024) + " MB");
        }

        // Check extension against blocklist
        String extension = getExtension(filename).toLowerCase();
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            log.warn("Blocked file upload with dangerous extension: {}", filename);
            return ValidationResult.failure("Filtypen " + extension + " är inte tillåten");
        }

        // Detect actual MIME type from content
        try {
            String detectedMimeType = detectMimeType(file);

            if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
                log.warn("Blocked file upload with disallowed MIME type: {} (filename: {})",
                        detectedMimeType, filename);
                return ValidationResult.failure(
                        "Filtypen " + detectedMimeType + " är inte tillåten. " +
                        "Tillåtna typer: PDF, Word, Excel, bilder (JPG, PNG, GIF)");
            }

            // Check for MIME type mismatch (possible extension spoofing)
            String declaredMimeType = file.getContentType();
            if (declaredMimeType != null && !isMimeTypeCompatible(declaredMimeType, detectedMimeType)) {
                log.warn("MIME type mismatch: declared={}, detected={}, filename={}",
                        declaredMimeType, detectedMimeType, filename);
                // This is suspicious but we trust the detected type
            }

            return ValidationResult.success(detectedMimeType);

        } catch (IOException e) {
            log.error("Failed to detect MIME type for file: {}", filename, e);
            return ValidationResult.failure("Kunde inte validera filen");
        }
    }

    /**
     * Validate multiple files.
     */
    public ValidationResult validateAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ValidationResult.failure("Inga filer uppladdade");
        }

        for (MultipartFile file : files) {
            ValidationResult result = validate(file);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.success(null);
    }

    /**
     * Validate just the filename (without file content).
     */
    public ValidationResult validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return ValidationResult.failure("Filnamn saknas");
        }

        if (filename.length() > maxFilenameLength) {
            return ValidationResult.failure("Filnamnet är för långt (max " + maxFilenameLength + " tecken)");
        }

        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Path traversal attempt detected in filename: {}", filename);
            return ValidationResult.failure("Ogiltigt filnamn");
        }

        // Check for null bytes (can bypass some security checks)
        if (filename.contains("\0")) {
            log.warn("Null byte detected in filename: {}", filename);
            return ValidationResult.failure("Ogiltigt filnamn");
        }

        // Check for control characters
        if (filename.chars().anyMatch(c -> c < 32)) {
            log.warn("Control characters detected in filename: {}", filename);
            return ValidationResult.failure("Ogiltigt filnamn");
        }

        return ValidationResult.success(null);
    }

    /**
     * Detect MIME type from file content using Apache Tika.
     */
    private String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is, file.getOriginalFilename());
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }

    /**
     * Check if declared and detected MIME types are compatible.
     * Some variation is normal (e.g., text/plain vs application/octet-stream).
     */
    private boolean isMimeTypeCompatible(String declared, String detected) {
        if (declared.equals(detected)) return true;

        // Generic types are often declared for unknown content
        if (declared.equals("application/octet-stream")) return true;

        // Same base type is usually fine (e.g., image/jpeg vs image/jpg)
        String declaredBase = declared.split("/")[0];
        String detectedBase = detected.split("/")[0];
        return declaredBase.equals(detectedBase);
    }

    /**
     * Result of file validation.
     */
    public record ValidationResult(boolean valid, String message, String detectedMimeType) {

        public static ValidationResult success(String mimeType) {
            return new ValidationResult(true, null, mimeType);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, null);
        }

        public boolean isValid() {
            return valid;
        }
    }
}
