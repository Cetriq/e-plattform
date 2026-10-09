package se.eplatform.audit.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.audit.repository.AuditEventRepository;

/**
 * Checks that no entry in the traceability log has been changed, removed or
 * inserted after the fact, by recomputing the hash chain.
 */
@Service
public class AuditChainVerifier {

    private static final int BATCH = 500;

    private final AuditEventRepository repository;

    public AuditChainVerifier(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * @param verified        number of entries checked
     * @param intact          true when every entry matches its hash and links to the one before
     * @param firstBrokenSeq  the first entry that doesn't, or null
     */
    public record Result(long verified, boolean intact, Long firstBrokenSeq) {}

    @Transactional(readOnly = true)
    public Result verify() {
        long verified = 0;
        long lastSeq = 0;
        String expectedPrev = null; // the oldest kept entry anchors the chain (older ones may be gallrade)

        Slice<AuditEvent> slice;
        do {
            slice = repository.findBySeqGreaterThanOrderBySeqAsc(lastSeq, PageRequest.of(0, BATCH));
            for (AuditEvent event : slice) {
                boolean linked = expectedPrev == null || expectedPrev.equals(event.getPrevHash());
                boolean unchanged = event.getHash() != null
                        && event.getHash().equals(AuditService.hash(event.getPrevHash(), event));
                if (!linked || !unchanged) {
                    return new Result(verified, false, event.getSeq());
                }
                expectedPrev = event.getHash();
                lastSeq = event.getSeq();
                verified++;
            }
        } while (slice.hasNext());

        return new Result(verified, true, null);
    }
}
