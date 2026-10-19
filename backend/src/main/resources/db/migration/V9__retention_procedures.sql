-- =====================================================
-- Gallring genom kontrollerade procedurer
-- =====================================================
-- Until now the append-only trigger on audit_events let any connection delete
-- entries after setting eplatform.audit_retention. Gallring now runs only
-- through SECURITY DEFINER procedures in the schema "retention":
--
--   eplatform_retention_owner  NOLOGIN. Owns the procedures; the only role the
--                              append-only triggers let delete.
--   eplatform_retention        Runs the procedures. Has no other privileges.
--                              LOGIN and password are set outside migrations.
--
-- The retention periods live in retention_rules and the procedures read them
-- themselves, so a caller can never ask for a shorter period. Every removal of
-- log entries writes an anchor first, so the chain stays verifiable.
--
-- Note: as long as the application connects as the role that owns the tables
-- it can still drop or disable triggers. That is closed when the application
-- gets its own role (fas 0, steg 1, RLS).

DO $$
BEGIN
    -- Lets a non-superuser migration role (Neon) assign ownership to the roles
    -- it creates. Ignored for superusers.
    PERFORM set_config('createrole_self_grant', 'set, inherit', true);
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eplatform_retention_owner') THEN
        CREATE ROLE eplatform_retention_owner NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eplatform_retention') THEN
        CREATE ROLE eplatform_retention NOLOGIN;
    END IF;
END
$$;

-- ---------- Rules ----------
CREATE TABLE retention_rules (
    data_type     VARCHAR(40) PRIMARY KEY,
    keep_interval INTERVAL    NOT NULL,
    description   TEXT        NOT NULL
);

INSERT INTO retention_rules (data_type, keep_interval, description) VALUES
    ('audit_events',  INTERVAL '24 months', 'Spårbarhetsloggen sparas i 24 månader'),
    ('system_events', INTERVAL '90 days',   'Den tekniska systemloggen sparas i 90 dagar'),
    ('draft_cases',   INTERVAL '90 days',   'Utkast som aldrig skickats in tas bort efter 90 dagar');
-- Closed cases follow the gallringsfrist of their e-service (flows.retention_months).

COMMENT ON TABLE retention_rules IS
    'Gallringsregler. Ändras bara genom migreringar; procedurerna i schemat retention läser dem.';

-- ---------- Anchors ----------
-- Before entries are removed from the start of the chain, an anchor records
-- the hash of the last removed entry. The first remaining entry links to it.
-- Anchors form their own hash chain and are never removed.
CREATE TABLE audit_chain_anchors (
    id                BIGINT       PRIMARY KEY,
    chain_key         VARCHAR(80)  NOT NULL DEFAULT 'global',
    kind              VARCHAR(20)  NOT NULL CHECK (kind IN ('BASELINE', 'RETENTION')),
    seq_from          BIGINT,
    seq_to            BIGINT,
    row_count         INTEGER      NOT NULL,
    last_removed_hash VARCHAR(64)  NOT NULL,
    rule              VARCHAR(80),
    created_at        TIMESTAMPTZ  NOT NULL,
    prev_hash         VARCHAR(64)  NOT NULL,
    hash              VARCHAR(64)  NOT NULL
);

CREATE SEQUENCE audit_chain_anchors_id_seq OWNED BY audit_chain_anchors.id;
CREATE INDEX idx_anchor_chain ON audit_chain_anchors(chain_key, id DESC);

-- The hash of an anchor. AuditChainVerifier computes the same in Java.
CREATE FUNCTION audit_anchor_hash(prev_hash TEXT, id BIGINT, chain_key TEXT, kind TEXT,
                                  seq_from BIGINT, seq_to BIGINT, row_count INTEGER,
                                  last_removed_hash TEXT, rule TEXT, created_at TIMESTAMPTZ)
    RETURNS VARCHAR(64) LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(sha256(convert_to(prev_hash || E'\n' || concat_ws('|',
        id::text, chain_key, kind, coalesce(seq_from::text, ''), coalesce(seq_to::text, ''),
        row_count::text, last_removed_hash, coalesce(rule, ''),
        (extract(epoch FROM created_at) * 1000000)::bigint::text), 'UTF8')), 'hex')
$$;

CREATE FUNCTION audit_chain_anchors_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_chain_anchors is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_anchor_append_only
    BEFORE UPDATE OR DELETE ON audit_chain_anchors
    FOR EACH ROW EXECUTE FUNCTION audit_chain_anchors_append_only();

-- If the chain does not start at the genesis hash (entries were removed before
-- this migration), record where it starts now.
DO $$
DECLARE
    first_prev VARCHAR(64);
    new_id     BIGINT;
    created    TIMESTAMPTZ := date_trunc('milliseconds', now());
    genesis    CONSTANT VARCHAR(64) := repeat('0', 64);
BEGIN
    SELECT prev_hash INTO first_prev FROM audit_events WHERE hash IS NOT NULL ORDER BY seq LIMIT 1;
    IF first_prev IS NOT NULL AND first_prev <> genesis THEN
        new_id := nextval('audit_chain_anchors_id_seq');
        INSERT INTO audit_chain_anchors (id, chain_key, kind, row_count, last_removed_hash, rule,
                                         created_at, prev_hash, hash)
        VALUES (new_id, 'global', 'BASELINE', 0, first_prev, 'V9 baseline', created, genesis,
                audit_anchor_hash(genesis, new_id, 'global', 'BASELINE', NULL, NULL, 0,
                                  first_prev, 'V9 baseline', created));
    END IF;
END
$$;

-- ---------- Append-only triggers ----------
-- Only the retention procedures (running as eplatform_retention_owner) may
-- delete. The old session setting no longer has any effect.
CREATE OR REPLACE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_user = 'eplatform_retention_owner' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

-- ---------- Procedures ----------
CREATE SCHEMA retention;

-- Removes the oldest entries of the traceability log: the longest run from the
-- start of the chain in which every entry is older than the rule allows.
CREATE FUNCTION retention.purge_audit_events()
    RETURNS TABLE (anchor_id BIGINT, removed INTEGER)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    keep       INTERVAL;
    young_from BIGINT;
    last_seq   BIGINT;
    first_seq  BIGINT;
    n          INTEGER;
    last_hash  VARCHAR(64);
    prev       VARCHAR(64);
    new_id     BIGINT;
    created    TIMESTAMPTZ := date_trunc('milliseconds', now());
BEGIN
    -- Same lock as AuditService uses when appending
    PERFORM pg_advisory_xact_lock(280603412820);

    SELECT keep_interval INTO keep FROM retention_rules WHERE data_type = 'audit_events';
    IF keep IS NULL THEN
        RAISE EXCEPTION 'No retention rule for audit_events';
    END IF;

    SELECT min(seq) INTO young_from FROM audit_events WHERE timestamp >= now() - keep;
    SELECT max(seq) INTO last_seq FROM audit_events
        WHERE young_from IS NULL OR seq < young_from;
    IF last_seq IS NULL THEN
        RETURN QUERY SELECT NULL::BIGINT, 0;
        RETURN;
    END IF;

    SELECT min(seq), count(*) INTO first_seq, n FROM audit_events WHERE seq <= last_seq;
    SELECT hash INTO last_hash FROM audit_events WHERE seq = last_seq;
    SELECT a.hash INTO prev FROM audit_chain_anchors a WHERE a.chain_key = 'global' ORDER BY a.id DESC LIMIT 1;
    prev := coalesce(prev, repeat('0', 64));

    new_id := nextval('audit_chain_anchors_id_seq');
    INSERT INTO audit_chain_anchors (id, chain_key, kind, seq_from, seq_to, row_count,
                                     last_removed_hash, rule, created_at, prev_hash, hash)
    VALUES (new_id, 'global', 'RETENTION', first_seq, last_seq, n, last_hash, 'audit_events',
            created, prev,
            audit_anchor_hash(prev, new_id, 'global', 'RETENTION', first_seq, last_seq, n,
                              last_hash, 'audit_events', created));

    DELETE FROM audit_events WHERE seq <= last_seq;
    RETURN QUERY SELECT new_id, n;
END;
$$;

-- Removes closed cases whose e-service gallringsfrist has run out, and drafts
-- older than the rule. Returns what was removed so it can be logged.
CREATE FUNCTION retention.purge_cases()
    RETURNS TABLE (case_id UUID, reference_number VARCHAR, owner_id UUID, reason VARCHAR)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    draft_keep INTERVAL;
BEGIN
    SELECT keep_interval INTO draft_keep FROM retention_rules WHERE data_type = 'draft_cases';
    IF draft_keep IS NULL THEN
        RAISE EXCEPTION 'No retention rule for draft_cases';
    END IF;

    RETURN QUERY
    WITH due AS (
        SELECT c.id, 'CLOSED'::VARCHAR AS why
        FROM cases c JOIN flows f ON f.id = c.flow_id
        WHERE f.retention_months IS NOT NULL AND c.completed_at IS NOT NULL
          AND c.completed_at + make_interval(months => f.retention_months) < now()
        UNION
        SELECT c.id, 'DRAFT'::VARCHAR
        FROM cases c
        WHERE c.submitted_at IS NULL AND c.updated_at < now() - draft_keep
    )
    DELETE FROM cases c USING due
    WHERE c.id = due.id
    RETURNING c.id, c.reference_number, c.created_by, due.why;
END;
$$;

CREATE FUNCTION retention.purge_system_events()
    RETURNS INTEGER
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    keep INTERVAL;
    n    INTEGER;
BEGIN
    SELECT keep_interval INTO keep FROM retention_rules WHERE data_type = 'system_events';
    IF keep IS NULL THEN
        RAISE EXCEPTION 'No retention rule for system_events';
    END IF;
    DELETE FROM system_events WHERE timestamp < now() - keep;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

-- ---------- Privileges ----------
GRANT USAGE, CREATE ON SCHEMA retention TO eplatform_retention_owner;
GRANT USAGE ON SCHEMA public TO eplatform_retention_owner;
GRANT SELECT ON retention_rules, flows TO eplatform_retention_owner;
GRANT SELECT, DELETE ON audit_events, system_events, cases TO eplatform_retention_owner;
GRANT SELECT, INSERT ON audit_chain_anchors TO eplatform_retention_owner;
GRANT USAGE ON SEQUENCE audit_chain_anchors_id_seq TO eplatform_retention_owner;

ALTER FUNCTION retention.purge_audit_events() OWNER TO eplatform_retention_owner;
ALTER FUNCTION retention.purge_cases() OWNER TO eplatform_retention_owner;
ALTER FUNCTION retention.purge_system_events() OWNER TO eplatform_retention_owner;

REVOKE ALL ON FUNCTION retention.purge_audit_events(), retention.purge_cases(),
    retention.purge_system_events() FROM PUBLIC;
GRANT USAGE ON SCHEMA retention TO eplatform_retention;
GRANT EXECUTE ON FUNCTION retention.purge_audit_events(), retention.purge_cases(),
    retention.purge_system_events() TO eplatform_retention;
