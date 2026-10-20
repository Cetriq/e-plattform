-- =====================================================
-- Publiceringsmanifest (fas 0, steg 2b)
-- =====================================================
-- When a version of an e-service is published, three canonical documents are
-- stored (RFC 8785, hashed with SHA-256):
--
--   contract_snapshots     the rules: fields and their validation, conditions,
--                          statuses and transitions (eplatform.contract/1)
--   projection_snapshots   what the citizen sees: steps, texts, help texts
--                          (eplatform.projection/1)
--   publication_manifests  binds the two to the version: e-service, version,
--                          organisation, both hashes, when and by whom
--                          (eplatform.manifest/1)
--
-- Every case is bound to the manifest of the version it was created on, so it
-- can be shown exactly which rules applied and which questions were asked.
-- The documents are only ever added.

CREATE TABLE contract_snapshots (
    hash            VARCHAR(64) PRIMARY KEY,
    organization_id UUID        REFERENCES organizations(id),
    format          VARCHAR(40) NOT NULL,
    canonical       TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE projection_snapshots (
    hash            VARCHAR(64) PRIMARY KEY,
    organization_id UUID        REFERENCES organizations(id),
    format          VARCHAR(40) NOT NULL,
    canonical       TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE publication_manifests (
    hash            VARCHAR(64) PRIMARY KEY,
    organization_id UUID        REFERENCES organizations(id),
    flow_id         UUID        NOT NULL REFERENCES flows(id),
    contract_hash   VARCHAR(64) NOT NULL REFERENCES contract_snapshots(hash),
    projection_hash VARCHAR(64) NOT NULL REFERENCES projection_snapshots(hash),
    format          VARCHAR(40) NOT NULL,
    canonical       TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_manifests_flow ON publication_manifests(flow_id);

CREATE FUNCTION snapshots_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contract_snapshots_append_only BEFORE UPDATE OR DELETE ON contract_snapshots
    FOR EACH ROW EXECUTE FUNCTION snapshots_append_only();
CREATE TRIGGER trg_projection_snapshots_append_only BEFORE UPDATE OR DELETE ON projection_snapshots
    FOR EACH ROW EXECUTE FUNCTION snapshots_append_only();
CREATE TRIGGER trg_publication_manifests_append_only BEFORE UPDATE OR DELETE ON publication_manifests
    FOR EACH ROW EXECUTE FUNCTION snapshots_append_only();

REVOKE UPDATE, DELETE ON contract_snapshots, projection_snapshots, publication_manifests FROM eplatform_app;

-- ---------- Versions and cases point at their manifest ----------
ALTER TABLE flows ADD COLUMN manifest_hash VARCHAR(64) REFERENCES publication_manifests(hash);

ALTER TABLE cases
    ADD COLUMN manifest_hash VARCHAR(64) REFERENCES publication_manifests(hash),
    -- Bound after the fact to the manifest of its version, written when
    -- manifests were introduced; the version may have changed before it was locked
    ADD COLUMN manifest_backfilled BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_cases_manifest ON cases(manifest_hash);

-- A version's manifest is set once, when it is published (or afterwards for
-- versions published before manifests existed), and never changed.
CREATE OR REPLACE FUNCTION flows_locked_when_published() RETURNS trigger AS $$
DECLARE
    unchanged CONSTANT TEXT[] := ARRAY['status', 'enabled', 'unpublish_date', 'updated_at', 'manifest_hash'];
BEGIN
    IF OLD.status = 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Version % of e-service % is % and cannot be deleted', OLD.version, OLD.family_id, OLD.status;
    END IF;
    IF (to_jsonb(NEW) - unchanged) IS DISTINCT FROM (to_jsonb(OLD) - unchanged) THEN
        RAISE EXCEPTION 'Version % of e-service % is % and cannot be changed', OLD.version, OLD.family_id, OLD.status;
    END IF;
    IF OLD.manifest_hash IS NOT NULL AND NEW.manifest_hash IS DISTINCT FROM OLD.manifest_hash THEN
        RAISE EXCEPTION 'The manifest of version % of e-service % cannot be changed', OLD.version, OLD.family_id;
    END IF;
    IF NEW.status <> OLD.status AND NOT (
           (OLD.status = 'PUBLISHED' AND NEW.status IN ('SUPERSEDED', 'ARCHIVED'))
        OR (OLD.status = 'SUPERSEDED' AND NEW.status = 'ARCHIVED')) THEN
        RAISE EXCEPTION 'Status % cannot change to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- A case's manifest is set once and never changed
CREATE FUNCTION cases_manifest_fixed() RETURNS trigger AS $$
BEGIN
    IF OLD.manifest_hash IS NOT NULL AND NEW.manifest_hash IS DISTINCT FROM OLD.manifest_hash THEN
        RAISE EXCEPTION 'The manifest of a case cannot be changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cases_manifest_fixed BEFORE UPDATE OF manifest_hash ON cases
    FOR EACH ROW EXECUTE FUNCTION cases_manifest_fixed();
