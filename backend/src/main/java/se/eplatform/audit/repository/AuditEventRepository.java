package se.eplatform.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.audit.domain.AuditAction;
import se.eplatform.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Find audit events by user ID.
     */
    Page<AuditEvent> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Find audit events by action type.
     */
    Page<AuditEvent> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    /**
     * Find audit events for a specific entity.
     */
    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType, String entityId, Pageable pageable);

    /**
     * Find audit events within a time range.
     */
    Page<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(
            Instant start, Instant end, Pageable pageable);

    /**
     * Find recent failed login attempts for an IP address.
     */
    @Query("SELECT COUNT(e) FROM AuditEvent e WHERE e.ipAddress = :ip " +
           "AND e.action = 'LOGIN_FAILURE' AND e.timestamp > :since")
    long countFailedLoginsByIpSince(@Param("ip") String ipAddress, @Param("since") Instant since);

    /**
     * Find recent rate limit exceeded events for an IP address.
     */
    @Query("SELECT COUNT(e) FROM AuditEvent e WHERE e.ipAddress = :ip " +
           "AND e.action = 'RATE_LIMIT_EXCEEDED' AND e.timestamp > :since")
    long countRateLimitExceededByIpSince(@Param("ip") String ipAddress, @Param("since") Instant since);

    /**
     * Get action counts for statistics.
     */
    @Query("SELECT e.action, COUNT(e) FROM AuditEvent e " +
           "WHERE e.timestamp > :since GROUP BY e.action")
    List<Object[]> countActionsSince(@Param("since") Instant since);

    /**
     * Get user activity counts for statistics.
     */
    @Query("SELECT e.userId, COUNT(e) FROM AuditEvent e " +
           "WHERE e.timestamp > :since GROUP BY e.userId ORDER BY COUNT(e) DESC")
    List<Object[]> countUserActivitySince(@Param("since") Instant since, Pageable pageable);

    /**
     * Delete old audit events (for data retention policy).
     */
    void deleteByTimestampBefore(Instant before);
}
