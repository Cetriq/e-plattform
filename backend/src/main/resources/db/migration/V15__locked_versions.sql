-- =====================================================
-- Oföränderliga tjänsteversioner (fas 0, steg 2a)
-- =====================================================
-- A published version of an e-service can no longer be changed, through the
-- API or in the database: cases created on it must keep seeing exactly what
-- they were created on. Changes are made in a new draft version of the same
-- family. Publishing it marks the previous version SUPERSEDED: it takes no
-- new cases, but its existing cases continue.

ALTER TABLE flows DROP CONSTRAINT chk_flow_status;
ALTER TABLE flows ADD CONSTRAINT chk_flow_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'ARCHIVED'));

-- Every e-service belongs to a family (versions of one service)
INSERT INTO flow_families (id, name, description, organization_id)
SELECT f.id, f.name, f.short_description, f.organization_id
FROM flows f WHERE f.family_id IS NULL;
UPDATE flows SET family_id = id WHERE family_id IS NULL;
ALTER TABLE flows ALTER COLUMN family_id SET NOT NULL;

-- ---------- The version itself ----------
-- Once published, only these may change: the status (PUBLISHED to SUPERSEDED
-- or ARCHIVED, SUPERSEDED to ARCHIVED), whether it is enabled, the unpublish
-- date, and updated_at. A published version cannot be deleted.
CREATE FUNCTION flows_locked_when_published() RETURNS trigger AS $$
DECLARE
    unchanged CONSTANT TEXT[] := ARRAY['status', 'enabled', 'unpublish_date', 'updated_at'];
BEGIN
    IF OLD.status = 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Version % of e-service % is % and cannot be deleted', OLD.version, OLD.family_id, OLD.status;
    END IF;
    IF (to_jsonb(NEW) - unchanged) IS DISTINCT FROM (to_jsonb(OLD) - unchanged) THEN
        RAISE EXCEPTION 'Version % of e-service % is % and cannot be changed', OLD.version, OLD.family_id, OLD.status;
    END IF;
    IF NEW.status <> OLD.status AND NOT (
           (OLD.status = 'PUBLISHED' AND NEW.status IN ('SUPERSEDED', 'ARCHIVED'))
        OR (OLD.status = 'SUPERSEDED' AND NEW.status = 'ARCHIVED')) THEN
        RAISE EXCEPTION 'Status % cannot change to %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_flows_locked
    BEFORE UPDATE OR DELETE ON flows
    FOR EACH ROW EXECUTE FUNCTION flows_locked_when_published();

-- ---------- Its parts ----------
-- Steps, fields, conditions, statuses and transitions of a version that is not
-- a draft can be neither added, changed nor removed.
CREATE FUNCTION flow_part_locked(flow UUID, part TEXT) RETURNS VOID AS $$
DECLARE
    current_status VARCHAR(20);
BEGIN
    SELECT status INTO current_status FROM flows WHERE id = flow;
    -- No row: the version itself is being deleted (only drafts can be)
    IF current_status IS NOT NULL AND current_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'The % of a % e-service version cannot be changed', part, current_status;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION steps_locked() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN PERFORM flow_part_locked(OLD.flow_id, 'steps'); END IF;
    IF TG_OP <> 'DELETE' THEN PERFORM flow_part_locked(NEW.flow_id, 'steps'); RETURN NEW; END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION query_definitions_locked() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM flow_part_locked((SELECT flow_id FROM steps WHERE id = OLD.step_id), 'fields');
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM flow_part_locked((SELECT flow_id FROM steps WHERE id = NEW.step_id), 'fields');
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION evaluator_definitions_locked() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM flow_part_locked((SELECT s.flow_id FROM query_definitions q JOIN steps s ON s.id = q.step_id
                                  WHERE q.id = OLD.query_id), 'conditions');
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM flow_part_locked((SELECT s.flow_id FROM query_definitions q JOIN steps s ON s.id = q.step_id
                                  WHERE q.id = NEW.query_id), 'conditions');
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION status_definitions_locked() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN PERFORM flow_part_locked(OLD.flow_id, 'statuses'); END IF;
    IF TG_OP <> 'DELETE' THEN PERFORM flow_part_locked(NEW.flow_id, 'statuses'); RETURN NEW; END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION status_transitions_locked() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM flow_part_locked((SELECT flow_id FROM status_definitions WHERE id = OLD.from_status_id), 'status transitions');
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM flow_part_locked((SELECT flow_id FROM status_definitions WHERE id = NEW.from_status_id), 'status transitions');
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_steps_locked BEFORE INSERT OR UPDATE OR DELETE ON steps
    FOR EACH ROW EXECUTE FUNCTION steps_locked();
CREATE TRIGGER trg_query_definitions_locked BEFORE INSERT OR UPDATE OR DELETE ON query_definitions
    FOR EACH ROW EXECUTE FUNCTION query_definitions_locked();
CREATE TRIGGER trg_evaluator_definitions_locked BEFORE INSERT OR UPDATE OR DELETE ON evaluator_definitions
    FOR EACH ROW EXECUTE FUNCTION evaluator_definitions_locked();
CREATE TRIGGER trg_status_definitions_locked BEFORE INSERT OR UPDATE OR DELETE ON status_definitions
    FOR EACH ROW EXECUTE FUNCTION status_definitions_locked();
CREATE TRIGGER trg_status_transitions_locked BEFORE INSERT OR UPDATE OR DELETE ON status_transitions
    FOR EACH ROW EXECUTE FUNCTION status_transitions_locked();
