\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_management.customer_configuration (
 customer_code text PRIMARY KEY CHECK(length(customer_code) BETWEEN 1 AND 100),
 customer text NOT NULL CHECK(length(customer) BETWEEN 1 AND 200),
 bamid text NOT NULL DEFAULT '', gnmorgid text NOT NULL DEFAULT '',
 snow_company_id text NOT NULL DEFAULT '', snow_assignment_group text NOT NULL DEFAULT '',
 gnm_assignment_group text NOT NULL DEFAULT '', cacf_assignment_group text NOT NULL DEFAULT '',
 chatops_team text NOT NULL DEFAULT '', aiops_extension text NOT NULL DEFAULT '',
 timezone text NOT NULL DEFAULT 'America/Mexico_City', enabled boolean NOT NULL DEFAULT true,
 origin text NOT NULL DEFAULT 'manual' CHECK(origin IN ('manual','demo','legacy')),
 updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO event_management.customer_configuration(customer_code,customer,origin)
 SELECT DISTINCT customer_code,customer_code,CASE WHEN customer_code LIKE 'DEMO%' THEN 'demo' ELSE 'manual' END
 FROM event_management.delivery_filter ON CONFLICT DO NOTHING;
-- Keep compatibility with external importers: validate the customer in the console API,
-- without adding an FK that would break existing legacy catalog imports.
CREATE TABLE IF NOT EXISTS event_management.console_catalog_audit (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 entity text NOT NULL, entity_id text NOT NULL, action text NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT now(), before_data jsonb, after_data jsonb
);
COMMIT;
