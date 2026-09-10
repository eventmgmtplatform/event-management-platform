\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_processor.correlation_group (
    tenant TEXT NOT NULL,
    correlation_key CHAR(64) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    document JSONB NOT NULL CHECK (document->>'key'=correlation_key AND (document->>'revision')::bigint=revision),
    PRIMARY KEY (tenant,correlation_key)
);
COMMIT;
