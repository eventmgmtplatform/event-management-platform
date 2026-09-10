\set ON_ERROR_STOP on

ALTER TABLE event_management.event_state
    ADD COLUMN IF NOT EXISTS source_severity INTEGER,
    ADD COLUMN IF NOT EXISTS effective_severity INTEGER,
    ADD COLUMN IF NOT EXISTS tally BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_state_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS state_payload JSONB;

CREATE TABLE IF NOT EXISTS event_management.ess_state_request (
    message_id VARCHAR(128) PRIMARY KEY,
    event_key VARCHAR(128) NOT NULL,
    tenant VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    disposition VARCHAR(20) NOT NULL CHECK (disposition IN ('APPLIED', 'STALE')),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ess_state_request_event
    ON event_management.ess_state_request(event_key);

CREATE TABLE IF NOT EXISTS event_management.ess_event_transition (
    message_id VARCHAR(128) PRIMARY KEY REFERENCES event_management.ess_state_request(message_id),
    event_key VARCHAR(128) NOT NULL REFERENCES event_management.event_state(event_key),
    tenant VARCHAR(100) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    transition_type VARCHAR(20) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(event_key, aggregate_version)
);
