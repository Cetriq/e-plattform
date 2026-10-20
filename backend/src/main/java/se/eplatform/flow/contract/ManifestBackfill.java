package se.eplatform.flow.contract;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import se.eplatform.common.tenant.TenantContext;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.repository.FlowRepository;

import java.util.List;
import java.util.UUID;

/**
 * Gives versions published before manifests existed their manifest (origin
 * "backfill"), and binds their cases to it (marked manifest_backfilled).
 * Runs when the application starts; does nothing once everything has one.
 *
 * A backfilled manifest describes the version as it is now. It was locked
 * when published versions became immutable (V15), but could have been
 * changed before that, which is why such cases are marked.
 */
@Component
public class ManifestBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManifestBackfill.class);

    private final FlowRepository flowRepository;
    private final PublicationService publicationService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    public ManifestBackfill(FlowRepository flowRepository, PublicationService publicationService,
                            EntityManager entityManager, TransactionTemplate transactions) {
        this.flowRepository = flowRepository;
        this.publicationService = publicationService;
        this.entityManager = entityManager;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill();
        } catch (RuntimeException e) {
            log.error("Manifest backfill failed", e);
        }
    }

    public void backfill() {
        List<UUID> versions = transactions.execute(status -> entityManager.createQuery(
                "SELECT f.id FROM Flow f WHERE f.status <> se.eplatform.flow.domain.FlowStatus.DRAFT " +
                "AND f.manifestHash IS NULL", UUID.class).getResultList());
        if (versions == null) versions = List.of();
        for (UUID id : versions) {
            transactions.executeWithoutResult(status -> {
                Flow flow = flowRepository.findById(id).orElseThrow();
                flow.setManifestHash(publicationService.publish(flow, flow.getPublishDate(), null, "backfill"));
                flowRepository.save(flow);
            });
        }

        // Cases are behind row level security: one organisation at a time
        List<UUID> organizations = transactions.execute(status -> entityManager
                .createQuery("SELECT o.id FROM Organization o", UUID.class).getResultList());
        int cases = 0;
        for (UUID organization : organizations == null ? List.<UUID>of() : organizations) {
            Integer bound = TenantContext.forOrganization(organization, () -> transactions.execute(status ->
                    entityManager.createNativeQuery("""
                            UPDATE cases c SET manifest_hash = f.manifest_hash, manifest_backfilled = true
                            FROM flows f
                            WHERE c.flow_id = f.id AND c.manifest_hash IS NULL AND f.manifest_hash IS NOT NULL
                              AND c.organization_id = :org""")
                            .setParameter("org", organization)
                            .executeUpdate()));
            cases += bound == null ? 0 : bound;
        }
        if (!versions.isEmpty() || cases > 0) {
            log.info("Backfilled manifests for {} versions and bound {} cases", versions.size(), cases);
        }
    }
}
