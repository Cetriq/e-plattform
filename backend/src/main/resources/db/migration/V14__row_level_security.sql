-- =====================================================
-- Radnivåskydd (RLS), fail-closed (fas 0, steg 1.4)
-- =====================================================
-- The application connects as eplatform_app, which owns nothing: it cannot
-- disable RLS or triggers, and it is not a member of any other role. Flyway
-- keeps running as the role that owns the tables. LOGIN and the password of
-- eplatform_app are set outside migrations; until then the application keeps
-- connecting as the owner and nothing changes.
--
-- RLS is a backstop of its own, independent of CaseAccessPolicy: the policies
-- are plain SQL on the organisation context and on ownership, and never call
-- into the application. The context is set per transaction by
-- TenantContextBinder with set_config(..., true); missing context means no
-- rows, and a value that is not a UUID aborts the transaction.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eplatform_app') THEN
        CREATE ROLE eplatform_app NOLOGIN;
    END IF;
END
$$;

-- ---------- Context ----------
CREATE FUNCTION app_current_org() RETURNS UUID LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.org_id', true), '')::uuid
$$;

CREATE FUNCTION app_current_user() RETURNS UUID LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.user_id', true), '')::uuid
$$;

-- ---------- Narrow functions that look past RLS ----------
-- Each returns one small fact, never rows of another organisation.

-- The hash a new entry of a chain links to (AuditService), which a writer
-- needs even when it may not read the chain.
CREATE FUNCTION audit_latest_hash(chain TEXT) RETURNS VARCHAR(64)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT COALESCE(
        (SELECT hash FROM audit_events WHERE chain_key = chain ORDER BY seq DESC LIMIT 1),
        (SELECT last_removed_hash FROM audit_chain_anchors WHERE chain_key = chain ORDER BY id DESC LIMIT 1))
$$;

-- The organisation owning what an audit entry concerns, so an attempt on
-- another organisation's case is logged in that organisation's chain.
CREATE FUNCTION audit_entity_organization(entity_type TEXT, entity_id UUID) RETURNS UUID
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT CASE entity_type
        WHEN 'CASE' THEN (SELECT organization_id FROM cases WHERE id = entity_id)
        WHEN 'FILE' THEN (SELECT organization_id FROM attachments WHERE id = entity_id)
        WHEN 'FLOW' THEN (SELECT organization_id FROM flows WHERE id = entity_id)
        WHEN 'CATEGORY' THEN COALESCE((SELECT organization_id FROM categories WHERE id = entity_id),
                                      (SELECT organization_id FROM flow_types WHERE id = entity_id))
        WHEN 'ORGANIZATION' THEN (SELECT id FROM organizations WHERE id = entity_id AND kind <> 'PLATFORM')
    END
$$;

-- Whose personal data an audit entry concerns (the creator of a case, the
-- uploader of a file), also when the actor may not see the case.
CREATE FUNCTION audit_entity_subject(entity_type TEXT, entity_id UUID) RETURNS UUID
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT CASE entity_type
        WHEN 'CASE' THEN (SELECT created_by FROM cases WHERE id = entity_id)
        WHEN 'FILE' THEN (SELECT uploaded_by FROM attachments WHERE id = entity_id)
    END
$$;

-- For the platform's erasure of an account: delete the person's drafts in
-- every organisation. The application only calls it for the platform.
CREATE FUNCTION privacy_delete_drafts(person UUID) RETURNS INTEGER
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    n INTEGER;
BEGIN
    DELETE FROM cases WHERE created_by = person AND submitted_at IS NULL;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

-- For the platform's register extract: which organisations hold the person's
-- cases (names only, nothing of the cases).
CREATE FUNCTION privacy_case_organizations(person UUID) RETURNS TABLE (name VARCHAR)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT DISTINCT o.name FROM cases c JOIN organizations o ON o.id = c.organization_id
    WHERE c.created_by = person ORDER BY o.name
$$;

REVOKE ALL ON FUNCTION audit_latest_hash(TEXT), audit_entity_organization(TEXT, UUID), audit_entity_subject(TEXT, UUID),
    privacy_delete_drafts(UUID), privacy_case_organizations(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit_latest_hash(TEXT), audit_entity_organization(TEXT, UUID), audit_entity_subject(TEXT, UUID),
    privacy_delete_drafts(UUID), privacy_case_organizations(UUID), app_current_org(), app_current_user()
    TO eplatform_app;

-- ---------- Privileges of the application ----------
GRANT USAGE ON SCHEMA public TO eplatform_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO eplatform_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO eplatform_app;
-- The traceability log and its anchors: add and read, never change
REVOKE UPDATE, DELETE ON audit_events, audit_chain_anchors FROM eplatform_app;
REVOKE INSERT, UPDATE, DELETE ON audit_chain_anchors FROM eplatform_app;
-- The rules are changed by migrations only
REVOKE INSERT, UPDATE, DELETE ON retention_rules FROM eplatform_app;
-- Flyway's own bookkeeping: read only (the operations page shows the schema version)
REVOKE ALL ON flyway_schema_history FROM eplatform_app;
GRANT SELECT ON flyway_schema_history TO eplatform_app;
-- Tables created by later migrations get the same privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO eplatform_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO eplatform_app;

-- ---------- Row level security ----------
-- The migration role (owner) and the retention procedures keep full access
-- through their own policies; the application has only the policies below.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['cases', 'query_instances', 'case_events', 'internal_messages',
                             'external_messages', 'attachments', 'audit_events'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY migrations ON %I TO %I USING (true) WITH CHECK (true)', t, current_user);
        EXECUTE format('CREATE POLICY retention ON %I TO eplatform_retention_owner USING (true)', t);
    END LOOP;
END
$$;

-- Cases: the organisation that handles them, and their creator and owners
CREATE POLICY app_cases ON cases TO eplatform_app
    USING (organization_id = app_current_org()
           OR created_by = app_current_user()
           OR id IN (SELECT case_id FROM case_owners WHERE user_id = app_current_user()))
    WITH CHECK (organization_id = app_current_org()
                OR created_by = app_current_user()
                OR id IN (SELECT case_id FROM case_owners WHERE user_id = app_current_user()));

-- Rows belonging to a case: visible when the case is (the case policy applies inside)
CREATE POLICY app_case_rows ON query_instances TO eplatform_app
    USING (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id))
    WITH CHECK (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id));
CREATE POLICY app_case_rows ON case_events TO eplatform_app
    USING (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id))
    WITH CHECK (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id));
CREATE POLICY app_case_rows ON internal_messages TO eplatform_app
    USING (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id))
    WITH CHECK (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id));
CREATE POLICY app_case_rows ON external_messages TO eplatform_app
    USING (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id))
    WITH CHECK (EXISTS (SELECT 1 FROM cases c WHERE c.id = case_id));

-- Files: the organisation, and whoever uploaded them
CREATE POLICY app_attachments ON attachments TO eplatform_app
    USING (organization_id = app_current_org() OR uploaded_by = app_current_user())
    WITH CHECK (organization_id = app_current_org() OR uploaded_by = app_current_user());

-- The traceability log: anyone may add entries (the trigger keeps them
-- unchanged); an organisation reads its own, the platform reads the platform
-- chain and the entries written before chains per organisation.
CREATE POLICY app_audit_insert ON audit_events FOR INSERT TO eplatform_app
    WITH CHECK (true);
CREATE POLICY app_audit_read ON audit_events FOR SELECT TO eplatform_app
    USING (organization_id = app_current_org()
           OR (app_current_org() = '00000000-0000-0000-0000-000000000012'
               AND (organization_id IS NULL OR chain_key IN ('platform', 'global'))));
