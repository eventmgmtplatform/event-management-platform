\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_management.gateway_rule (
    id varchar(64) PRIMARY KEY,
    revision bigint NOT NULL CHECK (revision > 0),
    definition jsonb NOT NULL CHECK (jsonb_typeof(definition) = 'object' AND definition->>'id' = id)
);
CREATE TABLE IF NOT EXISTS event_management.gateway_rule_history (
    id varchar(64) NOT NULL REFERENCES event_management.gateway_rule(id),
    revision bigint NOT NULL CHECK (revision > 0),
    definition jsonb NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(id, revision)
);
GRANT SELECT,INSERT,UPDATE ON event_management.gateway_rule TO oem_gateway_collector;
GRANT SELECT,INSERT ON event_management.gateway_rule_history TO oem_gateway_collector;
COMMIT;
