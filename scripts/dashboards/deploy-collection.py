"""Add original ingress storage and deploy the two operational dashboards locally."""
import os,secrets,subprocess,time
from pathlib import Path
from deploy import ROOT,run,sql

def main():
    os.umask(0o077)
    private=ROOT/'.local/gateway-collection';private.mkdir(parents=True,exist_ok=True)
    for service in ('event-gateway','frontend-management-api','event-management-console'):
        image=run(['docker','inspect','--format','{{.Image}}',service])
        previous=private/(service+'-previous-image.txt')
        if not previous.exists():previous.write_text(image+'\n')
    sql((ROOT/'infrastructure/postgres/init/025-gateway-data-collection.sql').read_text())
    config=private/'runtime.env'
    if not config.exists():
        password=secrets.token_urlsafe(36)
        sql("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='gateway_collection') THEN CREATE ROLE gateway_collection LOGIN; END IF; END $$; ALTER ROLE gateway_collection PASSWORD '"+password+"'; GRANT oem_gateway_collector TO gateway_collection; ALTER ROLE gateway_collection SET statement_timeout='5s';")
        config.write_text('GATEWAY_POSTGRES_USER=gateway_collection\nGATEWAY_POSTGRES_PASSWORD='+password+'\n');config.chmod(0o600)
    print('Migration 025 and dedicated gateway credential ready.',flush=True)
    compose=['docker','compose','--env-file',str(ROOT/'.env'),'--env-file',str(ROOT/'.local/oem-dashboards/runtime.env'),'-f',str(ROOT/'infrastructure/docker-compose.yml'),'-f',str(ROOT/'infrastructure/docker-compose.oem-dashboards.yml')]
    subprocess.run(compose+['build','event-gateway','frontend-management-api','oem-dashboards-api','event-management-console'],cwd=ROOT,check=True)
    subprocess.run(compose+['up','-d','--no-build','--no-deps','event-gateway','frontend-management-api','oem-dashboards-api','event-management-console'],cwd=ROOT,check=True)
    print('Existing shared Nginx and API services updated.',flush=True)
if __name__=='__main__':main()
