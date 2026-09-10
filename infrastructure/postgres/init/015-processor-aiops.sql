\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_processor.aiops_configuration (
    tenant TEXT NOT NULL,
    id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    deleted BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (tenant,id)
);
CREATE TABLE IF NOT EXISTS event_processor.aiops_change (
    tenant TEXT NOT NULL,
    id VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    actor TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('CREATE','UPDATE','DELETE')),
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant,id,revision),
    FOREIGN KEY (tenant,id) REFERENCES event_processor.aiops_configuration(tenant,id)
);
CREATE OR REPLACE TRIGGER immutable_aiops_change BEFORE UPDATE OR DELETE ON event_processor.aiops_change
    FOR EACH ROW EXECUTE FUNCTION event_processor.reject_rule_history_mutation();
COMMIT;
