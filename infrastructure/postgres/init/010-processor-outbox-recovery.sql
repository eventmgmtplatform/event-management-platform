\set ON_ERROR_STOP on
BEGIN;
-- Migration 010 is a database sequence number, not the excluded design artifact DA-10.
ALTER TABLE event_processor.output_outbox
    ADD COLUMN IF NOT EXISTS dispatch_sequence BIGINT GENERATED ALWAYS AS IDENTITY,
    ADD COLUMN IF NOT EXISTS attempts BIGINT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS last_error_code TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS processor_dispatch_sequence
    ON event_processor.output_outbox(dispatch_sequence);
CREATE INDEX IF NOT EXISTS processor_pending_key_order
    ON event_processor.output_outbox(topic, message_key, dispatch_sequence)
    WHERE published_at IS NULL;
CREATE INDEX IF NOT EXISTS processor_output_due
    ON event_processor.output_outbox(next_attempt_at, dispatch_sequence)
    WHERE published_at IS NULL;
COMMIT;
