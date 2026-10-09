package se.eplatform.auth.service;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Removes the citizen accounts created for demo visitors, together with
 * their cases, once they are older than the retention period.
 */
@Component
public class DemoCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DemoCleanupJob.class);

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final int retentionDays;

    public DemoCleanupJob(
            UserRepository userRepository,
            EntityManager entityManager,
            @Value("${eplatform.demo.retention-days:7}") int retentionDays) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${eplatform.demo.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void removeExpiredDemoUsers() {
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        List<UUID> userIds = userRepository.findDemoUserIdsCreatedBefore(MockAuthService.DEMO_EMAIL_DOMAIN, cutoff);
        if (userIds.isEmpty()) {
            return;
        }

        // Child rows of cases (answers, events, messages, owners) are removed by
        // ON DELETE CASCADE; attachments are unlinked and cleaned up as orphans.
        int cases = entityManager.createNativeQuery("DELETE FROM cases WHERE created_by IN (:ids)")
                .setParameter("ids", userIds)
                .executeUpdate();
        int users = entityManager.createNativeQuery("DELETE FROM users WHERE id IN (:ids)")
                .setParameter("ids", userIds)
                .executeUpdate();

        log.info("Removed {} demo users and {} of their cases older than {} days", users, cases, retentionDays);
    }
}
