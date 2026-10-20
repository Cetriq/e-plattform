-- =====================================================
-- Tjänsttyper per organisation (fas 0, steg 1.3b)
-- =====================================================
-- The existing service types are shared by every organisation (organization_id
-- NULL) and changed only through migrations. Types an organisation adds itself
-- belong to it, and only its administrators see and change them.

ALTER TABLE flow_types ADD COLUMN organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE;
CREATE INDEX idx_flow_types_org ON flow_types(organization_id);
