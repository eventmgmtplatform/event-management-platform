\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_processor.admin_request (
    tenant TEXT NOT NULL,
    actor TEXT NOT NULL,
    request_id TEXT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant,actor,request_id)
);
CREATE TABLE IF NOT EXISTS event_processor.admin_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant TEXT NOT NULL,
    actor TEXT NOT NULL,
    request_id TEXT NOT NULL,
    action TEXT NOT NULL,
    resource TEXT NOT NULL,
    previous_revision BIGINT,
    new_revision BIGINT,
    outcome TEXT NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS processor_admin_audit_lookup ON event_processor.admin_audit(tenant,request_id);
CREATE OR REPLACE TRIGGER immutable_admin_audit BEFORE UPDATE OR DELETE ON event_processor.admin_audit
    FOR EACH ROW EXECUTE FUNCTION event_processor.reject_rule_history_mutation();
CREATE OR REPLACE TRIGGER immutable_admin_receipt BEFORE UPDATE OR DELETE ON event_processor.admin_request
    FOR EACH ROW WHEN (OLD.response IS NOT NULL) EXECUTE FUNCTION event_processor.reject_rule_history_mutation();
COMMIT;
