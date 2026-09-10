\set ON_ERROR_STOP on

-- Additive migration. Invalid messages remain auditable/replayable in PostgreSQL.
CREATE TABLE IF NOT EXISTS event_management.ess_quarantine (
    topic TEXT NOT NULL,
    partition_id INTEGER NOT NULL,
    offset_id BIGINT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    quarantined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (topic, partition_id, offset_id)
);
CREATE INDEX IF NOT EXISTS idx_ess_quarantine_time
    ON event_management.ess_quarantine (quarantined_at DESC);
