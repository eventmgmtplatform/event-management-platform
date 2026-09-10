\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA IF NOT EXISTS dashboard_read;
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='oem_dashboard_reader') THEN CREATE ROLE oem_dashboard_reader NOLOGIN; END IF;
END $$;
CREATE TABLE IF NOT EXISTS event_management.gateway_receipt (
    receipt_id uuid PRIMARY KEY,
    received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    content_type varchar(256),
    original_body bytea NOT NULL CHECK (octet_length(original_body) <= 1048576),
    body_sha256 char(64) NOT NULL CHECK (body_sha256 ~ '^[0-9a-f]{64}$'),
    byte_count integer GENERATED ALWAYS AS (octet_length(original_body)) STORED
);
CREATE TABLE IF NOT EXISTS event_management.gateway_receipt_status (
    receipt_id uuid PRIMARY KEY REFERENCES event_management.gateway_receipt,
    status varchar(32) NOT NULL CHECK (status IN ('RECEIVED','VALIDATED','PUBLISHED','REJECTED','DELIVERY_FAILED','PUBLISH_UNCONFIRMED')),
    event_id text, event_key text, customer_code text, source_system text,
    severity smallint CHECK (severity BETWEEN 0 AND 5),
    error_code varchar(64),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS gateway_receipt_time ON event_management.gateway_receipt(received_at DESC,receipt_id);
CREATE INDEX IF NOT EXISTS gateway_receipt_status_event ON event_management.gateway_receipt_status(event_id);
CREATE INDEX IF NOT EXISTS gateway_receipt_status_customer ON event_management.gateway_receipt_status(customer_code,status);
CREATE OR REPLACE FUNCTION event_management.protect_gateway_original()
RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    RAISE EXCEPTION 'Gateway original receipts are append-only';
END $$;
DROP TRIGGER IF EXISTS protect_gateway_original ON event_management.gateway_receipt;
CREATE TRIGGER protect_gateway_original BEFORE UPDATE OR DELETE ON event_management.gateway_receipt
FOR EACH ROW EXECUTE FUNCTION event_management.protect_gateway_original();

CREATE OR REPLACE VIEW dashboard_read.data_collection AS
SELECT r.receipt_id::text AS id,r.received_at,r.content_type,r.body_sha256::text,r.byte_count,
       s.status,s.event_id,s.event_key,s.customer_code,s.source_system,s.severity,s.error_code,s.updated_at
FROM event_management.gateway_receipt r JOIN event_management.gateway_receipt_status s USING(receipt_id);
CREATE OR REPLACE VIEW dashboard_read.data_collection_today AS
SELECT * FROM dashboard_read.data_collection
WHERE received_at >= (date_trunc('day',CURRENT_TIMESTAMP AT TIME ZONE 'America/Mexico_City') AT TIME ZONE 'America/Mexico_City');
CREATE OR REPLACE VIEW dashboard_read.data_collection_history AS
SELECT * FROM dashboard_read.data_collection
WHERE received_at < (date_trunc('day',CURRENT_TIMESTAMP AT TIME ZONE 'America/Mexico_City') AT TIME ZONE 'America/Mexico_City');
CREATE OR REPLACE VIEW dashboard_read.data_collection_original AS
SELECT receipt_id::text AS id,original_body FROM event_management.gateway_receipt;
GRANT SELECT ON dashboard_read.data_collection,dashboard_read.data_collection_today,
    dashboard_read.data_collection_history,dashboard_read.data_collection_original TO oem_dashboard_reader;
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='oem_gateway_collector') THEN CREATE ROLE oem_gateway_collector NOLOGIN; END IF;
END $$;
GRANT USAGE ON SCHEMA event_management TO oem_gateway_collector;
GRANT INSERT,SELECT ON event_management.gateway_receipt TO oem_gateway_collector;
GRANT INSERT,SELECT,UPDATE ON event_management.gateway_receipt_status TO oem_gateway_collector;
COMMENT ON TABLE event_management.gateway_receipt IS 'Immutable original HTTP body persisted before validation/Kafka. No retention deletion or fabricated backfill.';
COMMIT;
