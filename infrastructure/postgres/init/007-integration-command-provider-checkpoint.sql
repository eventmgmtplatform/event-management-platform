\set ON_ERROR_STOP on

/*
 * Provider-side mutation checkpoint.
 *
 * Persists the minimum provider identity obtained after an accepted
 * external mutation and before terminal command completion.
 *
 * This allows an expired/restarted command to reconcile provider state
 * without blindly repeating the mutation.
 */

ALTER TABLE
    event_management.integration_command_execution
ADD COLUMN IF NOT EXISTS
    provider_checkpoint JSONB;

ALTER TABLE
    event_management.integration_command_execution
ADD COLUMN IF NOT EXISTS
    provider_checkpoint_at TIMESTAMPTZ;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid =
              'event_management.integration_command_execution'::regclass
          AND conname =
              'integration_command_execution_provider_checkpoint'
    ) THEN
        ALTER TABLE
            event_management.integration_command_execution
        ADD CONSTRAINT
            integration_command_execution_provider_checkpoint
        CHECK (
            (
                provider_checkpoint IS NULL
                AND provider_checkpoint_at IS NULL
            )
            OR
            (
                provider_checkpoint IS NOT NULL
                AND provider_checkpoint_at IS NOT NULL
            )
        );
    END IF;
END
$migration$;
