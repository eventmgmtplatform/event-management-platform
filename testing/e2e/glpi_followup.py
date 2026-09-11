"""GLPI APPLY_AUTOMATION_RESULT vertical slice through integration.commands."""
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
    s=uuid.uuid4().hex[:12]; tenant="glpi-followup-"+s; event="glpi-followup-event-"+s; key="glpi-followup-key-"+s; cmd="glpi-followup-command-"+s
    api="http://127.0.0.1:8185/apirest.php"; base={"Content-Type":"application/json","App-Token":"local-app"}
    sess=http("GET",api+"/initSession",headers={**base,"Authorization":"user_token local-user"})[1]["session_token"]
    try:
        created=http("POST",api+"/Ticket",{"input":{"name":"GLPI FOLLOWUP E2E "+s,"content":"Ticket for automation evidence"}},headers={**base,"Session-Token":sess}); require(created[0]==201,"seed create failed"); tid=created[1]["id"]
    finally: http("GET",api+"/killSession",headers={**base,"Session-Token":sess})
    content="Automation completed successfully for execution "+s
    c={"schemaVersion":"1.0","commandId":cmd,"eventId":event,"eventKey":key,"tenant":tenant,"processingId":"proc-"+s,"createdAt":"2026-09-10T00:00:00Z","integrationType":"GLPI","operation":"APPLY_AUTOMATION_RESULT","configuration":"default","payload":{"ticketId":tid,"content":content},"metadata":{"idempotencyKey":cmd}}
    publish(c)
    ex=json.loads(wait(lambda:sql("SELECT row_to_json(e)::text FROM event_management.integration_command_execution e WHERE command_id='"+cmd+"' AND execution_status='COMPLETED'"),"GLPI followup completion",120)); result=ex["result_payload"]
    require(result["integrationType"]=="GLPI" and result["operation"]=="APPLY_AUTOMATION_RESULT","unexpected result")
    sess=http("GET",api+"/initSession",headers={**base,"Authorization":"user_token local-user"})[1]["session_token"]
    try: notes=http("GET",api+"/ITILFollowup",headers={**base,"Session-Token":sess})[1]
    finally: http("GET",api+"/killSession",headers={**base,"Session-Token":sess})
    require(any(n.get("items_id")==tid and n.get("is_private")==1 and n.get("content")==content for n in notes),"private ITILFollowup not found")
    state=json.loads(wait(lambda:sql("SELECT row_to_json(s)::text FROM event_management.glpi_integration_state s WHERE tenant='"+tenant+"' AND event_key='"+key+"'"),"GLPI followup projection",120)); require(state["status"]=="SUCCESS","followup projection mismatch")
    report.update(ticketId=tid,checks=["APPLY_AUTOMATION_RESULT consumed","private ITILFollowup created","GLPI ticket identity confirmed","SUCCESS projection persisted"]); (output/"glpi-followup-result.json").write_text(json.dumps({"command":c,"execution":ex,"notes":notes,"state":state},indent=2)+"\n")
if __name__=="__main__":
    import argparse; from datetime import datetime,timezone
    p=argparse.ArgumentParser(); p.add_argument("--output",type=Path,default=ROOT/"evidences/testing/glpi-followup"); a=p.parse_args(); a.output.mkdir(parents=True,exist_ok=True); r={"suite":"glpi-followup-e2e","status":"RUNNING","startedAt":datetime.now(timezone.utc).isoformat()}
    try: run(a.output,r); r["status"]="PASS"
    except Exception as e: r.update(status="FAIL",errorType=type(e).__name__,error=str(e)); raise
    finally: r["finishedAt"]=datetime.now(timezone.utc).isoformat(); (a.output/"report.json").write_text(json.dumps(r,indent=2)+"\n")
