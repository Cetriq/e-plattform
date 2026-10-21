-- =====================================================
-- Row level security for the contract levels (fas 0, steg 3a)
-- =====================================================
-- The database keeps definitions and applications apart per organisation,
-- independent of the checks in ContractService:
--   * a definition is visible to its steward, and to everyone once it has a
--     version that is no longer a draft; only the steward writes it
--   * an application is visible to its owner and the organisations holding a
--     role in it; only the owner writes it
-- Concepts stay readable by everyone (a shared vocabulary, no organisation).

-- ---------- Narrow functions that look past RLS ----------
-- Each answers one question about one row; policies use them so that a policy
-- never queries a table whose policy queries it back.

CREATE FUNCTION contract_definition_steward(definition UUID) RETURNS UUID
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT steward_organization_id FROM service_definitions WHERE id = definition
$$;

CREATE FUNCTION contract_definition_published(definition UUID) RETURNS BOOLEAN
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT EXISTS (SELECT 1 FROM service_definition_versions WHERE definition_id = definition AND status <> 'DRAFT')
$$;

CREATE FUNCTION contract_application_owner(application UUID) RETURNS UUID
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT owner_organization_id FROM service_applications WHERE id = application
$$;

CREATE FUNCTION contract_application_role_holder(application UUID, organization UUID) RETURNS BOOLEAN
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp AS $$
    SELECT EXISTS (SELECT 1 FROM service_application_roles WHERE application_id = application AND organization_id = organization)
$$;

REVOKE ALL ON FUNCTION contract_definition_steward(UUID), contract_definition_published(UUID),
    contract_application_owner(UUID), contract_application_role_holder(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION contract_definition_steward(UUID), contract_definition_published(UUID),
    contract_application_owner(UUID), contract_application_role_holder(UUID, UUID) TO eplatform_app;

-- ---------- Policies ----------
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['service_definitions', 'service_definition_versions', 'service_applications',
                             'service_application_roles', 'service_application_versions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY migrations ON %I TO %I USING (true) WITH CHECK (true)', t, current_user);
    END LOOP;
END
$$;

CREATE POLICY app_read ON service_definitions FOR SELECT TO eplatform_app
    USING (steward_organization_id = app_current_org() OR contract_definition_published(id));
CREATE POLICY app_write ON service_definitions TO eplatform_app
    USING (steward_organization_id = app_current_org())
    WITH CHECK (steward_organization_id = app_current_org());

CREATE POLICY app_read ON service_definition_versions FOR SELECT TO eplatform_app
    USING (status <> 'DRAFT' OR contract_definition_steward(definition_id) = app_current_org());
CREATE POLICY app_write ON service_definition_versions TO eplatform_app
    USING (contract_definition_steward(definition_id) = app_current_org())
    WITH CHECK (contract_definition_steward(definition_id) = app_current_org());

CREATE POLICY app_read ON service_applications FOR SELECT TO eplatform_app
    USING (owner_organization_id = app_current_org() OR contract_application_role_holder(id, app_current_org()));
CREATE POLICY app_write ON service_applications TO eplatform_app
    USING (owner_organization_id = app_current_org())
    WITH CHECK (owner_organization_id = app_current_org());

CREATE POLICY app_read ON service_application_roles FOR SELECT TO eplatform_app
    USING (contract_application_owner(application_id) = app_current_org()
           OR contract_application_role_holder(application_id, app_current_org()));
CREATE POLICY app_write ON service_application_roles TO eplatform_app
    USING (contract_application_owner(application_id) = app_current_org())
    WITH CHECK (contract_application_owner(application_id) = app_current_org());

CREATE POLICY app_read ON service_application_versions FOR SELECT TO eplatform_app
    USING (contract_application_owner(application_id) = app_current_org()
           OR contract_application_role_holder(application_id, app_current_org()));
CREATE POLICY app_write ON service_application_versions TO eplatform_app
    USING (contract_application_owner(application_id) = app_current_org())
    WITH CHECK (contract_application_owner(application_id) = app_current_org());

-- ---------- Fail closed when pinning a definition version ----------
-- Under RLS another steward's draft is invisible; a missing row now counts as
-- not published instead of slipping past the comparison as NULL.
CREATE OR REPLACE FUNCTION application_pins_published_definition() RETURNS trigger AS $$
BEGIN
    IF (SELECT status FROM service_definition_versions WHERE id = NEW.definition_version_id) IS DISTINCT FROM 'PUBLISHED'
       AND NEW.status = 'DRAFT' AND (TG_OP = 'INSERT' OR NEW.definition_version_id <> OLD.definition_version_id) THEN
        RAISE EXCEPTION 'An application can only apply a published definition version';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
