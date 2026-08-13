\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS
    event_management.processed_integration_result (
        result_id          VARCHAR(128) PRIMARY KEY,
        event_key          VARCHAR(128) NOT NULL,
        event_id           VARCHAR(100) NOT NULL,
        tenant             VARCHAR(100) NOT NULL,
        integration_type   VARCHAR(50) NOT NULL,
        result_payload     JSONB NOT NULL,
        processed_at       TIMESTAMPTZ NOT NULL
                           DEFAULT CURRENT_TIMESTAMP,

        CONSTRAINT processed_integration_result_type_uppercase
            CHECK (
                integration_type = upper(integration_type)
            )
    );

CREATE INDEX IF NOT EXISTS
    idx_processed_integration_result_event_key
ON event_management.processed_integration_result (
    event_key
);

INSERT INTO event_management.processed_integration_result (
    result_id,
    event_key,
    event_id,
    tenant,
    integration_type,
    result_payload,
    processed_at
)
SELECT
    last_result ->> 'resultId',
    event_key,
    event_id,
    tenant,
    upper(last_result ->> 'integrationType'),
    last_result,
    last_updated_at
FROM event_management.event_state
WHERE last_result IS NOT NULL
  AND last_result ? 'resultId'
  AND last_result ? 'integrationType'
ON CONFLICT (result_id) DO NOTHING;
