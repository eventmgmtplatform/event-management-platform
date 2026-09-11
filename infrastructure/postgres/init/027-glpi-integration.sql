\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_management.glpi_integration_state (
    tenant text NOT NULL,
    event_key text NOT NULL,
    event_id text NOT NULL,
    status text NOT NULL,
    ticket_id text,
    result jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant, event_key)
);
CREATE SCHEMA IF NOT EXISTS dashboard_read;
CREATE OR REPLACE VIEW dashboard_read.glpi AS
SELECT tenant || ':' || event_key AS id, tenant, status, event_id,
       ticket_id AS reference, updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, NULL::text AS outcome
FROM event_management.glpi_integration_state;
GRANT SELECT ON dashboard_read.glpi TO oem_dashboard_reader;
ALTER TABLE event_management.delivery_filter_target
    DROP CONSTRAINT IF EXISTS delivery_filter_target_target_check;
ALTER TABLE event_management.delivery_filter_target
    ADD CONSTRAINT delivery_filter_target_target_check
    CHECK (target IN ('gnm','snow','glpi','cacf','chatops','extensions'));
COMMIT;
