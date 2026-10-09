-- The handläggare responsible for a case. (The case_managers table from V1
-- supports several managers and groups, but nothing uses it yet; one
-- responsible person is what the manager view needs.)
ALTER TABLE cases ADD COLUMN assigned_to UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_cases_assigned_to ON cases(assigned_to);
