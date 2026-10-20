package se.eplatform.auth.service;

import se.eplatform.common.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final int retentionDays;

    public DemoCleanupJob(
            UserRepository userRepository,
            EntityManager entityManager,
            org.springframework.transaction.support.TransactionTemplate transactions,
            @Value("${eplatform.demo.retention-days:7}") int retentionDays) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${eplatform.demo.cleanup-cron:0 30 3 * * *}")
    public void removeExpiredDemoUsers() {
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        List<UUID> userIds = transactions.execute(status ->
                userRepository.findDemoUserIdsCreatedBefore(MockAuthService.DEMO_EMAIL_DOMAIN, cutoff));
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // Row level security shows the job no cases unless it names the
        // organisation, so the cases are removed one organisation at a time.
        // Child rows of cases (answers, events, messages, owners) are removed by
        // ON DELETE CASCADE; attachments are unlinked and cleaned up as orphans.
        int cases = 0;
        List<UUID> organizations = transactions.execute(status -> entityManager
                .createQuery("SELECT o.id FROM Organization o", UUID.class).getResultList());
        for (UUID organization : organizations) {
            Integer removed = TenantContext.forOrganization(organization, () -> transactions.execute(status ->
                    entityManager.createNativeQuery("DELETE FROM cases WHERE created_by IN (:ids) AND organization_id = :org")
                            .setParameter("ids", userIds)
                            .setParameter("org", organization)
                            .executeUpdate()));
            cases += removed == null ? 0 : removed;
        }
        Integer users = transactions.execute(status -> entityManager
                .createNativeQuery("DELETE FROM users WHERE id IN (:ids)")
                .setParameter("ids", userIds)
                .executeUpdate());

        log.info("Removed {} demo users and {} of their cases older than {} days", users, cases, retentionDays);
    }
}
