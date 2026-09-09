\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_processor.rule_definition (
    tenant TEXT NOT NULL CHECK (length(trim(tenant)) BETWEEN 1 AND 128),
    rule_id TEXT NOT NULL,
    latest_version INTEGER NOT NULL DEFAULT 0 CHECK (latest_version >= 0),
    active_version INTEGER,
    status TEXT NOT NULL DEFAULT 'DISABLED' CHECK (status IN ('ENABLED','DISABLED','RETIRED')),
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant, rule_id),
    CHECK ((status = 'ENABLED') = (active_version IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS event_processor.rule_version (
    tenant TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    checksum CHAR(64) NOT NULL,
    definition JSONB NOT NULL CHECK (definition->>'id'=rule_id AND (definition->>'version')::numeric=version),
    actor TEXT NOT NULL CHECK (length(trim(actor)) > 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant, rule_id, version),
    FOREIGN KEY (tenant,rule_id) REFERENCES event_processor.rule_definition(tenant,rule_id)
);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='processor_active_rule_version'
                   AND conrelid='event_processor.rule_definition'::regclass) THEN
        ALTER TABLE event_processor.rule_definition ADD CONSTRAINT processor_active_rule_version
            FOREIGN KEY (tenant,rule_id,active_version) REFERENCES event_processor.rule_version(tenant,rule_id,version);
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS event_processor.rule_change (
    tenant TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    revision BIGINT NOT NULL,
    version INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('CREATED','ENABLED','DISABLED','RETIRED')),
    actor TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant,rule_id,revision),
    FOREIGN KEY (tenant,rule_id,version) REFERENCES event_processor.rule_version(tenant,rule_id,version)
);
CREATE OR REPLACE FUNCTION event_processor.reject_rule_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'IMMUTABLE_RULE_HISTORY';
END;
$$;
CREATE OR REPLACE TRIGGER immutable_rule_version BEFORE UPDATE OR DELETE ON event_processor.rule_version
    FOR EACH ROW EXECUTE FUNCTION event_processor.reject_rule_history_mutation();
CREATE OR REPLACE TRIGGER immutable_rule_change BEFORE UPDATE OR DELETE ON event_processor.rule_change
    FOR EACH ROW EXECUTE FUNCTION event_processor.reject_rule_history_mutation();
COMMIT;
