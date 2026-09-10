\set ON_ERROR_STOP on
BEGIN;
-- Existing history is not republished during upgrade. New completions explicitly clear this marker.
ALTER TABLE event_management.integration_command_execution
 ADD COLUMN IF NOT EXISTS result_published_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
 ADD COLUMN IF NOT EXISTS recovery_published_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS worker_result_pending ON event_management.integration_command_execution(completed_at)
 WHERE execution_status='COMPLETED' AND result_published_at IS NULL;
COMMIT;
