-- The application connects as this role, which owns nothing and is bound by
-- row level security (migration V14). Flyway keeps running as eplatform.
-- Runs only when the database volume is created. For an existing volume:
--   docker exec eplatform-db psql -U eplatform -c "CREATE ROLE eplatform_app LOGIN PASSWORD 'app-dev'"
CREATE ROLE eplatform_app LOGIN PASSWORD 'app-dev';
