\set ON_ERROR_STOP on

ALTER TABLE event_management.integration_configuration
  ADD COLUMN IF NOT EXISTS environment VARCHAR(100) NOT NULL DEFAULT 'local',
  ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_integration_configuration_tenant_env_name
  ON event_management.integration_configuration (tenant, environment, integration_name);

GRANT SELECT, INSERT, UPDATE ON event_management.integration_configuration TO console_catalog_login;
GRANT SELECT, INSERT ON event_management.console_catalog_audit TO console_catalog_login;
GRANT USAGE, SELECT ON SEQUENCE event_management.console_catalog_audit_id_seq TO console_catalog_login;
