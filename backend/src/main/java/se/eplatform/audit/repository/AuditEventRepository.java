package se.eplatform.audit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.audit.domain.AuditEvent;

import java.util.Optional;
import java.util.UUID;

@Repository
/**
 * Declared query methods run in a read-only transaction, so the row level
 * security context (TenantContextBinder) is always set when they query.
 */
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    /**
     * Hash the next entry of a chain is linked to: the chain's newest entry, or,
     * when gallring has removed all of them, the last removed one recorded in
     * the chain's newest anchor.
     */
    // A database function, because a writer may not be allowed to read the chain (RLS, V14)
    @Query(value = "SELECT audit_latest_hash(:chain)", nativeQuery = true)
    Optional<String> findLatestHash(@Param("chain") String chainKey);

    /** Entries in chain order, for verification. */
    Slice<AuditEvent> findByChainKeyAndSeqGreaterThanOrderBySeqAsc(String chainKey, long seq, Pageable pageable);
}
