-- =====================================================
-- Spårbarhetslogg per organisation (fas 0, steg 1.3c)
-- =====================================================
-- Each organisation gets its own hash chain, so its security officer can
-- search and verify its log without reading other organisations' entries.
-- Events that belong to no organisation (citizen logins, platform jobs) go
-- to the chain "platform", readable only by the platform's security function.
--
--   chain_key      'global'   entries written before this migration (one
--                             chain, hash version 1, left exactly as it is)
--                  'platform' platform events from now on
--                  <org id>   the organisation's events from now on
--   organization_id           whose entry it is, for searching; derived for
--                             old entries where possible (not part of their hash)
--   hash_version   1 = original content, 2 = also chain_key and organization_id

ALTER TABLE audit_events
    ADD COLUMN chain_key       VARCHAR(80) NOT NULL DEFAULT 'global',
    ADD COLUMN organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    ADD COLUMN hash_version    SMALLINT    NOT NULL DEFAULT 1;

-- Old entries: whose they are, as far as it can be told. These columns are
-- not covered by the version 1 hash, so the old chain stays intact.
ALTER TABLE audit_events DISABLE TRIGGER trg_audit_append_only;

UPDATE audit_events a SET organization_id = c.organization_id
FROM cases c WHERE a.entity_type = 'CASE' AND a.entity_id = c.id::text;

UPDATE audit_events a SET organization_id = f.organization_id
FROM flows f WHERE a.organization_id IS NULL AND a.entity_type = 'FLOW' AND a.entity_id = f.id::text;

UPDATE audit_events a SET organization_id = at.organization_id
FROM attachments at WHERE a.organization_id IS NULL AND a.entity_type = 'FILE' AND a.entity_id = at.id::text;

UPDATE audit_events a SET organization_id = u.organization_id
FROM users u
WHERE a.organization_id IS NULL AND a.user_id = u.id::text AND u.organization_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM organization_members m WHERE m.user_id = u.id);

ALTER TABLE audit_events ENABLE TRIGGER trg_audit_append_only;

-- New entries must name their chain and version
ALTER TABLE audit_events ALTER COLUMN chain_key DROP DEFAULT;
ALTER TABLE audit_events ALTER COLUMN hash_version DROP DEFAULT;

CREATE INDEX idx_audit_chain ON audit_events(chain_key, seq);
CREATE INDEX idx_audit_org ON audit_events(organization_id, seq DESC);

-- ---------- The platform ----------
-- Its security function reads the platform chain and the old shared chain.
INSERT INTO organizations (id, name, kind, slug) VALUES
    ('00000000-0000-0000-0000-000000000012', 'e-Plattform', 'PLATFORM', 'plattform');

-- Ingrid Säkerhet (informationssakerhet@example.com) also works for the platform
INSERT INTO organization_members (id, user_id, organization_id) VALUES
    ('00000000-0000-0000-0009-000000000103', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000012');
INSERT INTO organization_member_roles (member_id, role_id) VALUES
    ('00000000-0000-0000-0009-000000000103', '00000000-0000-0000-0000-000000000005');

-- ---------- Gallring per chain ----------
-- Each chain is gallrad on its own, with its own anchors.
DROP FUNCTION retention.purge_audit_events();

CREATE FUNCTION retention.purge_audit_events()
    RETURNS TABLE (chain VARCHAR, anchor_id BIGINT, removed INTEGER)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    keep       INTERVAL;
    k          VARCHAR(80);
    young_from BIGINT;
    last_seq   BIGINT;
    first_seq  BIGINT;
    n          INTEGER;
    last_hash  VARCHAR(64);
    prev       VARCHAR(64);
    new_id     BIGINT;
    created    TIMESTAMPTZ := date_trunc('milliseconds', now());
BEGIN
    SELECT keep_interval INTO keep FROM retention_rules WHERE data_type = 'audit_events';
    IF keep IS NULL THEN
        RAISE EXCEPTION 'No retention rule for audit_events';
    END IF;

    FOR k IN SELECT DISTINCT a.chain_key FROM audit_events a ORDER BY 1 LOOP
        -- Same lock as AuditService uses when appending to this chain
        PERFORM pg_advisory_xact_lock(hashtext('audit:' || k));

        SELECT min(a.seq) INTO young_from FROM audit_events a
            WHERE a.chain_key = k AND a.timestamp >= now() - keep;
        SELECT max(a.seq) INTO last_seq FROM audit_events a
            WHERE a.chain_key = k AND (young_from IS NULL OR a.seq < young_from);
        CONTINUE WHEN last_seq IS NULL;

        SELECT min(a.seq), count(*) INTO first_seq, n FROM audit_events a
            WHERE a.chain_key = k AND a.seq <= last_seq;
        SELECT a.hash INTO last_hash FROM audit_events a WHERE a.seq = last_seq;
        SELECT x.hash INTO prev FROM audit_chain_anchors x WHERE x.chain_key = k ORDER BY x.id DESC LIMIT 1;
        prev := coalesce(prev, repeat('0', 64));

        new_id := nextval('audit_chain_anchors_id_seq');
        INSERT INTO audit_chain_anchors (id, chain_key, kind, seq_from, seq_to, row_count,
                                         last_removed_hash, rule, created_at, prev_hash, hash)
        VALUES (new_id, k, 'RETENTION', first_seq, last_seq, n, last_hash, 'audit_events',
                created, prev,
                audit_anchor_hash(prev, new_id, k, 'RETENTION', first_seq, last_seq, n,
                                  last_hash, 'audit_events', created));

        DELETE FROM audit_events a WHERE a.chain_key = k AND a.seq <= last_seq;

        chain := k;
        anchor_id := new_id;
        removed := n;
        RETURN NEXT;
    END LOOP;
END;
$$;

ALTER FUNCTION retention.purge_audit_events() OWNER TO eplatform_retention_owner;
REVOKE ALL ON FUNCTION retention.purge_audit_events() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION retention.purge_audit_events() TO eplatform_retention;

-- Removed cases are logged in their organisation's chain, so the procedure
-- reports which organisation each belonged to.
DROP FUNCTION retention.purge_cases();

CREATE FUNCTION retention.purge_cases()
    RETURNS TABLE (case_id UUID, reference_number VARCHAR, owner_id UUID, reason VARCHAR, organization_id UUID)
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
    RETURNING c.id, c.reference_number, c.created_by, due.why, c.organization_id;
END;
$$;

ALTER FUNCTION retention.purge_cases() OWNER TO eplatform_retention_owner;
REVOKE ALL ON FUNCTION retention.purge_cases() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION retention.purge_cases() TO eplatform_retention;
