\set ON_ERROR_STOP on
BEGIN;
CREATE OR REPLACE FUNCTION event_management.delivery_criteria_valid(criteria jsonb)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE item record;
BEGIN
    IF jsonb_typeof(criteria) <> 'object' OR octet_length(criteria::text) > 16000 THEN RETURN false; END IF;
    FOR item IN SELECT * FROM jsonb_each(criteria) LOOP
        IF NOT (item.key = ANY(ARRAY['IBMManaged','ResourceId','Service','SubAccount','Subsystem','Application',
            'InstanceId','SubComponent','Component','ComponentType','ResourceUsage','OSType','MsgId',
            'AlertKey','AlertGroup','ResourceType','EventType','MonitoringSolution','Location','SourceType','OutsideServiceHours']))
            OR jsonb_typeof(item.value) <> 'object' THEN RETURN false; END IF;
        IF NOT (item.value ?& ARRAY['operator','value']) OR item.value - ARRAY['operator','value'] <> '{}'::jsonb
            OR jsonb_typeof(item.value->'operator') IS DISTINCT FROM 'string'
            OR NOT (item.value->>'operator' = ANY(ARRAY['eq','eq_ci','regex_ci']))
            OR jsonb_typeof(item.value->'value') NOT IN ('string','number','boolean') THEN RETURN false; END IF;
        IF item.value->>'operator' IN ('eq_ci','regex_ci') AND jsonb_typeof(item.value->'value') <> 'string' THEN RETURN false; END IF;
        IF length(item.value->>'value') > 512 THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
END $$;
CREATE TABLE IF NOT EXISTS event_management.delivery_filter (
    filter_id text PRIMARY KEY CHECK (length(filter_id) BETWEEN 1 AND 128),
    name text NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    description text NOT NULL DEFAULT '',
    customer_code text NOT NULL CHECK (length(customer_code) BETWEEN 1 AND 100),
    applid text CHECK (applid IS NULL OR length(applid) BETWEEN 1 AND 128),
    filter_state smallint NOT NULL DEFAULT 0 CHECK (filter_state IN (0,1,2)),
    filter_weight integer NOT NULL DEFAULT 0,
    severities smallint[] CHECK (severities IS NULL OR (cardinality(severities) BETWEEN 1 AND 6 AND severities <@ ARRAY[0,1,2,3,4,5]::smallint[] AND array_position(severities,NULL) IS NULL)),
    criteria jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (event_management.delivery_criteria_valid(criteria)),
    origin text NOT NULL DEFAULT 'manual' CHECK (origin IN ('manual','legacy','demo')),
    legacy_filter_id text,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS event_management.delivery_filter_target (
    filter_id text NOT NULL REFERENCES event_management.delivery_filter(filter_id) ON DELETE CASCADE,
    target text NOT NULL CHECK (target IN ('gnm','snow','cacf','chatops','extensions')),
    behavior text NOT NULL DEFAULT 'enable' CHECK (behavior IN ('enable','force_off','overlay')),
    action_reference text,
    assignment_group text,
    delay_seconds integer CHECK (delay_seconds IS NULL OR delay_seconds >= 0),
    depends_on_ticketing boolean NOT NULL DEFAULT false,
    PRIMARY KEY(filter_id,target)
);
CREATE INDEX IF NOT EXISTS delivery_filter_customer_applid ON event_management.delivery_filter(customer_code,lower(applid));
CREATE INDEX IF NOT EXISTS delivery_target_name ON event_management.delivery_filter_target(target,filter_id);
CREATE OR REPLACE VIEW dashboard_read.delivery AS
SELECT f.filter_id, f.name, f.description, f.customer_code, f.applid,
       f.filter_state, f.filter_weight, f.severities, f.criteria, f.origin,
       f.legacy_filter_id, f.updated_at,
       COALESCE((SELECT jsonb_agg(jsonb_build_object(
           'target', t.target, 'behavior', t.behavior,
           'actionReference', t.action_reference, 'assignmentGroup', t.assignment_group,
           'delaySeconds', t.delay_seconds, 'dependsOnTicketing', t.depends_on_ticketing
       ) ORDER BY t.target) FROM event_management.delivery_filter_target t
       WHERE t.filter_id=f.filter_id), '[]'::jsonb) AS targets
FROM event_management.delivery_filter f;
GRANT SELECT ON dashboard_read.delivery TO oem_dashboard_reader;
COMMENT ON TABLE event_management.delivery_filter IS 'Delivery configuration catalog v1 inspired by legacy AutomationFilters; not a runtime routing engine';
COMMIT;
