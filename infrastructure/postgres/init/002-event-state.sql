\set ON_ERROR_STOP on

CREATE SCHEMA IF NOT EXISTS event_management;

CREATE TABLE IF NOT EXISTS event_management.event_state (
    event_key               VARCHAR(128) PRIMARY KEY,
    event_id                VARCHAR(100) NOT NULL,
    tenant                  VARCHAR(100) NOT NULL,

    lifecycle_status        VARCHAR(50) NOT NULL DEFAULT 'OPEN',

    ticket_number           VARCHAR(100),
    notification_id         VARCHAR(100),
    automation_id           VARCHAR(100),

    servicenow_status       VARCHAR(50) NOT NULL DEFAULT 'NOT_REQUIRED',
    gnm_status              VARCHAR(50) NOT NULL DEFAULT 'NOT_REQUIRED',
    cacf_status             VARCHAR(50) NOT NULL DEFAULT 'NOT_REQUIRED',

    integration_state       JSONB NOT NULL DEFAULT '{}'::JSONB,
    last_result             JSONB,

    first_seen_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version                 BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_event_state_tenant
    ON event_management.event_state (tenant);

CREATE INDEX IF NOT EXISTS idx_event_state_ticket
    ON event_management.event_state (ticket_number);

CREATE INDEX IF NOT EXISTS idx_event_state_updated
    ON event_management.event_state (last_updated_at DESC);
