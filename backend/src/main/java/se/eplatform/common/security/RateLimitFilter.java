package se.eplatform.common.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter to prevent API abuse.
 * Uses token bucket algorithm via Bucket4j.
 *
 * Limits are applied per IP address with different limits for:
 * - General API requests: 100 requests per minute
 * - Authentication endpoints: 10 requests per minute (stricter to prevent brute force)
 * - File uploads: 20 requests per minute
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Value("${eplatform.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${eplatform.security.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${eplatform.security.rate-limit.auth-requests-per-minute:10}")
    private int authRequestsPerMinute;

    @Value("${eplatform.security.rate-limit.upload-requests-per-minute:20}")
    private int uploadRequestsPerMinute;

    // Cache buckets per IP address
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> uploadBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String path = request.getServletPath();

        Bucket bucket = resolveBucket(clientIp, path);

        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            response.addHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "Too Many Requests",
                    "message": "Rate limit exceeded. Please try again later.",
                    "retryAfter": 60
                }
                """);
        }
    }

    private Bucket resolveBucket(String clientIp, String path) {
        if (isAuthEndpoint(path)) {
            return authBuckets.computeIfAbsent(clientIp, this::createAuthBucket);
        } else if (isUploadEndpoint(path)) {
            return uploadBuckets.computeIfAbsent(clientIp, this::createUploadBucket);
        } else {
            return generalBuckets.computeIfAbsent(clientIp, this::createGeneralBucket);
        }
    }

    private Bucket createGeneralBucket(String key) {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createAuthBucket(String key) {
        Bandwidth limit = Bandwidth.classic(authRequestsPerMinute,
                Refill.greedy(authRequestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createUploadBucket(String key) {
        Bandwidth limit = Bandwidth.classic(uploadRequestsPerMinute,
                Refill.greedy(uploadRequestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/public/auth");
    }

    private boolean isUploadEndpoint(String path) {
        return path.contains("/files") || path.contains("/upload") || path.contains("/attachments");
    }

    private String getClientIp(HttpServletRequest request) {
        // Check for proxy headers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Don't rate limit health checks and static resources
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.equals("/graphiql");
    }

    /**
     * Cleanup old buckets periodically to prevent memory leaks.
     * Called by a scheduled task.
     */
    public void cleanupOldBuckets() {
        // In production, you'd want to track last access time
        // and remove buckets that haven't been used in a while.
        // For now, we'll keep it simple.
        if (generalBuckets.size() > 10000) {
            generalBuckets.clear();
            log.info("Cleared general rate limit buckets");
        }
        if (authBuckets.size() > 10000) {
            authBuckets.clear();
            log.info("Cleared auth rate limit buckets");
        }
        if (uploadBuckets.size() > 10000) {
            uploadBuckets.clear();
            log.info("Cleared upload rate limit buckets");
        }
    }
}
