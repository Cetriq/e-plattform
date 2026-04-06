package se.eplatform.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.audit.repository.AuditEventRepository;
import se.eplatform.auth.dto.AuthResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service for recording audit events.
 * All audit logging is done asynchronously to not impact request performance.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditRepository;

    public AuditService(AuditEventRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Log an audit event with automatic user and request context extraction.
     */
    @Async
    public void log(AuditAction action, String entityType, String entityId, String details) {
        try {
            AuditEvent.Builder builder = AuditEvent.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(sanitizeDetails(details));

            // Extract user info from security context
            extractUserInfo(builder);

            // Extract request info
            extractRequestInfo(builder);

            AuditEvent event = builder.build();
            auditRepository.save(event);

            log.debug("Audit: {} {} {} by {}",
                    action, entityType, entityId, event.getUserId());
        } catch (Exception e) {
            // Never let audit logging fail the main request
            log.error("Failed to log audit event: {} {} {}", action, entityType, entityId, e);
        }
    }

    /**
     * Log an audit event with just an action.
     */
    @Async
    public void log(AuditAction action) {
        log(action, null, null, null);
    }

    /**
     * Log an audit event with action and details.
     */
    @Async
    public void log(AuditAction action, String details) {
        log(action, null, null, details);
    }

    /**
     * Log a case-related audit event.
     */
    @Async
    public void logCase(AuditAction action, String caseId, String details) {
        log(action, "Case", caseId, details);
    }

    /**
     * Log a flow-related audit event.
     */
    @Async
    public void logFlow(AuditAction action, String flowId, String details) {
        log(action, "Flow", flowId, details);
    }

    /**
     * Log a file-related audit event.
     */
    @Async
    public void logFile(AuditAction action, String fileId, String details) {
        log(action, "File", fileId, details);
    }

    /**
     * Log a security-related audit event (login, unauthorized access, etc.)
     */
    @Async
    public void logSecurity(AuditAction action, String userId, String ipAddress, String details) {
        try {
            AuditEvent.Builder builder = AuditEvent.builder()
                    .action(action)
                    .userId(userId != null ? userId : "anonymous")
                    .ipAddress(ipAddress)
                    .details(sanitizeDetails(details));

            extractRequestInfo(builder);

            AuditEvent event = builder.build();
            auditRepository.save(event);

            log.info("Security audit: {} for user {} from IP {}",
                    action, userId, ipAddress);
        } catch (Exception e) {
            log.error("Failed to log security audit event: {} {}", action, userId, e);
        }
    }

    /**
     * Check if an IP has too many failed login attempts (for blocking).
     */
    public boolean hasExcessiveFailedLogins(String ipAddress, int maxAttempts, int withinMinutes) {
        Instant since = Instant.now().minus(withinMinutes, ChronoUnit.MINUTES);
        long count = auditRepository.countFailedLoginsByIpSince(ipAddress, since);
        return count >= maxAttempts;
    }

    /**
     * Check if an IP has hit rate limits too many times.
     */
    public boolean hasExcessiveRateLimitHits(String ipAddress, int maxHits, int withinMinutes) {
        Instant since = Instant.now().minus(withinMinutes, ChronoUnit.MINUTES);
        long count = auditRepository.countRateLimitExceededByIpSince(ipAddress, since);
        return count >= maxHits;
    }

    private void extractUserInfo(AuditEvent.Builder builder) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthResponse.UserInfo user) {
            builder.userId(user.id())
                    .userEmail(user.email())
                    .userName(user.displayName());
        } else {
            builder.userId("anonymous");
        }
    }

    private void extractRequestInfo(AuditEvent.Builder builder) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                builder.ipAddress(getClientIp(request))
                        .userAgent(truncate(request.getHeader("User-Agent"), 500))
                        .requestPath(request.getRequestURI())
                        .requestMethod(request.getMethod());
            }
        } catch (Exception e) {
            // Request context might not be available in async context
            log.trace("Could not extract request info: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Sanitize details to prevent PII leaks in logs.
     * Removes or masks sensitive data.
     */
    private String sanitizeDetails(String details) {
        if (details == null) return null;

        // Mask personnummer (Swedish personal ID)
        details = details.replaceAll("\\d{6,8}[-+]?\\d{4}", "******-****");

        // Mask email addresses partially
        details = details.replaceAll(
                "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})",
                "***@$2");

        // Mask potential credit card numbers
        details = details.replaceAll("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}", "****-****-****-****");

        return truncate(details, 2000);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
