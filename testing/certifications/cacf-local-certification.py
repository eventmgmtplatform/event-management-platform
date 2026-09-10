#!/usr/bin/env python3
"""Exercise CACF only in the isolated cacf-certification Compose project."""
import json
import pathlib
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = "http://127.0.0.1:18083"
TOKEN = "cacf-local-test"  # Fixed fixture credential, never a provider credential.
NS = "http://b2b.ibm.com/schema/IS_B2B_CDM/R2_2"
COMPOSE = ["docker", "compose", "-p", "cacf-certification", "-f", str(ROOT / "testing/environments/cacf.compose.yml")]


def http(method, path, body=None, content_type="application/json", key=None, base=BASE):
    if isinstance(body, dict):
        body = json.dumps(body).encode()
    elif isinstance(body, str):
        body = body.encode()
    headers = {"X-CACF-Token": TOKEN, "Content-Type": content_type}
    if key:
        headers["Idempotency-Key"] = key
    request = urllib.request.Request(base + path, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def wait(predicate, description, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if predicate():
                return
        except (OSError, ValueError):
            pass
        time.sleep(0.5)
    raise AssertionError("Timed out: " + description)


def request(timeout=600, ticket=True):
    data = json.loads((ROOT / "testing/services/integration-worker/resources/cacf/request.json").read_text())
    data["executionId"] = str(uuid.uuid4())
    data["eventId"] = str(uuid.uuid4())
    data["event"]["sourceSerial"] = data["executionId"]
    data["automation"]["resultTimeoutSeconds"] = timeout
    if not ticket:
        data["ticket"].pop("number")
    return data


def state(data):
    code, body = http("GET", "/api/v1/automations/" + data["executionId"])
    return json.loads(body) if code == 200 else {}


def callback(data, transaction, status="", legacy=True):
    xml = (f"<ServiceIncident xmlns='{NS}'><RequesterID>LOCAL:{data['executionId']}:local</RequesterID>"
           f"<ProviderID>{data['executionId']}</ProviderID><Transaction><TransactionName>{transaction}</TransactionName>"
           f"</Transaction><WorkflowStatus>{status}</WorkflowStatus></ServiceIncident>")
    code, body = http("POST", "/data" if legacy else "/api/v1/providers/next/callback", xml, "text/xml")
    assert (code, body) == (200, "request is applied"), (code, body)


def provider_calls(data, path):
    _, body = http("GET", "/__admin/requests", base="http://127.0.0.1:18183")
    return [r for r in json.loads(body)["requests"] if r["request"]["url"] == path and data["executionId"] in r["request"].get("body", "")]


def ticket_calls(data, group=None):
    _, body = http("GET", "/__admin/requests", base="http://127.0.0.1:18184")
    calls = [r for r in json.loads(body)["requests"] if r["request"]["method"] == "PATCH" and data["executionId"] in r["request"].get("body", "")]
    return [r for r in calls if group is None or json.loads(r["request"]["body"]).get("assignment_group") == group]


def submit(data):
    code, body = http("POST", "/api/v1/automations", data, key=data["executionId"])
    assert code == 202, (code, body)
    wait(lambda: state(data).get("state") == "SUBMITTED", "NEXT CREATE submission")


def main():
    wait(lambda: http("GET", "/health/ready")[0] == 200, "worker readiness")
    golden = request()
    submit(golden)
    assert http("POST", "/api/v1/automations", golden, key=golden["executionId"])[0] == 202
    callback(golden, "Acknowledge_Create")
    deadline = state(golden)["deadlineAt"]
    callback(golden, "Acknowledge_Create", legacy=False)
    assert state(golden)["deadlineAt"] == deadline
    wait(lambda: len(provider_calls(golden, "/tupix/api/v1/netcool/incidents")) == 1, "TKTUPDATE")
    subprocess.run(COMPOSE + ["restart", "integration-worker"], check=True, timeout=90)
    wait(lambda: http("GET", "/health/ready")[0] == 200, "readiness after restart")
    assert state(golden)["deadlineAt"] == deadline
    callback(golden, "result", "ESCALATE")
    callback(golden, "result", "ESCALATE")
    assert state(golden)["outcome"] == "ESCALATED"
    wait(lambda: len(ticket_calls(golden, "LOCAL-HUMAN")) == 1, "ServiceNow foundation reassignment")
    assert len(provider_calls(golden, "/tupix/api/v1/netcool/tickets")) == 1
    assert len(provider_calls(golden, "/tupix/api/v1/netcool/incidents")) == 1
    print("PASS golden: CREATE, ACK, TKTUPDATE, restart, duplicate terminal, ServiceNow reassignment")

    timed = request(timeout=2)
    submit(timed)
    callback(timed, "Acknowledge_Create")
    wait(lambda: state(timed).get("outcome") == "TIMEOUT", "persistent result timeout")
    callback(timed, "result", "RESOLVE")
    assert state(timed)["outcome"] == "TIMEOUT"
    print("PASS timeout: late RESOLVE cannot reverse terminal result")

    unknown = request()
    submit(unknown)
    callback(unknown, "new-provider-response", "unrecognized")
    assert state(unknown)["outcome"] == "UNKNOWN" and state(unknown)["requiresReview"]
    assert not ticket_calls(unknown, "LOCAL-HUMAN")
    print("PASS unknown: review without automatic reassignment or resolution")

    deferred = request(ticket=False)
    command = {"commandId": deferred["executionId"], "eventId": deferred["eventId"], "eventKey": deferred["eventId"],
               "tenant": "local", "integrationType": "CACF", "operation": "AUTOMATION_REQUESTED", "payload": deferred}
    subprocess.run(COMPOSE + ["exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh", "--bootstrap-server", "kafka:9092", "--topic", "integration.commands"],
                   input=json.dumps(command) + "\n", text=True, check=True, timeout=30)
    wait(lambda: state(deferred).get("state") == "SUBMITTED", "Kafka admission")
    callback(deferred, "Acknowledge_Create")
    assert http("PUT", "/api/v1/automations/" + deferred["executionId"] + "/ticket", {"number": "INCLOCAL001"})[0] == 204
    wait(lambda: len(provider_calls(deferred, "/tupix/api/v1/netcool/incidents")) == 1, "ticket association after ProviderID")
    callback(deferred, "result", "RESOLVE")
    print("PASS Kafka intake and ticket association after provider acknowledgement")
    assert http("POST", "/data", "<!DOCTYPE r><r/>", "text/xml")[0] == 400
    print("PASS unsafe XML rejected")


if __name__ == "__main__":
    main()
