\set ON_ERROR_STOP on

/*
 * Durable recovery lease for ServiceNow integration commands.
 *
 * A claim owner token prevents an obsolete worker from completing a
 * command after ownership has been transferred. An expired lease must
 * be reconciled with ServiceNow before another CREATE operation.
 */

ALTER TABLE
    event_management.integration_command_execution
ADD COLUMN IF NOT EXISTS
    claim_owner VARCHAR(64);

ALTER TABLE
    event_management.integration_command_execution
ADD COLUMN IF NOT EXISTS
    lease_expires_at TIMESTAMPTZ;

ALTER TABLE
    event_management.integration_command_execution
ADD COLUMN IF NOT EXISTS
    recovery_count INTEGER NOT NULL DEFAULT 0;

/*
 * Claims produced before lease support are marked with a deterministic
 * legacy owner and an already-expired lease. They must be reconciled;
 * they must never be blindly executed again.
 */
UPDATE
    event_management.integration_command_execution
SET
    claim_owner = COALESCE(
        claim_owner,
        'legacy-' || md5(command_id)
    ),
    lease_expires_at = COALESCE(
        lease_expires_at,
        updated_at
    )
WHERE execution_status = 'IN_PROGRESS'
  AND (
      claim_owner IS NULL
      OR lease_expires_at IS NULL
  );

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid =
              'event_management.integration_command_execution'::regclass
          AND conname =
              'integration_command_execution_recovery_count'
    ) THEN
        ALTER TABLE
            event_management.integration_command_execution
        ADD CONSTRAINT
            integration_command_execution_recovery_count
        CHECK (
            recovery_count >= 0
        );
    END IF;
END
$migration$;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid =
              'event_management.integration_command_execution'::regclass
          AND conname =
              'integration_command_execution_active_lease'
    ) THEN
        ALTER TABLE
            event_management.integration_command_execution
        ADD CONSTRAINT
            integration_command_execution_active_lease
        CHECK (
            execution_status <> 'IN_PROGRESS'
            OR (
                claim_owner IS NOT NULL
                AND length(trim(claim_owner)) > 0
                AND lease_expires_at IS NOT NULL
            )
        );
    END IF;
END
$migration$;

CREATE INDEX IF NOT EXISTS
    idx_integration_command_execution_expired_lease
ON event_management.integration_command_execution (
    lease_expires_at
)
WHERE execution_status = 'IN_PROGRESS';
