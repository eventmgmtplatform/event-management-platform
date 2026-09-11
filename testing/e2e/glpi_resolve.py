"""GLPI RESOLVE_TICKET vertical slice through integration.commands."""

import json
import subprocess
import time
import uuid
from pathlib import Path

from blackout import http, require, wait

ROOT = Path(__file__).resolve().parents[2]


def sql(query):
    require(query.lstrip().upper().startswith("SELECT"), "GLPI E2E SQL must be read only")
    return subprocess.run(
        ["docker", "exec", "-i", "event-postgres", "sh", "-c",
         'psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=query, capture_output=True, text=True, check=True, timeout=15,
    ).stdout.strip()


def publish(command):
    payload = json.dumps(command, separators=(",", ":"))
    subprocess.run(
        ["docker", "exec", "-i", "event-kafka", "/opt/kafka/bin/kafka-console-producer.sh",
         "--bootstrap-server", "kafka:9092", "--topic", "integration.commands"],
        input=payload + "\n", text=True, capture_output=True, check=True, timeout=15,
    )


def run(output, report):
    suffix = uuid.uuid4().hex[:12]
    tenant = "glpi-resolve-" + suffix
    event_id = "glpi-resolve-event-" + suffix
    event_key = "glpi-resolve-key-" + suffix
    command_id = "glpi-resolve-command-" + suffix
    processing_id = "glpi-resolve-processing-" + suffix
    api = "http://127.0.0.1:8185/apirest.php"
    headers = {"Content-Type": "application/json", "App-Token": "local-app"}
    report.update(tenant=tenant, eventId=event_id, eventKey=event_key, commandId=command_id, checks=[])

    session = http("GET", api + "/initSession", headers={**headers, "Authorization": "user_token local-user"})[1]["session_token"]
    try:
        created = http("POST", api + "/Ticket", {
            "input": {"name": "GLPI RESOLVE E2E " + suffix, "content": "Ticket abierto para resolución", "entities_id": 0,
                       "itilcategories_id": 0, "urgency": 4, "impact": 4, "priority": 4}},
            headers={**headers, "Session-Token": session})
        require(created[0] == 201, "GLPI seed ticket creation failed")
        ticket_id = created[1]["id"]
    finally:
        http("GET", api + "/killSession", headers={**headers, "Session-Token": session})

    command = {
        "schemaVersion": "1.0", "commandId": command_id, "eventId": event_id, "eventKey": event_key,
        "tenant": tenant, "processingId": processing_id, "createdAt": "2026-09-10T00:00:00Z",
        "integrationType": "GLPI", "operation": "RESOLVE_TICKET", "configuration": "default",
        "payload": {"ticketId": ticket_id, "content": "Resolución confirmada por monitoreo"},
        "metadata": {"idempotencyKey": command_id},
    }
    publish(command)

    execution = json.loads(wait(
        lambda: sql("SELECT row_to_json(e)::text FROM event_management.integration_command_execution e "
                    "WHERE command_id='" + command_id + "' AND execution_status='COMPLETED'"),
        "GLPI resolve command completion", 120,
    ))
    result = execution["result_payload"]
    require(result["integrationType"] == "GLPI" and result["operation"] == "RESOLVE_TICKET", "Unexpected GLPI result")
    require(result["ticketLifecycleState"] == "RESOLVED_CONFIRMED", "GLPI resolution was not confirmed")
    require(result["externalSystemId"] == str(ticket_id), "GLPI resolve identity mismatch")

    # The worker already confirmed the authoritative GET. Repeat through a fresh session for the evidence.
    session = http("GET", api + "/initSession", headers={**headers, "Authorization": "user_token local-user"})[1]["session_token"]
    try:
        ticket = http("GET", api + "/Ticket/" + str(ticket_id), headers={**headers, "Session-Token": session})[1]
    finally:
        http("GET", api + "/killSession", headers={**headers, "Session-Token": session})
    require(ticket["status"] == 5, "GLPI ticket did not reach native status 5")

    state = json.loads(wait(
        lambda: sql("SELECT row_to_json(s)::text FROM event_management.glpi_integration_state s "
                    "WHERE tenant='" + tenant + "' AND event_key='" + event_key + "'"),
        "GLPI resolve state projection", 120,
    ))
    require(state["ticket_id"] == str(ticket_id) and state["status"] == "RESOLVED", "GLPI resolve projection mismatch")
    report["ticketId"] = ticket_id
    report["checks"] = ["RESOLVE_TICKET consumed from integration.commands", "ITILSolution created", "GLPI status confirmed as 5", "SUCCESS result and provider projection persisted"]
    (output / "glpi-resolve-result.json").write_text(json.dumps({"command": command, "execution": execution, "ticket": ticket, "state": state}, indent=2) + "\n")


if __name__ == "__main__":
    import argparse
    from datetime import datetime, timezone
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "evidences/testing/glpi-resolve")
    args = parser.parse_args(); args.output.mkdir(parents=True, exist_ok=True)
    report = {"suite": "glpi-resolve-e2e", "status": "RUNNING", "startedAt": datetime.now(timezone.utc).isoformat()}
    try:
        run(args.output, report); report["status"] = "PASS"
    except Exception as error:
        report.update(status="FAIL", errorType=type(error).__name__, error=str(error)); raise
    finally:
        report["finishedAt"] = datetime.now(timezone.utc).isoformat()
        (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
