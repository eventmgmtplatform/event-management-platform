\set ON_ERROR_STOP on

BEGIN;
CREATE TABLE IF NOT EXISTS event_management.automation_execution (
    execution_id UUID PRIMARY KEY,
    command_id VARCHAR(128) UNIQUE NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    customer_code VARCHAR(64) NOT NULL,
    request JSONB NOT NULL,
    requester_id VARCHAR(512) UNIQUE NOT NULL,
    provider_execution_id VARCHAR(255) UNIQUE,
    transaction_number VARCHAR(255) UNIQUE NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'RECEIVED'
        CHECK (state IN ('RECEIVED','SUBMITTING','SUBMITTED','IN_PROGRESS','COMPLETED','TIMED_OUT','SUBMISSION_FAILED')),
    outcome VARCHAR(64),
    itsm_ticket_number VARCHAR(100),
    result_timeout_seconds INTEGER NOT NULL CHECK (result_timeout_seconds > 0),
    provider_ewt TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    deadline_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_automation_deadline
    ON event_management.automation_execution(deadline_at) WHERE completed_at IS NULL;

CREATE TABLE IF NOT EXISTS event_management.automation_provider_message (
    message_id UUID PRIMARY KEY,
    execution_id UUID REFERENCES event_management.automation_execution,
    direction VARCHAR(16) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    transaction_number VARCHAR(255),
    http_status INTEGER,
    payload TEXT NOT NULL,
    raw_payload BYTEA NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    late_result BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_automation_message_hash
    ON event_management.automation_provider_message(execution_id, payload_sha256);

CREATE TABLE IF NOT EXISTS event_management.automation_result (
    result_id UUID PRIMARY KEY,
    execution_id UUID UNIQUE NOT NULL REFERENCES event_management.automation_execution,
    outcome VARCHAR(64) NOT NULL,
    requires_review BOOLEAN NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS event_management.automation_outbox (
    outbox_id UUID PRIMARY KEY,
    sequence_id BIGSERIAL UNIQUE NOT NULL,
    execution_id UUID NOT NULL REFERENCES event_management.automation_execution,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    UNIQUE(execution_id, event_type)
);
CREATE INDEX IF NOT EXISTS ix_automation_outbox_pending
    ON event_management.automation_outbox(created_at) WHERE NOT published;

-- Mutating provider calls are claimed durably and never blindly retried.
CREATE TABLE IF NOT EXISTS event_management.automation_provider_dispatch (
    execution_id UUID NOT NULL REFERENCES event_management.automation_execution,
    operation VARCHAR(32) NOT NULL CHECK (operation IN ('CREATE','TKTUPDATE')),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','IN_FLIGHT','SENT','REVIEW')),
    claimed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY(execution_id, operation)
);
COMMIT;
