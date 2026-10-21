package se.eplatform.flow.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import se.eplatform.contract.ContractService;
import se.eplatform.flow.domain.Flow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores the published documents of a version (contract, projection and the
 * manifest binding them) and checks them later. The documents are only ever
 * added; the database refuses changes.
 */
@Service
public class PublicationService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ContractCanonicalizer canonicalizer;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicationService(ContractCanonicalizer canonicalizer, JdbcTemplate jdbc) {
        this.canonicalizer = canonicalizer;
        this.jdbc = jdbc;
    }

    /** The documents of a version that does not apply a service application (backfill). */
    public String publish(Flow flow, Instant publishedAt, UUID publishedBy, String origin) {
        if (flow.getApplicationVersionId() != null) {
            throw new IllegalStateException("Version " + flow.getId() + " applies a service application; publish it with its binding");
        }
        return publish(flow, publishedAt, publishedBy, origin, null);
    }

    /**
     * Store the documents of a version and return the manifest's hash. Runs in
     * the caller's transaction, so a failed publication leaves nothing behind.
     *
     * @param binding the service application version the version applies, or null
     */
    public String publish(Flow flow, Instant publishedAt, UUID publishedBy, String origin, ContractService.Binding binding) {
        var contract = canonicalizer.contract(flow);
        var projection = canonicalizer.projection(flow);
        var manifest = canonicalizer.manifest(flow, contract, projection, publishedAt, publishedBy, origin, binding);
        jdbc.update("INSERT INTO contract_snapshots (hash, organization_id, format, canonical) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (hash) DO NOTHING",
                contract.hash(), flow.getOrganizationId(), contract.format(), contract.canonical());
        jdbc.update("INSERT INTO projection_snapshots (hash, organization_id, format, canonical) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (hash) DO NOTHING",
                projection.hash(), flow.getOrganizationId(), projection.format(), projection.canonical());
        jdbc.update("INSERT INTO publication_manifests (hash, organization_id, flow_id, contract_hash, projection_hash, " +
                    "format, canonical, definition_version_id, application_version_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (hash) DO NOTHING",
                manifest.hash(), flow.getOrganizationId(), flow.getId(), contract.hash(), projection.hash(),
                manifest.format(), manifest.canonical(),
                binding == null ? null : binding.definitionVersionId(), binding == null ? null : binding.applicationVersionId());
        return manifest.hash();
    }

    /**
     * What a case was created on, and whether it still checks out.
     *
     * @param manifestIntact   the stored manifest hashes to its own hash
     * @param documentsIntact  the stored contract and projection hash to the hashes the manifest names
     * @param versionUnchanged the version's rows today still give the same contract and projection
     */
    public record Verification(String manifestHash, Map<String, Object> manifest, Map<String, Object> contract,
                               Map<String, Object> projection, boolean manifestIntact, boolean documentsIntact,
                               boolean versionUnchanged) {
        public boolean intact() {
            return manifestIntact && documentsIntact && versionUnchanged;
        }
    }

    /** Must run in a transaction with the version's parts loadable. */
    public Verification verify(String manifestHash, Flow flow) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT canonical, contract_hash, projection_hash FROM publication_manifests WHERE hash = ?", manifestHash);
        String manifestText = (String) row.get("canonical");
        String contractHash = (String) row.get("contract_hash");
        String projectionHash = (String) row.get("projection_hash");
        Map<String, Object> manifest = parse(manifestText);

        String contractText = single("SELECT canonical FROM contract_snapshots WHERE hash = ?", contractHash);
        String projectionText = single("SELECT canonical FROM projection_snapshots WHERE hash = ?", projectionHash);

        boolean manifestIntact = manifestHash.equals(ContractCanonicalizer.hashOf(manifestText))
                && contractHash.equals(manifest.get("contractHash"))
                && projectionHash.equals(manifest.get("projectionHash"))
                && String.valueOf(flow.getId()).equals(manifest.get("flowId"));
        boolean documentsIntact = contractHash.equals(ContractCanonicalizer.hashOf(contractText))
                && projectionHash.equals(ContractCanonicalizer.hashOf(projectionText));
        boolean versionUnchanged = contractHash.equals(canonicalizer.contract(flow).hash())
                && projectionHash.equals(canonicalizer.projection(flow).hash());

        return new Verification(manifestHash, manifest, parse(contractText), parse(projectionText),
                manifestIntact, documentsIntact, versionUnchanged);
    }

    private String single(String sql, String hash) {
        List<String> rows = jdbc.queryForList(sql, String.class, hash);
        return rows.isEmpty() ? "" : rows.get(0);
    }

    private Map<String, Object> parse(String canonical) {
        try {
            return canonical.isEmpty() ? Map.of() : objectMapper.readValue(canonical, MAP);
        } catch (java.io.IOException e) {
            return Map.of();
        }
    }
}
