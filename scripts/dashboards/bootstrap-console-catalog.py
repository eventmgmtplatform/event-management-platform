#!/usr/bin/env python3
"""Provision the local console catalog. Never prints the generated database secret."""
import pathlib,secrets,subprocess
root=pathlib.Path(__file__).resolve().parents[2]
folder=root/'.local/console-catalog'; folder.mkdir(parents=True,exist_ok=True)
secret=folder/'db-password'
if not secret.exists():
    secret.write_text(secrets.token_hex(32)); secret.chmod(0o600)
password=secret.read_text().strip()
if not password or any(c not in '0123456789abcdef' for c in password): raise SystemExit('Invalid generated secret format')
def sql(text):
    subprocess.run(['docker','exec','-i','event-postgres','sh','-c','exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'],input=text,text=True,check=True)
sql((root/'infrastructure/postgres/init/023-console-customers.sql').read_text())
sql("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='console_catalog_login') THEN CREATE ROLE console_catalog_login LOGIN; END IF; END $$;\n"+
    "ALTER ROLE console_catalog_login WITH PASSWORD '"+password+"';\n"+
    "GRANT USAGE ON SCHEMA event_management TO console_catalog_login;\n"+
    "GRANT SELECT,INSERT,UPDATE,DELETE ON event_management.customer_configuration,event_management.delivery_filter,event_management.delivery_filter_target TO console_catalog_login;\n"+
    "GRANT INSERT ON event_management.console_catalog_audit TO console_catalog_login;\n"+
    "GRANT USAGE ON SEQUENCE event_management.console_catalog_audit_id_seq TO console_catalog_login;\n")
sql((root/'infrastructure/postgres/init/024-console-product-views.sql').read_text())
sql((root/'scripts/dashboards/seed-console-catalog.sql').read_text())
print('Console catalog provisioned; database password remains in a local ignored file.')
