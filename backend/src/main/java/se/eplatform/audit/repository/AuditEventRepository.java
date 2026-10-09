package se.eplatform.audit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import se.eplatform.audit.domain.AuditEvent;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    /** Hash of the newest entry, which the next entry is chained to. */
    @Query(value = "SELECT hash FROM audit_events ORDER BY seq DESC LIMIT 1", nativeQuery = true)
    Optional<String> findLatestHash();

    /** Entries in chain order, for verification. */
    Slice<AuditEvent> findBySeqGreaterThanOrderBySeqAsc(long seq, Pageable pageable);
}
