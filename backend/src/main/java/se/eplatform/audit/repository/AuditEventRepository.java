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
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    /**
     * Hash the next entry of a chain is linked to: the chain's newest entry, or,
     * when gallring has removed all of them, the last removed one recorded in
     * the chain's newest anchor.
     */
    @Query(value = """
            SELECT COALESCE(
                (SELECT hash FROM audit_events WHERE chain_key = :chain ORDER BY seq DESC LIMIT 1),
                (SELECT last_removed_hash FROM audit_chain_anchors WHERE chain_key = :chain ORDER BY id DESC LIMIT 1))
            """, nativeQuery = true)
    Optional<String> findLatestHash(@Param("chain") String chainKey);

    /** Entries in chain order, for verification. */
    Slice<AuditEvent> findByChainKeyAndSeqGreaterThanOrderBySeqAsc(String chainKey, long seq, Pageable pageable);
}
