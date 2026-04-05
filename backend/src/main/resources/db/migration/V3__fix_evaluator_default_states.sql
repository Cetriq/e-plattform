-- Fix default_state for organization fields to HIDDEN
-- These should only be visible when "company" is selected in applicant type

UPDATE query_definitions
SET default_state = 'HIDDEN'
WHERE id IN (
    '00000000-0000-0000-0006-000000000004',  -- Organisationsnamn
    '00000000-0000-0000-0006-000000000005'   -- Organisationsnummer
);
