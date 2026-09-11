"""Run explicitly: disposable Docker PostgreSQL, no platform database access."""
from pathlib import Path
import subprocess
import time
import uuid

ROOT=Path(__file__).resolve().parents[3]
def run():
    name="glpi-migration-test-"+uuid.uuid4().hex[:10]
    def docker(*args,**kwargs):
        result = subprocess.run(["docker",*args],capture_output=True,text=True,**kwargs)
        if result.returncode: raise RuntimeError(result.stderr)
        return result
    try:
        docker("run","--rm","-d","--name",name,"-e","POSTGRES_HOST_AUTH_METHOD=trust","postgres:17")
        for _ in range(40):
            if subprocess.run(["docker","exec",name,"pg_isready","-U","postgres"],capture_output=True).returncode==0:break
            time.sleep(.25)
        else:raise RuntimeError("Temporary PostgreSQL unavailable")
        def sql(value):return docker("exec","-i",name,"psql","-U","postgres","-v","ON_ERROR_STOP=1",input=value)
        sql("CREATE ROLE oem_dashboard_reader; CREATE SCHEMA event_management;")
        for file in ["002-event-state.sql","022-delivery-filter-catalog.sql","027-glpi-integration.sql","027-glpi-integration.sql"]:
            sql((ROOT/"infrastructure/postgres/init"/file).read_text())
        sql("""
        INSERT INTO event_management.glpi_integration_state(tenant,event_key,event_id,status,ticket_id,result)
        VALUES ('tenant-a','same-event','event-1','SUCCESS','42','{}'),('tenant-b','same-event','event-2','SUCCESS','43','{}');
        DO $$ BEGIN
          IF (SELECT count(*) FROM dashboard_read.glpi) <> 2 THEN RAISE EXCEPTION 'GLPI tenant isolation failed'; END IF;
          IF (SELECT count(*) FROM event_management.event_state) <> 0 THEN RAISE EXCEPTION 'ServiceNow state touched'; END IF;
          IF NOT has_table_privilege('oem_dashboard_reader','dashboard_read.glpi','SELECT') THEN RAISE EXCEPTION 'Dashboard grant missing'; END IF;
        END $$;
        """)
        print("PASS: GLPI migration is repeatable, tenant keyed, readable by dashboards, independent from ServiceNow.")
    finally:
        subprocess.run(["docker","rm","-f","-v",name],capture_output=True)
if __name__=="__main__":run()
