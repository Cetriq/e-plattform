-- =====================================================
-- E-tjänstversioner på kontraktsnivåerna (fas 0, steg 3b)
-- =====================================================
-- A version of an e-service can apply a published version of a service
-- application (level 2, which in turn applies a definition version, level 1).
-- Each of its fields can then name the field of the definition, or the local
-- field of the application, that it collects: the field key.
--
-- Both are part of the version and locked with it once it is published
-- (flows_locked_when_published, query_definitions_locked). Versions published
-- before this keep their manifests unchanged; they are moved onto the levels
-- by publishing a new version.

ALTER TABLE flows ADD COLUMN application_version_id UUID REFERENCES service_application_versions(id);
CREATE INDEX idx_flows_application_version ON flows(application_version_id);

ALTER TABLE query_definitions ADD COLUMN field_key VARCHAR(120) CHECK (field_key ~ '^[a-z0-9][a-z0-9.-]*$');

-- The manifest names the level versions it was composed from (eplatform.manifest/2)
ALTER TABLE publication_manifests
    ADD COLUMN definition_version_id  UUID REFERENCES service_definition_versions(id),
    ADD COLUMN application_version_id UUID REFERENCES service_application_versions(id);
