\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS
    event_management.integration_worker_control (
        singleton_id BOOLEAN PRIMARY KEY DEFAULT TRUE,
        operating_mode VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        recovery_generation BIGINT NOT NULL DEFAULT 0,
        recovery_started_at TIMESTAMPTZ,
        recovery_completed_at TIMESTAMPTZ,
        last_error TEXT,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT integration_worker_control_singleton
            CHECK (singleton_id),
        CONSTRAINT integration_worker_control_mode
            CHECK (
                operating_mode IN (
                    'STANDBY',
                    'ACTIVE',
                    'PULL_RESTART'
                )
            ),
        CONSTRAINT integration_worker_control_generation
            CHECK (recovery_generation >= 0)
    );

INSERT INTO event_management.integration_worker_control (
    singleton_id,
    operating_mode
)
VALUES (TRUE, 'ACTIVE')
ON CONFLICT (singleton_id) DO NOTHING;
