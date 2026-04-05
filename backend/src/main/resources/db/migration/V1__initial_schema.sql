-- =====================================================
-- E-PLATFORM DATABASE SCHEMA
-- Version: 1.0.0
-- =====================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For text search

-- =====================================================
-- FLOW TABLES (Formulärdefinitioner)
-- =====================================================

-- Tjänstfamiljer (gruppering av versioner)
CREATE TABLE flow_families (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE flow_families IS 'Grupperar olika versioner av samma tjänst/formulär';

-- Tjänsttyper (kategorisering på högsta nivå)
CREATE TABLE flow_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    color           VARCHAR(7),  -- Hex color, e.g., #FF5733
    icon            VARCHAR(100),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE flow_types IS 'Övergripande kategorisering av tjänster (t.ex. Bygglov, Miljö)';

-- Kategorier (underkategorier till flow_types)
CREATE TABLE categories (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    flow_type_id    UUID REFERENCES flow_types(id) ON DELETE SET NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_flow_type ON categories(flow_type_id);

-- Flöden/Formulär (huvudentitet)
CREATE TABLE flows (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id           UUID REFERENCES flow_families(id) ON DELETE SET NULL,
    type_id             UUID REFERENCES flow_types(id) ON DELETE SET NULL,
    category_id         UUID REFERENCES categories(id) ON DELETE SET NULL,

    -- Grundläggande info
    name                VARCHAR(255) NOT NULL,
    version             INTEGER NOT NULL DEFAULT 1,

    -- Beskrivningar
    short_description   TEXT,
    long_description    TEXT,
    submitted_message   TEXT,  -- Meddelande som visas efter inskickning

    -- Konfiguration
    enabled             BOOLEAN NOT NULL DEFAULT false,
    require_auth        BOOLEAN NOT NULL DEFAULT true,
    require_signing     BOOLEAN NOT NULL DEFAULT false,
    sequential_signing  BOOLEAN NOT NULL DEFAULT false,
    allow_save_draft    BOOLEAN NOT NULL DEFAULT true,
    allow_multiple      BOOLEAN NOT NULL DEFAULT true,  -- Tillåt flera ärenden per användare

    -- Publicering
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    publish_date        TIMESTAMPTZ,
    unpublish_date      TIMESTAMPTZ,

    -- Externa länkar
    external_link       VARCHAR(1024),

    -- Metadata
    tags                TEXT[] DEFAULT '{}',

    -- Tidsstämplar
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,

    CONSTRAINT uq_flow_family_version UNIQUE(family_id, version),
    CONSTRAINT chk_flow_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_flows_status ON flows(status);
CREATE INDEX idx_flows_type ON flows(type_id);
CREATE INDEX idx_flows_family ON flows(family_id);
CREATE INDEX idx_flows_tags ON flows USING GIN(tags);
CREATE INDEX idx_flows_enabled ON flows(enabled) WHERE enabled = true;

-- Steg i formulär
CREATE TABLE steps (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,

    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_steps_flow ON steps(flow_id);
CREATE INDEX idx_steps_sort ON steps(flow_id, sort_order);

-- Frågedefinitioner (formulärfält)
CREATE TABLE query_definitions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    step_id         UUID NOT NULL REFERENCES steps(id) ON DELETE CASCADE,

    -- Grundläggande info
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    help_text       TEXT,
    placeholder     VARCHAR(500),

    -- Typ
    query_type      VARCHAR(50) NOT NULL,

    -- Konfiguration (typ-specifik JSON)
    config          JSONB NOT NULL DEFAULT '{}',

    -- Tillstånd
    required        BOOLEAN NOT NULL DEFAULT false,
    default_state   VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',

    -- Export
    export_name     VARCHAR(255),
    exportable      BOOLEAN NOT NULL DEFAULT true,

    -- Layout
    sort_order      INTEGER NOT NULL DEFAULT 0,
    width           VARCHAR(20) DEFAULT 'FULL',  -- FULL, HALF, THIRD

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_query_type CHECK (query_type IN (
        'TEXT', 'TEXTAREA', 'NUMBER', 'EMAIL', 'PHONE', 'URL',
        'DATE', 'DATETIME', 'TIME',
        'SELECT', 'MULTISELECT', 'RADIO', 'CHECKBOX',
        'FILE', 'IMAGE',
        'MAP', 'LOCATION',
        'SIGNATURE',
        'ORGANIZATION', 'PERSON',
        'HEADING', 'PARAGRAPH', 'DIVIDER'
    )),
    CONSTRAINT chk_default_state CHECK (default_state IN ('VISIBLE', 'VISIBLE_REQUIRED', 'HIDDEN'))
);

CREATE INDEX idx_query_defs_step ON query_definitions(step_id);
CREATE INDEX idx_query_defs_type ON query_definitions(query_type);
CREATE INDEX idx_query_defs_sort ON query_definitions(step_id, sort_order);

-- Evaluatorer (villkorslogik)
CREATE TABLE evaluator_definitions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    query_id        UUID NOT NULL REFERENCES query_definitions(id) ON DELETE CASCADE,

    name            VARCHAR(255),
    description     TEXT,

    -- Typ av evaluator
    evaluator_type  VARCHAR(50) NOT NULL,

    -- Villkor (JSON för flexibilitet)
    condition       JSONB NOT NULL,

    -- Målstyrning
    target_query_ids UUID[] NOT NULL,
    target_state    VARCHAR(20) NOT NULL,

    enabled         BOOLEAN NOT NULL DEFAULT true,
    sort_order      INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_evaluator_type CHECK (evaluator_type IN (
        'VALUE_EQUALS', 'VALUE_NOT_EQUALS',
        'VALUE_IN', 'VALUE_NOT_IN',
        'VALUE_CONTAINS', 'VALUE_NOT_CONTAINS',
        'VALUE_GREATER_THAN', 'VALUE_LESS_THAN',
        'VALUE_BETWEEN',
        'REGEX_MATCH',
        'IS_EMPTY', 'IS_NOT_EMPTY',
        'CUSTOM'
    )),
    CONSTRAINT chk_target_state CHECK (target_state IN ('VISIBLE', 'VISIBLE_REQUIRED', 'HIDDEN'))
);

CREATE INDEX idx_evaluators_query ON evaluator_definitions(query_id);
CREATE INDEX idx_evaluators_targets ON evaluator_definitions USING GIN(target_query_ids);

-- Status-definitioner per flöde
CREATE TABLE status_definitions (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    flow_id                 UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,

    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    internal_description    TEXT,

    -- Typ av status
    status_type             VARCHAR(30) NOT NULL,

    -- Visuell representation
    color                   VARCHAR(7),
    icon                    VARCHAR(100),

    -- Konfiguration
    config                  JSONB NOT NULL DEFAULT '{}',

    -- Behörigheter
    user_can_edit           BOOLEAN NOT NULL DEFAULT false,
    user_can_delete         BOOLEAN NOT NULL DEFAULT false,
    user_can_message        BOOLEAN NOT NULL DEFAULT true,
    manager_can_edit        BOOLEAN NOT NULL DEFAULT true,

    -- SLA
    handling_days           INTEGER,

    sort_order              INTEGER NOT NULL DEFAULT 0,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status_type CHECK (status_type IN (
        'DRAFT', 'SUBMITTED', 'IN_PROGRESS', 'WAITING_FOR_USER',
        'WAITING_FOR_EXTERNAL', 'APPROVED', 'REJECTED',
        'COMPLETED', 'CANCELLED', 'ARCHIVED'
    ))
);

CREATE INDEX idx_status_defs_flow ON status_definitions(flow_id);

-- Tillåtna statusövergångar
CREATE TABLE status_transitions (
    from_status_id      UUID REFERENCES status_definitions(id) ON DELETE CASCADE,
    to_status_id        UUID REFERENCES status_definitions(id) ON DELETE CASCADE,

    requires_comment    BOOLEAN NOT NULL DEFAULT false,
    requires_signature  BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY (from_status_id, to_status_id)
);

-- =====================================================
-- IDENTITY TABLES (Användare och behörigheter)
-- =====================================================

-- Organisationer
CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    name            VARCHAR(255) NOT NULL,
    org_number      VARCHAR(20),  -- Organisationsnummer

    parent_id       UUID REFERENCES organizations(id) ON DELETE SET NULL,

    -- Kontaktinfo
    email           VARCHAR(255),
    phone           VARCHAR(50),
    address         TEXT,

    active          BOOLEAN NOT NULL DEFAULT true,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizations_parent ON organizations(parent_id);
CREATE INDEX idx_organizations_org_number ON organizations(org_number);

-- Användare
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Identitet
    external_id     VARCHAR(255) UNIQUE,  -- BankID personnummer, etc.
    email           VARCHAR(255) UNIQUE,
    username        VARCHAR(100) UNIQUE,

    -- Profil
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    display_name    VARCHAR(255),
    phone           VARCHAR(50),

    -- Koppling till organisation
    organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,

    -- Status
    active          BOOLEAN NOT NULL DEFAULT true,
    email_verified  BOOLEAN NOT NULL DEFAULT false,

    -- Inställningar (JSON)
    settings        JSONB NOT NULL DEFAULT '{}',

    -- Tidsstämplar
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_external_id ON users(external_id);
CREATE INDEX idx_users_organization ON users(organization_id);
CREATE INDEX idx_users_name ON users(last_name, first_name);

-- Grupper
CREATE TABLE groups (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,

    name            VARCHAR(255) NOT NULL,
    description     TEXT,

    -- Systemgrupper kan inte raderas
    is_system       BOOLEAN NOT NULL DEFAULT false,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groups_organization ON groups(organization_id);

-- Roller
CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT,

    -- Behörigheter som array
    permissions     TEXT[] NOT NULL DEFAULT '{}',

    -- Systemroller kan inte raderas
    is_system       BOOLEAN NOT NULL DEFAULT false,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Användar-grupp-koppling
CREATE TABLE user_groups (
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    group_id        UUID REFERENCES groups(id) ON DELETE CASCADE,

    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by        UUID REFERENCES users(id) ON DELETE SET NULL,

    PRIMARY KEY (user_id, group_id)
);

-- Användar-roll-koppling
CREATE TABLE user_roles (
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID REFERENCES roles(id) ON DELETE CASCADE,

    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, role_id)
);

-- =====================================================
-- CASE TABLES (Ärendeinstanser)
-- =====================================================

-- Ärenden (instanser av formulär)
CREATE TABLE cases (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    flow_id             UUID NOT NULL REFERENCES flows(id),
    status_id           UUID REFERENCES status_definitions(id),

    -- Läsbart referensnummer
    reference_number    VARCHAR(50) NOT NULL UNIQUE,

    -- Aktuellt steg (för pågående ärenden)
    current_step_id     UUID REFERENCES steps(id),
    current_step_index  INTEGER NOT NULL DEFAULT 0,

    -- Prioritet
    priority            VARCHAR(10) NOT NULL DEFAULT 'NORMAL',

    -- Beskrivningar
    user_description    VARCHAR(500),
    manager_description VARCHAR(500),

    -- Skapare
    created_by          UUID NOT NULL REFERENCES users(id),

    -- Tidsstämplar
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,

    -- Metadata (flexibelt JSON-fält)
    metadata            JSONB NOT NULL DEFAULT '{}',

    CONSTRAINT chk_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_cases_flow ON cases(flow_id);
CREATE INDEX idx_cases_status ON cases(status_id);
CREATE INDEX idx_cases_created_by ON cases(created_by);
CREATE INDEX idx_cases_created_at ON cases(created_at DESC);
CREATE INDEX idx_cases_reference ON cases(reference_number);
CREATE INDEX idx_cases_priority ON cases(priority);
CREATE INDEX idx_cases_submitted ON cases(submitted_at DESC) WHERE submitted_at IS NOT NULL;

-- Sekvens för referensnummer
CREATE SEQUENCE case_reference_seq START 1000;

-- Funktion för att generera referensnummer
CREATE OR REPLACE FUNCTION generate_case_reference()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.reference_number IS NULL THEN
        NEW.reference_number := 'E-' || TO_CHAR(NOW(), 'YYYY') || '-' ||
                                LPAD(nextval('case_reference_seq')::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_reference
    BEFORE INSERT ON cases
    FOR EACH ROW
    EXECUTE FUNCTION generate_case_reference();

-- Ägarrelationer (medborgare som äger ärendet)
CREATE TABLE case_owners (
    case_id         UUID REFERENCES cases(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    is_primary      BOOLEAN NOT NULL DEFAULT false,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (case_id, user_id)
);

CREATE INDEX idx_case_owners_user ON case_owners(user_id);

-- Handläggarrelationer
CREATE TABLE case_managers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    group_id        UUID REFERENCES groups(id) ON DELETE CASCADE,

    is_primary      BOOLEAN NOT NULL DEFAULT false,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by     UUID REFERENCES users(id) ON DELETE SET NULL,

    CONSTRAINT chk_manager_target CHECK (user_id IS NOT NULL OR group_id IS NOT NULL)
);

CREATE INDEX idx_case_managers_case ON case_managers(case_id);
CREATE INDEX idx_case_managers_user ON case_managers(user_id);
CREATE INDEX idx_case_managers_group ON case_managers(group_id);

-- Frågeinstanser (formulärvärden)
CREATE TABLE query_instances (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    query_def_id    UUID NOT NULL REFERENCES query_definitions(id),

    -- Aktuellt tillstånd
    state           VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',

    -- Värde (JSONB för flexibilitet)
    value           JSONB,

    -- Validering
    populated       BOOLEAN NOT NULL DEFAULT false,
    validated       BOOLEAN NOT NULL DEFAULT false,
    validation_errors JSONB,

    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_query_instance UNIQUE(case_id, query_def_id),
    CONSTRAINT chk_instance_state CHECK (state IN ('VISIBLE', 'VISIBLE_REQUIRED', 'HIDDEN'))
);

CREATE INDEX idx_query_instances_case ON query_instances(case_id);
CREATE INDEX idx_query_instances_def ON query_instances(query_def_id);

-- Sökindex för formulärvärden (för snabb sökning)
CREATE TABLE case_search_values (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    query_def_id    UUID NOT NULL REFERENCES query_definitions(id),

    field_name      VARCHAR(255) NOT NULL,
    search_value    VARCHAR(500),

    CONSTRAINT uq_case_search UNIQUE(case_id, query_def_id)
);

CREATE INDEX idx_case_search_case ON case_search_values(case_id);
CREATE INDEX idx_case_search_value ON case_search_values USING GIN(search_value gin_trgm_ops);

-- Händelselogg
CREATE TABLE case_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    event_type      VARCHAR(50) NOT NULL,

    -- Detaljer (JSON)
    data            JSONB NOT NULL DEFAULT '{}',

    -- Relaterade objekt
    old_status_id   UUID REFERENCES status_definitions(id),
    new_status_id   UUID REFERENCES status_definitions(id),

    -- Metadata
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,

    -- Sökbar beskrivning
    description     TEXT,

    CONSTRAINT chk_event_type CHECK (event_type IN (
        'CREATED', 'UPDATED', 'SUBMITTED', 'SAVED_DRAFT',
        'STATUS_CHANGED', 'ASSIGNED', 'UNASSIGNED',
        'MESSAGE_SENT', 'MESSAGE_READ',
        'ATTACHMENT_ADDED', 'ATTACHMENT_REMOVED',
        'SIGNED', 'SIGNATURE_REQUESTED',
        'PAYMENT_INITIATED', 'PAYMENT_COMPLETED', 'PAYMENT_FAILED',
        'EXPORTED', 'ARCHIVED', 'RESTORED',
        'COMMENT_ADDED', 'COMMENT_DELETED'
    ))
);

CREATE INDEX idx_case_events_case ON case_events(case_id);
CREATE INDEX idx_case_events_type ON case_events(event_type);
CREATE INDEX idx_case_events_created ON case_events(created_at DESC);

-- Interna meddelanden (handläggare till handläggare)
CREATE TABLE internal_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    message         TEXT NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID NOT NULL REFERENCES users(id),

    -- Soft delete
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID REFERENCES users(id)
);

CREATE INDEX idx_internal_messages_case ON internal_messages(case_id);
CREATE INDEX idx_internal_messages_created ON internal_messages(created_at DESC);

-- Externa meddelanden (till/från medborgare)
CREATE TABLE external_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    message         TEXT NOT NULL,

    -- Typ
    from_manager    BOOLEAN NOT NULL DEFAULT false,
    system_message  BOOLEAN NOT NULL DEFAULT false,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID NOT NULL REFERENCES users(id),

    -- Läskvitto
    read_at         TIMESTAMPTZ,
    read_by         UUID REFERENCES users(id)
);

CREATE INDEX idx_external_messages_case ON external_messages(case_id);
CREATE INDEX idx_external_messages_unread ON external_messages(case_id) WHERE read_at IS NULL;

-- Bilagor
CREATE TABLE attachments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Polymorf koppling
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,

    -- Filinformation
    file_id         UUID NOT NULL,  -- Referens till MinIO
    filename        VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100),
    size_bytes      BIGINT NOT NULL,

    -- Checksumma för integritet
    checksum        VARCHAR(64),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES users(id),

    CONSTRAINT chk_entity_type CHECK (entity_type IN (
        'CASE', 'QUERY_INSTANCE', 'INTERNAL_MESSAGE', 'EXTERNAL_MESSAGE'
    ))
);

CREATE INDEX idx_attachments_entity ON attachments(entity_type, entity_id);
CREATE INDEX idx_attachments_file ON attachments(file_id);

-- Dynamiska attribut för ärenden
CREATE TABLE case_attributes (
    case_id         UUID REFERENCES cases(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    value           TEXT,

    PRIMARY KEY (case_id, name)
);

CREATE INDEX idx_case_attributes_name ON case_attributes(name);

-- =====================================================
-- UTILITY FUNCTIONS
-- =====================================================

-- Funktion för att uppdatera updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Applicera trigger på relevanta tabeller
CREATE TRIGGER trg_flows_updated_at
    BEFORE UPDATE ON flows
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_cases_updated_at
    BEFORE UPDATE ON cases
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_flow_families_updated_at
    BEFORE UPDATE ON flow_families
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
