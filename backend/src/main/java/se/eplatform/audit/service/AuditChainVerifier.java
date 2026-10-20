package se.eplatform.audit.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.audit.repository.AuditEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks that no entry in the traceability log has been changed, removed or
 * inserted after the fact, by recomputing the hash chain.
 *
 * Gallring removes entries from the start of the chain. Before it does, it
 * writes an anchor (audit_chain_anchors) holding the hash of the last removed
 * entry, so the first kept entry must link to the newest anchor, or to the
 * genesis hash when nothing has been removed. The anchors form their own
 * chain and are verified first.
 *
 * Each organisation, the platform, and the entries written before chains per
 * organisation ("global") form separate chains, verified one at a time.
 */
@Service
public class AuditChainVerifier {

    private static final int BATCH = 500;

    private final AuditEventRepository repository;
    private final JdbcTemplate jdbc;

    public AuditChainVerifier(AuditEventRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /**
     * @param chain             the chain checked
     * @param verified          number of entries checked
     * @param intact            true when every anchor and entry matches its hash and links to the one before
     * @param firstBrokenSeq    the first entry that doesn't, or null
     * @param anchors           number of gallring anchors checked
     * @param firstBrokenAnchor the first anchor that doesn't, or null
     */
    public record Result(String chain, long verified, boolean intact, Long firstBrokenSeq, long anchors, Long firstBrokenAnchor) {}

    record Anchor(long id, String chainKey, String kind, Long seqFrom, Long seqTo, int rowCount,
                  String lastRemovedHash, String rule, Instant createdAt, String prevHash, String hash) {}

    @Transactional(readOnly = true)
    public Result verify(String chain) {
        List<Anchor> anchors = jdbc.query("""
                SELECT id, chain_key, kind, seq_from, seq_to, row_count, last_removed_hash, rule,
                       created_at, prev_hash, hash
                FROM audit_chain_anchors WHERE chain_key = ? ORDER BY id
                """, (rs, i) -> new Anchor(rs.getLong("id"), rs.getString("chain_key"), rs.getString("kind"),
                rs.getObject("seq_from", Long.class), rs.getObject("seq_to", Long.class), rs.getInt("row_count"),
                rs.getString("last_removed_hash"), rs.getString("rule"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("prev_hash"), rs.getString("hash")), chain);

        String expectedAnchorPrev = AuditService.GENESIS;
        for (Anchor anchor : anchors) {
            if (!expectedAnchorPrev.equals(anchor.prevHash()) || !anchor.hash().equals(hash(anchor))) {
                return new Result(chain, 0, false, null, anchors.size(), anchor.id());
            }
            expectedAnchorPrev = anchor.hash();
        }

        long verified = 0;
        long lastSeq = 0;
        String expectedPrev = anchors.isEmpty() ? AuditService.GENESIS : anchors.getLast().lastRemovedHash();

        Slice<AuditEvent> slice;
        do {
            slice = repository.findByChainKeyAndSeqGreaterThanOrderBySeqAsc(chain, lastSeq, PageRequest.of(0, BATCH));
            for (AuditEvent event : slice) {
                boolean linked = expectedPrev.equals(event.getPrevHash());
                boolean unchanged = event.getHash() != null
                        && event.getHash().equals(AuditService.hash(event.getPrevHash(), event));
                if (!linked || !unchanged) {
                    return new Result(chain, verified, false, event.getSeq(), anchors.size(), null);
                }
                expectedPrev = event.getHash();
                lastSeq = event.getSeq();
                verified++;
            }
        } while (slice.hasNext());

        return new Result(chain, verified, true, null, anchors.size(), null);
    }

    /** Same as the database function audit_anchor_hash (V9). */
    static String hash(Anchor a) {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, a.createdAt());
        String canonical = String.join("|",
                Long.toString(a.id()), a.chainKey(), a.kind(),
                a.seqFrom() == null ? "" : a.seqFrom().toString(),
                a.seqTo() == null ? "" : a.seqTo().toString(),
                Integer.toString(a.rowCount()), a.lastRemovedHash(),
                a.rule() == null ? "" : a.rule(),
                Long.toString(micros));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(a.prevHash().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
