-- Gallring logs in as this role and can only run the procedures in the schema
-- "retention" (migration V9). In deployed environments LOGIN and the password
-- are set by hand; locally a fixed development password is enough.
-- Runs only when the database volume is created. For an existing volume:
--   docker exec eplatform-db psql -U eplatform -c "CREATE ROLE eplatform_retention LOGIN PASSWORD 'retention-dev'"
CREATE ROLE eplatform_retention LOGIN PASSWORD 'retention-dev';
