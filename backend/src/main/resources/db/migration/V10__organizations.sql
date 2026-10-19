-- =====================================================
-- Organisationer (fas 0, steg 1.2)
-- =====================================================
-- Staff work for one organisation at a time. Their roles are given per
-- organisation through a membership; global roles (USER for citizens,
-- OPERATIONS for the platform) stay in user_roles. E-services, categories,
-- cases and attachments belong to an organisation.
--
-- This migration adds the model and the data. Access is not yet restricted
-- by organisation; that comes with CaseAccessPolicy (steg 1.3) and RLS
-- (steg 1.4). The new organization_id columns are nullable for now and
-- become NOT NULL once all code writing them has been deployed.

-- ---------- Organisations ----------
ALTER TABLE organizations
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'MUNICIPALITY'
        CHECK (kind IN ('MUNICIPALITY', 'REGION', 'AGENCY', 'COLLABORATION', 'PLATFORM')),
    ADD COLUMN slug VARCHAR(60);

UPDATE organizations SET slug = 'exempelkommun' WHERE id = '00000000-0000-0000-0000-000000000010';

INSERT INTO organizations (id, name, org_number, email, kind, slug) VALUES
    ('00000000-0000-0000-0000-000000000011', 'Grannkommun', '212000-0001', 'kontakt@grannkommun.se',
     'MUNICIPALITY', 'grannkommun');

UPDATE organizations SET slug = 'org-' || substr(id::text, 1, 8) WHERE slug IS NULL;
ALTER TABLE organizations ALTER COLUMN slug SET NOT NULL;
ALTER TABLE organizations ADD CONSTRAINT uq_organizations_slug UNIQUE (slug);
ALTER TABLE organizations ADD CONSTRAINT chk_organizations_slug CHECK (slug ~ '^[a-z0-9][a-z0-9-]*$');

-- ---------- Memberships ----------
CREATE TABLE organization_members (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    -- Highest confidentiality level of cases the member may see (steg 1.3)
    clearance       VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (clearance IN ('NORMAL', 'RESTRICTED', 'CONFIDENTIAL')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organization_member UNIQUE (user_id, organization_id)
);

CREATE INDEX idx_organization_members_org ON organization_members(organization_id);

CREATE TABLE organization_member_roles (
    member_id UUID NOT NULL REFERENCES organization_members(id) ON DELETE CASCADE,
    role_id   UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, role_id)
);

-- Existing staff become members of their organisation, keeping their roles there
INSERT INTO organization_members (user_id, organization_id)
SELECT DISTINCT u.id, u.organization_id
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE u.organization_id IS NOT NULL
  AND r.name NOT IN ('USER', 'OPERATIONS');

INSERT INTO organization_member_roles (member_id, role_id)
SELECT m.id, ur.role_id
FROM organization_members m
JOIN user_roles ur ON ur.user_id = m.user_id
JOIN roles r ON r.id = ur.role_id
WHERE r.name NOT IN ('USER', 'OPERATIONS');

DELETE FROM user_roles ur
USING roles r
WHERE r.id = ur.role_id AND r.name NOT IN ('USER', 'OPERATIONS');

-- ---------- Owning organisation ----------
ALTER TABLE flow_families ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE flows         ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE categories    ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE cases         ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE attachments   ADD COLUMN organization_id UUID REFERENCES organizations(id);

-- Everything that exists so far belongs to Exempelkommun
UPDATE flow_families SET organization_id = '00000000-0000-0000-0000-000000000010';
UPDATE flows         SET organization_id = '00000000-0000-0000-0000-000000000010';
UPDATE categories    SET organization_id = '00000000-0000-0000-0000-000000000010';
UPDATE cases c       SET organization_id = f.organization_id FROM flows f WHERE f.id = c.flow_id;
UPDATE attachments a SET organization_id = c.organization_id FROM cases c WHERE c.id = a.case_id;
UPDATE attachments   SET organization_id = '00000000-0000-0000-0000-000000000010' WHERE organization_id IS NULL;

CREATE INDEX idx_flow_families_org ON flow_families(organization_id);
CREATE INDEX idx_flows_org         ON flows(organization_id);
CREATE INDEX idx_categories_org    ON categories(organization_id);
CREATE INDEX idx_cases_org_status  ON cases(organization_id, status_id);
CREATE INDEX idx_attachments_org   ON attachments(organization_id);

-- =====================================================
-- Grannkommun: personas, a category and an e-service
-- =====================================================

INSERT INTO users (id, email, username, first_name, last_name, display_name, organization_id, active, email_verified) VALUES
    ('00000000-0000-0000-0000-000000000110', 'admin.grannkommun@example.com', 'admin-grannkommun',
     'Greta', 'Admin', 'Greta Admin', '00000000-0000-0000-0000-000000000011', true, true),
    ('00000000-0000-0000-0000-000000000111', 'handlaggare.grannkommun@example.com', 'handlaggare-grannkommun',
     'Gustav', 'Handläggare', 'Gustav Handläggare', '00000000-0000-0000-0000-000000000011', true, true),
    ('00000000-0000-0000-0000-000000000112', 'informationssakerhet.grannkommun@example.com', 'informationssakerhet-grannkommun',
     'Gunilla', 'Säkerhet', 'Gunilla Säkerhet', '00000000-0000-0000-0000-000000000011', true, true);

INSERT INTO organization_members (id, user_id, organization_id) VALUES
    ('00000000-0000-0000-0009-000000000110', '00000000-0000-0000-0000-000000000110', '00000000-0000-0000-0000-000000000011'),
    ('00000000-0000-0000-0009-000000000111', '00000000-0000-0000-0000-000000000111', '00000000-0000-0000-0000-000000000011'),
    ('00000000-0000-0000-0009-000000000112', '00000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-000000000011');

INSERT INTO organization_member_roles (member_id, role_id) VALUES
    ('00000000-0000-0000-0009-000000000110', '00000000-0000-0000-0000-000000000001'),  -- ADMIN
    ('00000000-0000-0000-0009-000000000110', '00000000-0000-0000-0000-000000000004'),  -- FLOW_EDITOR
    ('00000000-0000-0000-0009-000000000111', '00000000-0000-0000-0000-000000000002'),  -- MANAGER
    ('00000000-0000-0000-0009-000000000112', '00000000-0000-0000-0000-000000000005');  -- SECURITY_OFFICER

INSERT INTO categories (id, flow_type_id, name, description, sort_order, organization_id) VALUES
    ('00000000-0000-0000-0002-000000000011', '00000000-0000-0000-0001-000000000004',
     'Skolskjuts', 'Ansökningar om skolskjuts', 1, '00000000-0000-0000-0000-000000000011');

INSERT INTO flow_families (id, name, description, icon, organization_id) VALUES
    ('00000000-0000-0000-0003-000000000011', 'Ansökan om skolskjuts',
     'Ansökan om skolskjuts för grundskoleelever', 'bus', '00000000-0000-0000-0000-000000000011');

INSERT INTO flows (
    id, family_id, type_id, category_id, name, version,
    short_description, long_description, submitted_message,
    enabled, require_auth, require_signing, status, tags, created_by, organization_id
) VALUES (
    '00000000-0000-0000-0004-000000000011',
    '00000000-0000-0000-0003-000000000011',
    '00000000-0000-0000-0001-000000000004',
    '00000000-0000-0000-0002-000000000011',
    'Ansökan om skolskjuts',
    1,
    'Ansök om skolskjuts för ditt barn i grundskolan',
    'Elever i grundskolan kan ha rätt till skolskjuts om avståndet till skolan är långt eller om det finns andra särskilda skäl.',
    'Tack för din ansökan! Du får besked inom 15 arbetsdagar.',
    true, true, false, 'PUBLISHED',
    ARRAY['skolskjuts', 'grundskola'],
    '00000000-0000-0000-0000-000000000110',
    '00000000-0000-0000-0000-000000000011'
);

INSERT INTO steps (id, flow_id, name, description, sort_order) VALUES
    ('00000000-0000-0000-0005-000000000011', '00000000-0000-0000-0004-000000000011',
     'Eleven', 'Uppgifter om eleven och skolan', 0),
    ('00000000-0000-0000-0005-000000000012', '00000000-0000-0000-0004-000000000011',
     'Skäl', 'Varför behövs skolskjuts?', 1);

INSERT INTO query_definitions (id, step_id, name, description, query_type, config, required, sort_order) VALUES
    ('00000000-0000-0000-0006-000000001101', '00000000-0000-0000-0005-000000000011',
     'Elevens namn', NULL, 'TEXT', '{"maxLength": 100}', true, 0),
    ('00000000-0000-0000-0006-000000001102', '00000000-0000-0000-0005-000000000011',
     'Skola', 'Skolan eleven går i', 'TEXT', '{"maxLength": 200}', true, 1),
    ('00000000-0000-0000-0006-000000001103', '00000000-0000-0000-0005-000000000011',
     'Årskurs', NULL, 'SELECT',
     '{"options": [{"value": "F", "label": "Förskoleklass"}, {"value": "1-3", "label": "Årskurs 1–3"}, {"value": "4-6", "label": "Årskurs 4–6"}, {"value": "7-9", "label": "Årskurs 7–9"}]}',
     true, 2),
    ('00000000-0000-0000-0006-000000001104', '00000000-0000-0000-0005-000000000012',
     'Avstånd till skolan (km)', 'Kortaste gångväg från hemmet', 'NUMBER', '{"min": 0, "max": 100}', true, 0),
    ('00000000-0000-0000-0006-000000001105', '00000000-0000-0000-0005-000000000012',
     'Andra skäl', 'Till exempel trafikfarlig väg eller funktionsnedsättning', 'TEXTAREA',
     '{"maxLength": 2000}', false, 1);

INSERT INTO status_definitions (id, flow_id, name, description, internal_description, status_type, color, sort_order, handling_days, user_can_message) VALUES
    ('00000000-0000-0000-0008-000000001101', '00000000-0000-0000-0004-000000000011',
     'Utkast', 'Din ansökan är sparad som utkast', 'Ej inskickad', 'DRAFT', '#6b7280', 0, NULL, false),
    ('00000000-0000-0000-0008-000000001102', '00000000-0000-0000-0004-000000000011',
     'Inskickad', 'Din ansökan har tagits emot', 'Inväntar handläggning', 'SUBMITTED', '#3b82f6', 1, 15, true),
    ('00000000-0000-0000-0008-000000001103', '00000000-0000-0000-0004-000000000011',
     'Beviljad', 'Skolskjuts har beviljats', 'Beslut fattat - bifall', 'APPROVED', '#22c55e', 2, NULL, true),
    ('00000000-0000-0000-0008-000000001104', '00000000-0000-0000-0004-000000000011',
     'Avslagen', 'Ansökan har avslagits', 'Beslut fattat - avslag', 'REJECTED', '#dc2626', 3, NULL, true);

INSERT INTO status_transitions (from_status_id, to_status_id, requires_comment) VALUES
    ('00000000-0000-0000-0008-000000001101', '00000000-0000-0000-0008-000000001102', false),
    ('00000000-0000-0000-0008-000000001102', '00000000-0000-0000-0008-000000001103', true),
    ('00000000-0000-0000-0008-000000001102', '00000000-0000-0000-0008-000000001104', true);
