-- =====================================================
-- Kontraktsmodell med tre nivåer (fas 0, steg 3a)
-- =====================================================
--   1. service_definitions          the service as defined once, e.g. nationally:
--                                   concepts, rules, decision types, and what a
--                                   local application may change (change policy,
--                                   extension points, parameters). Maintained by
--                                   a steward organisation.
--   2. service_applications         an organisation's application of a definition:
--                                   parameters, local content at extension points,
--                                   fees, integrations, AI policy, and the roles
--                                   (responsible, deciding authority, data
--                                   controller, participants) kept apart from who
--                                   owns it technically.
--   3. the published composition    composed and checked in steg 4.
--
-- Definition and application contents are JSON documents validated against
-- JSON Schemas (eplatform.definition/1, eplatform.application/1). A published
-- version never changes; its content is hashed in canonical form (RFC 8785).

-- ---------- Concepts ----------
CREATE TABLE concepts (
    key         VARCHAR(120) NOT NULL,
    version     INTEGER      NOT NULL,
    schema      JSONB        NOT NULL,
    data_class  VARCHAR(20)  NOT NULL
        CHECK (data_class IN ('PUBLIC', 'PERSONAL', 'SENSITIVE', 'CONFIDENTIAL')),
    description TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (key, version)
);

COMMENT ON COLUMN concepts.data_class IS
    'PUBLIC; PERSONAL (personuppgift); SENSITIVE (känslig personuppgift, GDPR art. 9); CONFIDENTIAL (sekretess enligt OSL)';

CREATE TRIGGER trg_concepts_append_only BEFORE UPDATE OR DELETE ON concepts
    FOR EACH ROW EXECUTE FUNCTION snapshots_append_only();

-- ---------- Level 1: definitions ----------
CREATE TABLE service_definitions (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key                    VARCHAR(120) NOT NULL UNIQUE CHECK (key ~ '^[a-z0-9][a-z0-9.-]*$'),
    name                   VARCHAR(255) NOT NULL,
    steward_organization_id UUID        NOT NULL REFERENCES organizations(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE service_definition_versions (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    definition_id UUID        NOT NULL REFERENCES service_definitions(id),
    version       INTEGER     NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    -- When the legal ground holds, apart from when it was published
    valid_from    DATE,
    valid_to      DATE,
    legal_basis   JSONB       NOT NULL DEFAULT '[]',
    content       JSONB       NOT NULL,
    content_hash  VARCHAR(64),
    published_at  TIMESTAMPTZ,
    published_by  UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (definition_id, version),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CHECK (status = 'DRAFT' OR content_hash IS NOT NULL)
);

-- ---------- Level 2: applications ----------
CREATE TABLE service_applications (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    definition_id         UUID         NOT NULL REFERENCES service_definitions(id),
    name                  VARCHAR(255) NOT NULL,
    -- Technical owner: may be a collaboration; not the same as being responsible
    owner_organization_id UUID         NOT NULL REFERENCES organizations(id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE service_application_roles (
    application_id     UUID    NOT NULL REFERENCES service_applications(id) ON DELETE CASCADE,
    organization_id    UUID    NOT NULL REFERENCES organizations(id),
    responsible        BOOLEAN NOT NULL DEFAULT false,
    deciding_authority BOOLEAN NOT NULL DEFAULT false,
    data_controller    BOOLEAN NOT NULL DEFAULT false,
    participant        BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (application_id, organization_id)
);

-- Exactly one responsible organisation is checked when publishing; at most one here
CREATE UNIQUE INDEX uq_application_responsible ON service_application_roles(application_id) WHERE responsible;

CREATE TABLE service_application_versions (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id        UUID        NOT NULL REFERENCES service_applications(id),
    -- The definition version it applies; changing to a newer one is a new version
    definition_version_id UUID        NOT NULL REFERENCES service_definition_versions(id),
    version               INTEGER     NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    valid_from            DATE,
    valid_to              DATE,
    content               JSONB       NOT NULL,
    content_hash          VARCHAR(64),
    published_at          TIMESTAMPTZ,
    published_by          UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (application_id, version),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CHECK (status = 'DRAFT' OR content_hash IS NOT NULL)
);

-- ---------- Published versions never change ----------
-- Only PUBLISHED to RETIRED, once published.
CREATE FUNCTION contract_versions_locked() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% version % is % and cannot be deleted', TG_TABLE_NAME, OLD.version, OLD.status;
    END IF;
    IF (to_jsonb(NEW) - 'status') IS DISTINCT FROM (to_jsonb(OLD) - 'status')
       OR NOT (NEW.status = OLD.status OR (OLD.status = 'PUBLISHED' AND NEW.status = 'RETIRED')) THEN
        RAISE EXCEPTION '% version % is % and cannot be changed', TG_TABLE_NAME, OLD.version, OLD.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_definition_versions_locked BEFORE UPDATE OR DELETE ON service_definition_versions
    FOR EACH ROW EXECUTE FUNCTION contract_versions_locked();
CREATE TRIGGER trg_application_versions_locked BEFORE UPDATE OR DELETE ON service_application_versions
    FOR EACH ROW EXECUTE FUNCTION contract_versions_locked();

-- An application version may only apply a published definition version
CREATE FUNCTION application_pins_published_definition() RETURNS trigger AS $$
BEGIN
    IF (SELECT status FROM service_definition_versions WHERE id = NEW.definition_version_id) <> 'PUBLISHED'
       AND NEW.status = 'DRAFT' AND (TG_OP = 'INSERT' OR NEW.definition_version_id <> OLD.definition_version_id) THEN
        RAISE EXCEPTION 'An application can only apply a published definition version';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_application_pins_published BEFORE INSERT OR UPDATE ON service_application_versions
    FOR EACH ROW EXECUTE FUNCTION application_pins_published_definition();

REVOKE UPDATE, DELETE ON concepts FROM eplatform_app;
