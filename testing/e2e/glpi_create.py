"""GLPI backend vertical slice: public event -> command -> GLPI -> result/state."""

import json
import re
import subprocess
import time
import uuid
from pathlib import Path

from blackout import http, require, wait

ROOT = Path(__file__).resolve().parents[2]


def sql(query):
    """Read-only observation against the shared PostgreSQL container."""
    require(query.lstrip().upper().startswith("SELECT"), "GLPI E2E SQL must be read only")
    result = subprocess.run(
        ["docker", "exec", "-i", "event-postgres", "sh", "-c",
         'psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=query, capture_output=True, text=True, check=True, timeout=15,
    )
    return result.stdout.strip()


def run(output, report):
    run_id = uuid.uuid4().hex[:12]
    tenant = "glpi-e2e-" + run_id
    node = "glpi-e2e-node-" + run_id
    api = "http://127.0.0.1:8082/api/v1"
    gateway = "http://127.0.0.1:8081/api/v1/events"
    headers = {"X-Tenant-Id": tenant, "X-Actor-Id": "glpi-e2e"}
    correlation_id = tenant + "-correlation"
    route_id = tenant + "-route"
    report.update(tenant=tenant, node=node, checks=[])

    correlation = {
        "id": correlation_id, "version": 1, "enabled": True,
        "priority": 10, "strategy": "ATTRIBUTE",
        "scope": {"field": "resource.node", "operator": "EQ", "value": node},
        "candidateSelection": {"windowSeconds": 300, "maxCandidates": 8, "activeOnly": True},
        "match": {"fields": ["resource.node"]}, "relationship": {"type": "GROUP"},
        "metadata": {"owner": "glpi-e2e"},
    }
    route = {
        "id": route_id, "version": 1, "type": "ROUTING", "enabled": True, "priority": 10,
        "condition": {"field": "resource.node", "operator": "EQ", "value": node},
        "actions": [{"type": "CREATE_TICKET", "target": "GLPI",
                      "parameters": {"configuration": "default", "correlationRuleId": correlation_id}}],
        "metadata": {"owner": "glpi-e2e"},
    }

    def mutate(path, body, revision):
        return http("POST", api + path, body, {
            **headers, "Idempotency-Key": uuid.uuid4().hex, "If-Match": f'"{revision}"',
        })

    try:
        require(mutate("/rules", {"rule": correlation, "reason": "GLPI E2E setup"}, 0)[0] == 201,
                "Correlation rule creation failed")
        require(mutate("/rules/" + correlation_id + "/enable", {"version": 1, "reason": "GLPI E2E setup"}, 1)[0] == 200,
                "Correlation rule enable failed")
        require(mutate("/rules", {"rule": route, "reason": "GLPI E2E setup"}, 0)[0] == 201,
                "GLPI route creation failed")
        require(mutate("/rules/" + route_id + "/enable", {"version": 1, "reason": "GLPI E2E setup"}, 1)[0] == 200,
                "GLPI route enable failed")

        event = json.loads((ROOT / "testing/fixtures/events/sdc/zabbix-messagebus-problem.json").read_text())
        event.update({"CustomerCode": tenant, "Node": node, "hostname": node,
                      "msg": "GLPI E2E backend creation", "severity": "4"})
        status, accepted, _ = http("POST", gateway, event)
        require(status == 202 and accepted.get("accepted") is True, "Gateway rejected GLPI event")
        event_id = accepted["eventId"]
        require(re.fullmatch(r"[A-Za-z0-9_-]+", event_id), "Unexpected event identity")

        processing = json.loads(wait(
            lambda: sql("SELECT evidence::text FROM event_processor.processing_record "
                        "WHERE tenant='" + tenant + "' AND event_id='" + event_id + "'"),
            "GLPI processing record", 120,
        ))
        require(processing["directive"] == "GENERATE_COMMANDS", "GLPI route did not generate a command")
        processing_id = processing["processingId"]
        command = json.loads(wait(
            lambda: sql("SELECT envelope::text FROM event_processor.integration_command "
                        "WHERE tenant='" + tenant + "' AND envelope->>'integrationType'='GLPI'"),
            "GLPI durable command", 120,
        ))
        require(command["integrationType"] == "GLPI" and command["operation"] == "CREATE_TICKET",
                "Unexpected GLPI command")
        require(command["payload"]["severity"] == 4, "Severity was not preserved")

        tickets = wait(
            lambda: next((items for items in [http("GET", "http://127.0.0.1:8096/api/glpi/tickets")[1].get("tickets", [])]
                          if any("[OEM:" + command["commandId"] + "]" in ticket.get("name", "") for ticket in items)), None),
            "GLPI ticket creation", 120,
        )
        created = [ticket for ticket in tickets if "[OEM:" + command["commandId"] + "]" in ticket.get("name", "")]
        require(len(created) == 1, "GLPI did not create exactly one ticket")
        ticket = created[0]
        require(ticket["status"] == 1 and ticket["entities_id"] == 0, "Unexpected native GLPI ticket state")

        result = json.loads(wait(
            lambda: sql("SELECT result_payload::text FROM event_management.integration_command_execution "
                        "WHERE command_id='" + command["commandId"] + "' AND execution_status='COMPLETED'"),
            "GLPI integration result", 120,
        ))
        require(result["integrationType"] == "GLPI" and result["status"] == "SUCCESS",
                "GLPI result was not successful")
        require(result["externalSystemId"] == str(ticket["id"]), "Result ticket identity mismatch")
        state = json.loads(wait(
            lambda: sql("SELECT row_to_json(s)::text FROM event_management.glpi_integration_state s "
                        "WHERE tenant='" + tenant + "' AND event_key='" + command["eventKey"] + "'"),
            "GLPI state projection", 120,
        ))
        require(state["status"] == "SUCCESS" and state["ticket_id"] == str(ticket["id"]),
                "GLPI state projection mismatch")
        report["checks"].extend([
            "Public event accepted by Event Gateway",
            "GLPI CREATE_TICKET command persisted with canonical identity",
            "Exactly one native GLPI ticket created in status 1",
            "GLPI SUCCESS result persisted with the native ticket ID",
            "GLPI provider state projected separately in PostgreSQL",
        ])
        report["identities"] = {"eventId": event_id, "processingId": processing_id,
                                "commandId": command["commandId"], "ticketId": ticket["id"]}
        (output / "glpi-e2e-result.json").write_text(json.dumps({"processing": processing, "command": command,
                                                                    "ticket": ticket, "result": result, "state": state}, indent=2) + "\n")
    finally:
        for rule_id in (route_id, correlation_id):
            try:
                mutate("/rules/" + rule_id + "/disable", {"version": 2, "reason": "GLPI E2E cleanup"}, 2)
            except Exception:
                pass


if __name__ == "__main__":
    import argparse
    from datetime import datetime, timezone

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "evidences/testing/glpi-create")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"suite": "glpi-create-e2e", "status": "RUNNING",
              "startedAt": datetime.now(timezone.utc).isoformat()}
    try:
        run(args.output, report)
        report["status"] = "PASS"
    except Exception as error:
        report.update(status="FAIL", errorType=type(error).__name__, error=str(error))
        raise
    finally:
        report["finishedAt"] = datetime.now(timezone.utc).isoformat()
        (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
