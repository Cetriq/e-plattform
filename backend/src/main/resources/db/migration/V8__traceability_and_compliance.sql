-- =====================================================
-- Spårbarhet, dataskydd och drift
-- =====================================================

-- ---------- Spårbarhetslogg (audit_events) ----------
-- seq gives a strict order for the hash chain, subject_user_id records whose
-- personal data was involved (so "who has seen my data?" can be answered),
-- category/outcome make the log searchable, and prev_hash/hash chain every
-- entry to the one before it so that changes can be detected.
ALTER TABLE audit_events
    ADD COLUMN seq             BIGSERIAL,
    ADD COLUMN category        VARCHAR(20) NOT NULL DEFAULT 'DATA_ACCESS',
    ADD COLUMN outcome         VARCHAR(10) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN subject_user_id UUID,
    ADD COLUMN prev_hash       VARCHAR(64),
    ADD COLUMN hash            VARCHAR(64);

CREATE UNIQUE INDEX idx_audit_seq ON audit_events(seq);
CREATE INDEX idx_audit_subject ON audit_events(subject_user_id, timestamp DESC);
CREATE INDEX idx_audit_category ON audit_events(category, timestamp DESC);

-- Entries written before this migration were recorded without user context
-- (always "anonymous") and outside the hash chain; they carry no information.
DELETE FROM audit_events;

-- The log is append-only. Rows can only be removed by the retention job,
-- which sets eplatform.audit_retention for its own transaction.
CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('eplatform.audit_retention', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();

COMMENT ON TABLE audit_events IS
    'Spårbarhetslogg: vem gjorde vad med vilka uppgifter, med hashkedja. Endast tillägg.';

-- ---------- Systemlogg för IT/drift ----------
-- Technical events (errors, job runs, startups). Must not contain personal data.
CREATE TABLE system_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    level       VARCHAR(10) NOT NULL,
    source      VARCHAR(100) NOT NULL,
    message     TEXT NOT NULL,
    details     JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_system_events_time ON system_events(timestamp DESC);
CREATE INDEX idx_system_events_level ON system_events(level, timestamp DESC);

-- ---------- Gallring ----------
-- Months after a case is closed before it is removed (gallras). NULL means the
-- cases are kept (bevaras), which is the safe default for allmänna handlingar.
ALTER TABLE flows ADD COLUMN retention_months INTEGER;
ALTER TABLE flows ADD CONSTRAINT chk_flows_retention CHECK (retention_months IS NULL OR retention_months > 0);

-- ---------- Roller ----------
INSERT INTO roles (id, name, description, permissions, is_system) VALUES
    ('00000000-0000-0000-0000-000000000005', 'SECURITY_OFFICER',
     'Informationssäkerhet och dataskydd: spårbarhetslogg, registerutdrag och radering',
     ARRAY['audit:read', 'audit:export', 'privacy:extract', 'privacy:erase', 'retention:read'], true),
    ('00000000-0000-0000-0000-000000000006', 'OPERATIONS',
     'IT och drift: driftstatus och tekniska loggar (inga personuppgifter)',
     ARRAY['ops:status', 'ops:logs'], true);

-- Demopersonor för de nya rollerna
INSERT INTO users (id, email, username, first_name, last_name, display_name, organization_id, active, email_verified) VALUES
    ('00000000-0000-0000-0000-000000000103', 'informationssakerhet@example.com', 'informationssakerhet',
     'Ingrid', 'Säkerhet', 'Ingrid Säkerhet', '00000000-0000-0000-0000-000000000010', true, true),
    ('00000000-0000-0000-0000-000000000104', 'it-drift@example.com', 'it-drift',
     'Ivar', 'Drift', 'Ivar Drift', '00000000-0000-0000-0000-000000000010', true, true);

INSERT INTO user_roles (user_id, role_id) VALUES
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000005'),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000006');
