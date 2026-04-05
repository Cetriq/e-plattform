-- =====================================================
-- SEED DATA FOR DEVELOPMENT
-- =====================================================

-- Systemroller
INSERT INTO roles (id, name, description, permissions, is_system) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'Systemadministratör med full åtkomst',
     ARRAY['*'], true),
    ('00000000-0000-0000-0000-000000000002', 'MANAGER', 'Handläggare som kan hantera ärenden',
     ARRAY['cases:read', 'cases:update', 'cases:assign', 'messages:send', 'flows:read'], true),
    ('00000000-0000-0000-0000-000000000003', 'USER', 'Standardanvändare (medborgare)',
     ARRAY['cases:create', 'cases:read:own', 'cases:update:own', 'messages:send:own'], true),
    ('00000000-0000-0000-0000-000000000004', 'FLOW_EDITOR', 'Kan skapa och redigera formulär',
     ARRAY['flows:*', 'categories:*'], true);

-- Standardorganisation
INSERT INTO organizations (id, name, org_number, email) VALUES
    ('00000000-0000-0000-0000-000000000010', 'Exempelkommun', '212000-0000', 'kontakt@exempelkommun.se');

-- Standardgrupper
INSERT INTO groups (id, organization_id, name, description, is_system) VALUES
    ('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000010',
     'Bygglovshandläggare', 'Handlägger bygglovsärenden', false),
    ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000010',
     'Miljöhandläggare', 'Handlägger miljöärenden', false),
    ('00000000-0000-0000-0000-000000000022', '00000000-0000-0000-0000-000000000010',
     'Kundtjänst', 'Hanterar inkommande ärenden', false);

-- Testanvändare (för utveckling)
INSERT INTO users (id, external_id, email, username, first_name, last_name, display_name, organization_id, active, email_verified) VALUES
    ('00000000-0000-0000-0000-000000000100', '199001011234', 'admin@example.com', 'admin',
     'Admin', 'Adminsson', 'Admin Adminsson', '00000000-0000-0000-0000-000000000010', true, true),
    ('00000000-0000-0000-0000-000000000101', '198505052345', 'handlaggare@example.com', 'handlaggare',
     'Hans', 'Handläggare', 'Hans Handläggare', '00000000-0000-0000-0000-000000000010', true, true),
    ('00000000-0000-0000-0000-000000000102', '197512123456', 'medborgare@example.com', 'medborgare',
     'Maria', 'Medborgare', 'Maria Medborgare', NULL, true, true);

-- Tilldela roller till testanvändare
INSERT INTO user_roles (user_id, role_id) VALUES
    ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000001'),  -- Admin
    ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000004'),  -- Flow editor
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000002'),  -- Manager
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000003');  -- User

-- Tilldela grupper
INSERT INTO user_groups (user_id, group_id) VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000020');  -- Bygglov

-- =====================================================
-- EXEMPELFLÖDEN
-- =====================================================

-- Tjänsttyper
INSERT INTO flow_types (id, name, description, color, icon, sort_order) VALUES
    ('00000000-0000-0000-0001-000000000001', 'Byggande & Boende',
     'Tjänster relaterade till bygglov, bostadsanpassning och liknande', '#2563eb', 'building', 1),
    ('00000000-0000-0000-0001-000000000002', 'Miljö & Hälsa',
     'Tjänster relaterade till miljötillstånd och hälsoskydd', '#16a34a', 'leaf', 2),
    ('00000000-0000-0000-0001-000000000003', 'Omsorg & Stöd',
     'Tjänster för äldreomsorg, funktionsstöd och liknande', '#9333ea', 'heart', 3),
    ('00000000-0000-0000-0001-000000000004', 'Skola & Förskola',
     'Ansökningar till förskola, fritids och skolval', '#f59e0b', 'graduation-cap', 4);

-- Kategorier
INSERT INTO categories (id, flow_type_id, name, description, sort_order) VALUES
    -- Byggande & Boende
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0001-000000000001',
     'Bygglov', 'Ansökningar om bygglov och förhandsbesked', 1),
    ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0001-000000000001',
     'Bostadsanpassning', 'Ansökningar om bostadsanpassningsbidrag', 2),
    -- Miljö & Hälsa
    ('00000000-0000-0000-0002-000000000003', '00000000-0000-0000-0001-000000000002',
     'Tillstånd', 'Miljötillstånd och anmälningar', 1),
    -- Skola & Förskola
    ('00000000-0000-0000-0002-000000000004', '00000000-0000-0000-0001-000000000004',
     'Förskola', 'Ansökningar till förskola', 1);

-- Tjänstfamilj
INSERT INTO flow_families (id, name, description, icon) VALUES
    ('00000000-0000-0000-0003-000000000001', 'Ansökan om bygglov',
     'Formulär för att ansöka om bygglov för olika typer av byggnationer', 'file-text');

-- Exempelflöde: Enkel bygglovsansökan
INSERT INTO flows (
    id, family_id, type_id, category_id, name, version,
    short_description, long_description, submitted_message,
    enabled, require_auth, require_signing, status, tags, created_by
) VALUES (
    '00000000-0000-0000-0004-000000000001',
    '00000000-0000-0000-0003-000000000001',
    '00000000-0000-0000-0001-000000000001',
    '00000000-0000-0000-0002-000000000001',
    'Ansökan om bygglov',
    1,
    'Ansök om bygglov för nybyggnad, tillbyggnad eller ändring',
    'Använd denna e-tjänst för att ansöka om bygglov. Du behöver ha ritningar och teknisk beskrivning redo innan du börjar.',
    'Tack för din ansökan! Vi kommer att behandla ditt ärende och återkomma inom 10 arbetsdagar.',
    true,
    true,
    false,
    'PUBLISHED',
    ARRAY['bygglov', 'nybyggnad', 'tillbyggnad'],
    '00000000-0000-0000-0000-000000000100'
);

-- Steg i bygglovsformuläret
INSERT INTO steps (id, flow_id, name, description, sort_order) VALUES
    ('00000000-0000-0000-0005-000000000001', '00000000-0000-0000-0004-000000000001',
     'Sökande', 'Information om den som söker bygglov', 0),
    ('00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0004-000000000001',
     'Fastighet', 'Information om fastigheten', 1),
    ('00000000-0000-0000-0005-000000000003', '00000000-0000-0000-0004-000000000001',
     'Åtgärd', 'Beskriv vad du vill bygga', 2),
    ('00000000-0000-0000-0005-000000000004', '00000000-0000-0000-0004-000000000001',
     'Bilagor', 'Ladda upp ritningar och dokument', 3);

-- Frågedefinitioner för Steg 1: Sökande
INSERT INTO query_definitions (id, step_id, name, description, query_type, config, required, sort_order) VALUES
    ('00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000001',
     'Sökande typ', 'Är du privatperson eller företag?', 'RADIO',
     '{"options": [{"value": "private", "label": "Privatperson"}, {"value": "company", "label": "Företag/Organisation"}]}',
     true, 0),
    ('00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0005-000000000001',
     'Förnamn', NULL, 'TEXT',
     '{"maxLength": 100}',
     true, 1),
    ('00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0005-000000000001',
     'Efternamn', NULL, 'TEXT',
     '{"maxLength": 100}',
     true, 2),
    ('00000000-0000-0000-0006-000000000004', '00000000-0000-0000-0005-000000000001',
     'Organisationsnamn', 'Ange företagets eller organisationens namn', 'TEXT',
     '{"maxLength": 200}',
     false, 3),
    ('00000000-0000-0000-0006-000000000005', '00000000-0000-0000-0005-000000000001',
     'Organisationsnummer', NULL, 'TEXT',
     '{"pattern": "^[0-9]{6}-[0-9]{4}$", "placeholder": "XXXXXX-XXXX"}',
     false, 4),
    ('00000000-0000-0000-0006-000000000006', '00000000-0000-0000-0005-000000000001',
     'E-post', NULL, 'EMAIL',
     '{}',
     true, 5),
    ('00000000-0000-0000-0006-000000000007', '00000000-0000-0000-0005-000000000001',
     'Telefon', NULL, 'PHONE',
     '{}',
     true, 6);

-- Evaluator: Visa organisationsfält endast för företag
INSERT INTO evaluator_definitions (id, query_id, name, evaluator_type, condition, target_query_ids, target_state, enabled) VALUES
    ('00000000-0000-0000-0007-000000000001',
     '00000000-0000-0000-0006-000000000001',
     'Visa organisationsfält för företag',
     'VALUE_EQUALS',
     '{"value": "company"}',
     ARRAY['00000000-0000-0000-0006-000000000004'::uuid, '00000000-0000-0000-0006-000000000005'::uuid],
     'VISIBLE_REQUIRED',
     true);

-- Frågedefinitioner för Steg 2: Fastighet
INSERT INTO query_definitions (id, step_id, name, description, help_text, query_type, config, required, sort_order) VALUES
    ('00000000-0000-0000-0006-000000000010', '00000000-0000-0000-0005-000000000002',
     'Fastighetsbeteckning', 'T.ex. Staden 1:23', 'Du hittar fastighetsbeteckningen på ditt lagfartsbevis eller via lantmäteriet', 'TEXT',
     '{"maxLength": 100}',
     true, 0),
    ('00000000-0000-0000-0006-000000000011', '00000000-0000-0000-0005-000000000002',
     'Adress', 'Fastighetens adress', NULL, 'TEXT',
     '{"maxLength": 200}',
     true, 1),
    ('00000000-0000-0000-0006-000000000012', '00000000-0000-0000-0005-000000000002',
     'Postnummer', NULL, NULL, 'TEXT',
     '{"pattern": "^[0-9]{5}$", "maxLength": 5}',
     true, 2),
    ('00000000-0000-0000-0006-000000000013', '00000000-0000-0000-0005-000000000002',
     'Ort', NULL, NULL, 'TEXT',
     '{"maxLength": 100}',
     true, 3);

-- Frågedefinitioner för Steg 3: Åtgärd
INSERT INTO query_definitions (id, step_id, name, description, query_type, config, required, sort_order) VALUES
    ('00000000-0000-0000-0006-000000000020', '00000000-0000-0000-0005-000000000003',
     'Typ av åtgärd', 'Vad vill du göra?', 'SELECT',
     '{"options": [
        {"value": "new_building", "label": "Nybyggnad"},
        {"value": "extension", "label": "Tillbyggnad"},
        {"value": "change", "label": "Ändring av byggnad"},
        {"value": "demolition", "label": "Rivning"},
        {"value": "other", "label": "Annat"}
     ]}',
     true, 0),
    ('00000000-0000-0000-0006-000000000021', '00000000-0000-0000-0005-000000000003',
     'Typ av byggnad', 'Vad är det för typ av byggnad?', 'SELECT',
     '{"options": [
        {"value": "house", "label": "En- eller tvåbostadshus"},
        {"value": "garage", "label": "Garage/Carport"},
        {"value": "shed", "label": "Förråd/Uthus"},
        {"value": "commercial", "label": "Kommersiell byggnad"},
        {"value": "other", "label": "Annat"}
     ]}',
     true, 1),
    ('00000000-0000-0000-0006-000000000022', '00000000-0000-0000-0005-000000000003',
     'Beskrivning av åtgärd', 'Beskriv kortfattat vad du planerar att göra', 'TEXTAREA',
     '{"maxLength": 2000, "rows": 5}',
     true, 2),
    ('00000000-0000-0000-0006-000000000023', '00000000-0000-0000-0005-000000000003',
     'Byggnadsarea (kvm)', 'Total byggnadsarea i kvadratmeter', 'NUMBER',
     '{"min": 1, "max": 10000}',
     true, 3),
    ('00000000-0000-0000-0006-000000000024', '00000000-0000-0000-0005-000000000003',
     'Beräknad byggstart', 'När planerar du att påbörja byggnationen?', 'DATE',
     '{"minDate": "today"}',
     true, 4);

-- Frågedefinitioner för Steg 4: Bilagor
INSERT INTO query_definitions (id, step_id, name, description, help_text, query_type, config, required, sort_order) VALUES
    ('00000000-0000-0000-0006-000000000030', '00000000-0000-0000-0005-000000000004',
     'Situationsplan', 'Ladda upp situationsplan', 'Situationsplanen ska visa byggnadens placering på tomten', 'FILE',
     '{"accept": ".pdf,.jpg,.png", "maxSize": 10485760, "maxFiles": 3}',
     true, 0),
    ('00000000-0000-0000-0006-000000000031', '00000000-0000-0000-0005-000000000004',
     'Planritning', 'Ladda upp planritning', 'Planritningen ska visa rumsindelning och mått', 'FILE',
     '{"accept": ".pdf,.jpg,.png", "maxSize": 10485760, "maxFiles": 5}',
     true, 1),
    ('00000000-0000-0000-0006-000000000032', '00000000-0000-0000-0005-000000000004',
     'Fasadritning', 'Ladda upp fasadritningar', 'Fasadritningar för alla sidor av byggnaden', 'FILE',
     '{"accept": ".pdf,.jpg,.png", "maxSize": 10485760, "maxFiles": 5}',
     true, 2),
    ('00000000-0000-0000-0006-000000000033', '00000000-0000-0000-0005-000000000004',
     'Övriga handlingar', 'Ladda upp övriga relevanta dokument', NULL, 'FILE',
     '{"accept": ".pdf,.jpg,.png,.doc,.docx", "maxSize": 10485760, "maxFiles": 10}',
     false, 3);

-- Statusdefinitioner för bygglovsflödet
INSERT INTO status_definitions (id, flow_id, name, description, internal_description, status_type, color, sort_order, handling_days, user_can_message) VALUES
    ('00000000-0000-0000-0008-000000000001', '00000000-0000-0000-0004-000000000001',
     'Utkast', 'Din ansökan är sparad som utkast', 'Ej inskickad', 'DRAFT', '#6b7280', 0, NULL, false),
    ('00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0004-000000000001',
     'Inskickad', 'Din ansökan har tagits emot', 'Inväntar granskning', 'SUBMITTED', '#3b82f6', 1, 5, true),
    ('00000000-0000-0000-0008-000000000003', '00000000-0000-0000-0004-000000000001',
     'Under granskning', 'Din ansökan granskas av en handläggare', 'Under aktiv handläggning', 'IN_PROGRESS', '#f59e0b', 2, 10, true),
    ('00000000-0000-0000-0008-000000000004', '00000000-0000-0000-0004-000000000001',
     'Komplettering begärd', 'Vi behöver mer information', 'Inväntar komplettering från sökande', 'WAITING_FOR_USER', '#ef4444', 3, NULL, true),
    ('00000000-0000-0000-0008-000000000005', '00000000-0000-0000-0004-000000000001',
     'Godkänd', 'Din ansökan har beviljats', 'Beslut fattat - bifall', 'APPROVED', '#22c55e', 4, NULL, true),
    ('00000000-0000-0000-0008-000000000006', '00000000-0000-0000-0004-000000000001',
     'Avslagen', 'Din ansökan har avslagits', 'Beslut fattat - avslag', 'REJECTED', '#dc2626', 5, NULL, true),
    ('00000000-0000-0000-0008-000000000007', '00000000-0000-0000-0004-000000000001',
     'Avslutad', 'Ärendet är avslutat', 'Arkiverat', 'COMPLETED', '#6b7280', 6, NULL, false);

-- Statusövergångar
INSERT INTO status_transitions (from_status_id, to_status_id, requires_comment) VALUES
    -- Från Utkast
    ('00000000-0000-0000-0008-000000000001', '00000000-0000-0000-0008-000000000002', false),
    -- Från Inskickad
    ('00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0008-000000000003', false),
    ('00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0008-000000000004', true),
    -- Från Under granskning
    ('00000000-0000-0000-0008-000000000003', '00000000-0000-0000-0008-000000000004', true),
    ('00000000-0000-0000-0008-000000000003', '00000000-0000-0000-0008-000000000005', true),
    ('00000000-0000-0000-0008-000000000003', '00000000-0000-0000-0008-000000000006', true),
    -- Från Komplettering begärd
    ('00000000-0000-0000-0008-000000000004', '00000000-0000-0000-0008-000000000003', false),
    ('00000000-0000-0000-0008-000000000004', '00000000-0000-0000-0008-000000000006', true),
    -- Från Godkänd/Avslagen till Avslutad
    ('00000000-0000-0000-0008-000000000005', '00000000-0000-0000-0008-000000000007', false),
    ('00000000-0000-0000-0008-000000000006', '00000000-0000-0000-0008-000000000007', false);
