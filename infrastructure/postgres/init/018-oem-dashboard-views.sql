\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA IF NOT EXISTS dashboard_read;

-- Stable reporting boundary. No raw payloads, provider messages or credentials.
CREATE OR REPLACE VIEW dashboard_read.events AS
SELECT event_key::text AS id, tenant::text, lifecycle_status::text AS status,
       event_id::text, ticket_number::text AS reference, last_updated_at AS updated_at,
       effective_severity AS severity, tally, NULL::text AS outcome
FROM event_management.event_state;

-- These count event aggregates with an integration, NOT unique provider tickets/incidents.
CREATE OR REPLACE VIEW dashboard_read.ticketing AS
SELECT event_key::text AS id, tenant::text, servicenow_status::text AS status,
       event_id::text, ticket_number::text AS reference, last_updated_at AS updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, NULL::text AS outcome
FROM event_management.event_state
WHERE servicenow_status <> 'NOT_REQUIRED' OR ticket_number IS NOT NULL;

CREATE OR REPLACE VIEW dashboard_read.gnm AS
SELECT event_key::text AS id, tenant::text, gnm_status::text AS status,
       event_id::text, notification_id::text AS reference, last_updated_at AS updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, NULL::text AS outcome
FROM event_management.event_state
WHERE gnm_status <> 'NOT_REQUIRED' OR notification_id IS NOT NULL;

CREATE OR REPLACE VIEW dashboard_read.cacf AS
SELECT execution_id::text AS id, customer_code::text AS tenant, state::text AS status,
       event_id::text, provider_execution_id::text AS reference, updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, outcome::text
FROM event_management.automation_execution;

-- Group role only: provision a separate LOGIN with a secret outside this migration.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'oem_dashboard_reader') THEN
        CREATE ROLE oem_dashboard_reader NOLOGIN;
    END IF;
END $$;
GRANT USAGE ON SCHEMA dashboard_read TO oem_dashboard_reader;
GRANT SELECT ON dashboard_read.events, dashboard_read.ticketing,
    dashboard_read.gnm, dashboard_read.cacf TO oem_dashboard_reader;
COMMENT ON SCHEMA dashboard_read IS 'OEM read contract v1; read-only views, no ingestion ownership';
COMMIT;
