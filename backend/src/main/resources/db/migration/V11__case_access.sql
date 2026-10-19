-- =====================================================
-- Behörighet till ärenden (fas 0, steg 1.3a)
-- =====================================================
-- Staff may reach a case only when all four hold:
--   1. organisation  the case belongs to the organisation they work for
--   2. role          MANAGER or ADMIN there
--   3. scope         the e-service's access_scope: the whole organisation,
--                    the case's handling group, or only the assigned case worker
--   4. secrecy       the case's confidentiality is at most their clearance
-- See CaseAccessPolicy; lists and single reads use the same condition.

ALTER TABLE flows
    ADD COLUMN access_scope VARCHAR(20) NOT NULL DEFAULT 'ORGANIZATION'
        CHECK (access_scope IN ('ORGANIZATION', 'GROUP', 'ASSIGNED'));

ALTER TABLE cases
    ADD COLUMN handling_group_id UUID REFERENCES groups(id) ON DELETE SET NULL,
    ADD COLUMN confidentiality VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (confidentiality IN ('NORMAL', 'RESTRICTED', 'CONFIDENTIAL'));

CREATE INDEX idx_cases_handling_group ON cases(handling_group_id);
