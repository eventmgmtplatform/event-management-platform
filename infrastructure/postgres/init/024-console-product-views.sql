\set ON_ERROR_STOP on
-- Applied after the local catalog bootstrap creates its restricted login.
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='console_catalog_login') THEN
GRANT USAGE ON SCHEMA event_processor TO console_catalog_login;
GRANT SELECT ON event_processor.rule_definition,event_processor.rule_version,event_processor.aiops_configuration,
 event_management.blackout,event_management.event_policy,event_management.inventory_resource TO console_catalog_login;
END IF; END $$;
