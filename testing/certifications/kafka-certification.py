#!/usr/bin/env python3
"""Bounded real Kafka checks on an explicitly prepared, independent candidate."""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("kafka_admin", ROOT / "scripts/kafka-admin.py")
admin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admin)


def certify(directory, restart, report):
    compose = admin.bundle_config(directory)
    manifest = json.loads((directory / "manifest.json").read_text())
    if not manifest["project"].startswith("em-kafka-"):
        raise ValueError("Only em-kafka-* candidate projects are supported")
    config = json.loads(admin.run(compose + ["config", "--format", "json"]))
    for service in ("kafka", "kafka-ui"):
        container = admin.run(compose + ["ps", "-q", service]).strip()
        if not container:
            raise ValueError(f"Candidate service is absent: {service}")
        details = json.loads(admin.run(["docker", "inspect", container]))[0]
        if details["State"].get("Health", {}).get("Status") != "healthy":
            raise ValueError(f"Candidate service is not healthy: {service}")
        expected = json.loads(admin.run(["docker", "image", "inspect", config["services"][service]["image"]]))[0]
        if details["Image"] != expected["Id"]:
            raise ValueError(f"Unexpected running image: {service}")
    report["checks"].append("broker-ui-health-and-pinned-images")
    admin.verify(compose, directory / "create-topics.sh")
    report["checks"].append("nine-topic-configuration")
    # Unique retained topic; no application consumer groups or data are touched.
    topic = "em.certification." + uuid.uuid4().hex
    report["topic"] = topic
    admin.kafka(compose, "kafka-topics", "--create", "--topic", topic,
                "--partitions", "1", "--replication-factor", "1",
                "--config", "retention.ms=86400000")
    payload = "certification:" + uuid.uuid4().hex
    cmd = compose + ["exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
                     "--bootstrap-server", "kafka:29092", "--topic", topic,
                     "--producer-property", "acks=all"]
    subprocess.run([str(x) for x in cmd], input=payload + "\n", text=True,
                   capture_output=True, check=True, timeout=60, env=admin.docker_environment())

    def read_record():
        content = admin.kafka(compose, "kafka-console-consumer", "--topic", topic,
                              "--partition", "0", "--offset", "0", "--max-messages", "1",
                              "--timeout-ms", "15000")
        if content.strip() != payload:
            raise ValueError("Stored record did not match the published record")

    read_record()
    report["checks"].append("produce-consume-explicit-partition-no-application-group")
    if restart:
        admin.run(compose + ["restart", "kafka"], capture=False, timeout=120)
        admin.run(compose + ["up", "-d", "--no-recreate", "--wait", "--wait-timeout", "240", "kafka"],
                  capture=False, timeout=300)
        read_record()
        report["checks"].append("record-survives-broker-restart")
        admin.run(compose + ["up", "-d", "--force-recreate", "--no-deps", "--wait",
                             "--wait-timeout", "240", "kafka"], capture=False, timeout=300)
        read_record()
        report["checks"].append("record-survives-container-recreation-with-volume")
        admin.verify(compose, directory / "create-topics.sh")
        report["checks"].append("topic-configuration-survives-recreation")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--restart", action="store_true", help="Restart AND recreate only the candidate broker, preserving its volume")
    args = parser.parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    output = ROOT / "evidences/kafka" / run_id
    output.mkdir(parents=True)
    report = {"status": "RUNNING", "scope": "isolated Kafka/UI infrastructure; not product release certification",
              "directory": str(args.directory.resolve()), "checks": [], "restartRequested": args.restart}
    try:
        certify(args.directory.resolve(), args.restart, report)
        report["status"] = "PASS"
    except Exception as error:
        report["status"] = "FAIL"
        report["error"] = str(error)
    finally:
        (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2))
        print(f"Report: {output / 'report.json'}")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
