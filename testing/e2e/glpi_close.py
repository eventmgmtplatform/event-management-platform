"""GLPI CLOSE_TICKET vertical slice through integration.commands."""
import json, subprocess, uuid
from pathlib import Path
from blackout import http, require, wait
ROOT=Path(__file__).resolve().parents[2]
def sql(q):
    require(q.lstrip().upper().startswith("SELECT"),"read-only SQL")
    return subprocess.run(["docker","exec","-i","event-postgres","sh","-c",'psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],input=q,capture_output=True,text=True,check=True,timeout=15).stdout.strip()
def publish(c):
    subprocess.run(["docker","exec","-i","event-kafka","/opt/kafka/bin/kafka-console-producer.sh","--bootstrap-server","kafka:9092","--topic","integration.commands"],input=json.dumps(c,separators=(",",":"))+"\n",text=True,capture_output=True,check=True,timeout=15)
def run(output,report):
    s=uuid.uuid4().hex[:12]; tenant="glpi-close-"+s; event="glpi-close-event-"+s; key="glpi-close-key-"+s; cmd="glpi-close-command-"+s
    api="http://127.0.0.1:8185/apirest.php"; base={"Content-Type":"application/json","App-Token":"local-app"}
    sess=http("GET",api+"/initSession",headers={**base,"Authorization":"user_token local-user"})[1]["session_token"]
    try:
        created=http("POST",api+"/Ticket",{"input":{"name":"GLPI CLOSE E2E "+s,"content":"Ticket listo para cierre"}},headers={**base,"Session-Token":sess}); require(created[0]==201,"seed create failed"); tid=created[1]["id"]
        sol=http("POST",api+"/ITILSolution",{"input":{"itemtype":"Ticket","items_id":tid,"content":"Solución ITIL previa al cierre"}},headers={**base,"Session-Token":sess}); require(sol[0]==201,"seed resolve failed")
    finally: http("GET",api+"/killSession",headers={**base,"Session-Token":sess})
    c={"schemaVersion":"1.0","commandId":cmd,"eventId":event,"eventKey":key,"tenant":tenant,"processingId":"proc-"+s,"createdAt":"2026-09-10T00:00:00Z","integrationType":"GLPI","operation":"CLOSE_TICKET","configuration":"default","payload":{"ticketId":tid},"metadata":{"idempotencyKey":cmd}}
    publish(c)
    ex=json.loads(wait(lambda:sql("SELECT row_to_json(e)::text FROM event_management.integration_command_execution e WHERE command_id='"+cmd+"' AND execution_status='COMPLETED'"),"GLPI close completion",120)); result=ex["result_payload"]
    require(result["ticketLifecycleState"]=="CLOSED_CONFIRMED","close not confirmed")
    sess=http("GET",api+"/initSession",headers={**base,"Authorization":"user_token local-user"})[1]["session_token"]
    try: ticket=http("GET",api+"/Ticket/"+str(tid),headers={**base,"Session-Token":sess})[1]
    finally: http("GET",api+"/killSession",headers={**base,"Session-Token":sess})
    require(ticket["status"]==6,"GLPI status did not reach 6")
    state=json.loads(wait(lambda:sql("SELECT row_to_json(s)::text FROM event_management.glpi_integration_state s WHERE tenant='"+tenant+"' AND event_key='"+key+"'"),"GLPI close projection",120)); require(state["status"]=="CLOSED","close projection mismatch")
    report.update(ticketId=tid,checks=["CLOSE_TICKET consumed","PUT /Ticket status 6","GLPI status confirmed as 6","CLOSED projection persisted"]); (output/"glpi-close-result.json").write_text(json.dumps({"command":c,"execution":ex,"ticket":ticket,"state":state},indent=2)+"\n")
if __name__=="__main__":
    import argparse; from datetime import datetime,timezone
    p=argparse.ArgumentParser(); p.add_argument("--output",type=Path,default=ROOT/"evidences/testing/glpi-close"); a=p.parse_args(); a.output.mkdir(parents=True,exist_ok=True); r={"suite":"glpi-close-e2e","status":"RUNNING","startedAt":datetime.now(timezone.utc).isoformat()}
    try: run(a.output,r); r["status"]="PASS"
    except Exception as e: r.update(status="FAIL",errorType=type(e).__name__,error=str(e)); raise
    finally: r["finishedAt"]=datetime.now(timezone.utc).isoformat(); (a.output/"report.json").write_text(json.dumps(r,indent=2)+"\n")
