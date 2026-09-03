\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS
    event_management.integration_command_execution (
        command_id          VARCHAR(128) PRIMARY KEY,
        payload_hash        CHAR(64) NOT NULL,
        event_id            VARCHAR(100) NOT NULL,
        event_key           VARCHAR(256) NOT NULL,
        tenant              VARCHAR(100) NOT NULL,
        integration_type    VARCHAR(50) NOT NULL,
        operation           VARCHAR(50) NOT NULL,
        execution_status    VARCHAR(20) NOT NULL,
        command_payload     JSONB NOT NULL,
        result_payload      JSONB,
        claimed_at          TIMESTAMPTZ NOT NULL
                            DEFAULT CURRENT_TIMESTAMP,
        completed_at        TIMESTAMPTZ,
        updated_at          TIMESTAMPTZ NOT NULL
                            DEFAULT CURRENT_TIMESTAMP,

        CONSTRAINT integration_command_execution_status
            CHECK (
                execution_status IN (
                    'IN_PROGRESS',
                    'COMPLETED'
                )
            ),

        CONSTRAINT integration_command_execution_type_uppercase
            CHECK (
                integration_type = upper(integration_type)
            ),

        CONSTRAINT integration_command_execution_operation_uppercase
            CHECK (
                operation = upper(operation)
            ),

        CONSTRAINT integration_command_execution_terminal_result
            CHECK (
                (
                    execution_status = 'IN_PROGRESS'
                    AND result_payload IS NULL
                    AND completed_at IS NULL
                )
                OR
                (
                    execution_status = 'COMPLETED'
                    AND result_payload IS NOT NULL
                    AND completed_at IS NOT NULL
                )
            )
    );

CREATE INDEX IF NOT EXISTS
    idx_integration_command_execution_event_key
ON event_management.integration_command_execution (
    event_key
);

CREATE INDEX IF NOT EXISTS
    idx_integration_command_execution_status_updated
ON event_management.integration_command_execution (
    execution_status,
    updated_at
);
