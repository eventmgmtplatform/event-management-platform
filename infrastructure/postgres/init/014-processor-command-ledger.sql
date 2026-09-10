\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_processor.integration_command (
    command_id CHAR(64) PRIMARY KEY,
    processing_id VARCHAR(64) NOT NULL REFERENCES event_processor.processing_record(processing_id),
    tenant TEXT NOT NULL,
    envelope JSONB NOT NULL CHECK(envelope->>'commandId'=command_id AND envelope->>'tenant'=tenant),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS processor_command_tenant ON event_processor.integration_command(tenant,command_id);
CREATE OR REPLACE TRIGGER immutable_integration_command BEFORE UPDATE OR DELETE ON event_processor.integration_command
    FOR EACH ROW EXECUTE FUNCTION event_processor.reject_rule_history_mutation();
COMMIT;
