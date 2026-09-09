\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA IF NOT EXISTS event_processor;
CREATE TABLE IF NOT EXISTS event_processor.processing_record (
    processing_id VARCHAR(64) PRIMARY KEY,
    input_hash VARCHAR(64) NOT NULL,
    event_id TEXT,
    tenant TEXT NOT NULL,
    evidence JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS processor_event_lookup
    ON event_processor.processing_record (tenant, event_id);
CREATE TABLE IF NOT EXISTS event_processor.output_outbox (
    message_id VARCHAR(64) PRIMARY KEY,
    processing_id VARCHAR(64) NOT NULL REFERENCES event_processor.processing_record(processing_id),
    topic TEXT NOT NULL,
    message_key TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS processor_pending_output
    ON event_processor.output_outbox (created_at, message_id) WHERE published_at IS NULL;
COMMIT;
