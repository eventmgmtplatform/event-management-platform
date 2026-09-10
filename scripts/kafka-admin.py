#!/usr/bin/env python3
"""Kafka administration and portable, isolated installation. Standard library only."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "infrastructure/kafka"
SERVICES = ("kafka", "kafka-init", "kafka-ui")
BUNDLE_FILES = ("compose.json", "create-topics.sh", "images.lock.json", "kafka-admin.py", "runtime.env")


def run(args, *, capture=True, timeout=60):
    result = subprocess.run([str(a) for a in args], text=True, capture_output=capture,
                            timeout=timeout, check=False, env=docker_environment())
    if result.returncode:
        # Compose configuration can contain credentials from unrelated services.
        raise RuntimeError(f"Command failed ({result.returncode}): {args[0]} {args[1] if len(args)>1 else ''}")
    return result.stdout if capture else ""


def docker_environment():
    # Shell overrides must not change a prepared bundle's identity or images.
    return {k: v for k, v in os.environ.items()
            if not k.startswith(("COMPOSE_", "KAFKA_"))}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(directory, project, broker_port, ui_port):
    if not re.fullmatch(r"em-kafka-[a-z0-9][a-z0-9-]{0,39}", project):
        raise ValueError("El proyecto debe empezar por em-kafka- y contener sólo minúsculas, números y guiones.")
    if broker_port == ui_port or any(p < 1024 or p > 65535 for p in (broker_port, ui_port)):
        raise ValueError("Usa dos puertos distintos entre 1024 y 65535.")
    if broker_port in (9092, 8085) or ui_port in (9092, 8085):
        raise ValueError("9092 y 8085 están reservados al runtime actual; usa puertos independientes.")
    lock = json.loads((ASSETS / "images.lock.json").read_text())
    for component in ("kafka", "kafka-ui"):
        if not re.fullmatch(r"[^\s]+@sha256:[a-f0-9]{64}", lock[component]["image"]):
            raise ValueError("Cada imagen debe estar fijada por digest.")
    # Fail instead of replacing an installation's cluster ID or settings.
    directory.mkdir(parents=True, exist_ok=False)
    for name in ("compose.json", "create-topics.sh", "images.lock.json"):
        shutil.copyfile(ASSETS / name, directory / name)
    shutil.copyfile(Path(__file__), directory / "kafka-admin.py")
    cluster_id = base64.urlsafe_b64encode(uuid.uuid4().bytes).decode().rstrip("=")
    values = {
        "COMPOSE_PROJECT_NAME": project,
        "KAFKA_IMAGE": lock["kafka"]["image"],
        "KAFKA_UI_IMAGE": lock["kafka-ui"]["image"],
        "KAFKA_CLUSTER_ID": cluster_id,
        "KAFKA_HOST_PORT": str(broker_port),
        "KAFKA_UI_PORT": str(ui_port),
        "KAFKA_UI_CLUSTER_NAME": project,
    }
    (directory / "runtime.env").write_text("".join(f"{k}={v}\n" for k, v in values.items()))
    (directory / "manifest.json").write_text(json.dumps({
        "status": "PREPARED_NOT_CERTIFIED", "project": project,
        "files": {name: digest(directory / name) for name in BUNDLE_FILES},
    }, indent=2) + "\n")
    print(f"Paquete preparado: {directory}\nWeb UI: http://localhost:{ui_port}\nNo se inició ningún contenedor.")


def bundle_config(directory):
    manifest = json.loads((directory / "manifest.json").read_text())
    for name in BUNDLE_FILES:
        if manifest["files"].get(name) != digest(directory / name):
            raise ValueError(f"El paquete cambió: {name}. Prepara un nuevo candidato; no sobrescribas el anterior.")
    values = dict(line.split("=", 1) for line in (directory / "runtime.env").read_text().splitlines() if line)
    if values["COMPOSE_PROJECT_NAME"] != manifest["project"]:
        raise ValueError("Identidad de proyecto incompatible.")
    return ["docker", "compose", "--project-name", manifest["project"],
            "--env-file", directory / "runtime.env", "-f", directory / "compose.json"]


def current_config():
    if os.environ.get("EVENTMANAGEMENT_RUNTIME", "ecosystem") not in ("local", "ecosystem"):
        raise ValueError("Esta administración Kafka apunta al runtime principal; usa --directory para un paquete aislado.")
    return ["docker", "compose", "--env-file", ROOT / ".env",
            "-f", ROOT / "infrastructure/docker-compose.yml"]


def kafka(compose, tool, *arguments):
    return run(compose + ["exec", "-T", "kafka", f"/opt/kafka/bin/{tool}.sh",
                         "--bootstrap-server", "kafka:29092", *arguments], timeout=120)


def inventory(script):
    rows = run(["bash", script, "--inventory"]).splitlines()
    if not rows or any(len(row.split("\t")) != 5 for row in rows):
        raise ValueError("Inventario de topics inválido.")
    return [dict(zip(("name", "partitions", "replication", "cleanup", "retention"), row.split("\t"))) for row in rows]


def compare_topics(expected, description):
    observed = {}
    for line in description.splitlines():
        name = re.search(r"Topic:\s+(\S+)", line)
        partitions = re.search(r"PartitionCount:\s+(\d+)", line)
        replicas = re.search(r"ReplicationFactor:\s+(\d+)", line)
        if name and partitions and replicas:
            observed[name[1]] = {"partitions": partitions[1], "replication": replicas[1],
                                 "config": line.split("Configs:", 1)[-1].strip()}
    failures = []
    for topic in expected:
        actual = observed.get(topic["name"])
        if actual is None:
            failures.append(f"{topic['name']}: ausente")
            continue
        for key in ("partitions", "replication"):
            if topic[key] != actual[key]:
                failures.append(f"{topic['name']}: {key} esperado={topic[key]} actual={actual[key]}")
        config = dict(re.findall(r"([\w.]+)=([^,\s]+)", actual["config"]))
        for key, setting in (("retention", "retention.ms"), ("cleanup", "cleanup.policy")):
            if config.get(setting) != topic[key]:
                failures.append(f"{topic['name']}: {setting} esperado={topic[key]} actual={config.get(setting, 'no explícito')}")
    return failures


def verify(compose, script):
    errors = compare_topics(inventory(script), kafka(compose, "kafka-topics", "--describe"))
    if errors:
        raise ValueError("Drift de topics (no se modificó nada):\n" + "\n".join(errors))
    print("PASS: topics, particiones, replicación, retención y cleanup coinciden. No certifica entrega ni recuperación.")


def install(compose, directory):
    # Only a validated portable bundle can reach this function. Never current_config().
    run(compose + ["config", "--quiet"])
    # Fail closed on a project name already owned by another bundle.
    project = json.loads((directory / "manifest.json").read_text())["project"]
    ids = run(["docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={project}"]).split()
    for container in ids:
        working_dir = run(["docker", "inspect", "--format",
                           '{{index .Config.Labels "com.docker.compose.project.working_dir"}}', container]).strip()
        if Path(working_dir).resolve() != directory:
            raise ValueError("El nombre de proyecto pertenece a otro directorio. Usa un proyecto nuevo.")
    # A volume can survive container removal; do not adopt another cluster's data.
    volume = f"{project}_kafka-data"
    existing = run(["docker", "volume", "ls", "--format", "{{.Name}}"]).splitlines()
    if volume in existing:
        values = dict(line.split("=", 1) for line in (directory / "runtime.env").read_text().splitlines() if line)
        cluster = run(["docker", "volume", "inspect", "--format",
                       '{{index .Labels "org.eventmanagement.kafka.cluster-id"}}', volume]).strip()
        if cluster != values["KAFKA_CLUSTER_ID"]:
            raise ValueError("El volumen pertenece a otra identidad KRaft; no se modificó. Usa un proyecto nuevo.")
    run(compose + ["up", "-d", "--no-recreate", "--wait", "--wait-timeout", "240", "kafka"], capture=False, timeout=300)
    run(compose + ["run", "--rm", "--no-deps", "kafka-init"], capture=False, timeout=300)
    # Topics are initialized and verified before exposing UI.
    verify(compose, directory / "create-topics.sh")
    run(compose + ["up", "-d", "--no-deps", "--no-recreate", "--wait", "--wait-timeout", "180", "kafka-ui"], capture=False, timeout=240)
    print("Instalación terminada. Datos conservados; candidato aún no certificado como producto.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "install", "config", "inventory", "topics", "groups", "verify", "inspect"))
    parser.add_argument("--directory", type=Path, help="Paquete independiente; omitido = runtime actual de sólo lectura")
    parser.add_argument("--project", default="em-kafka-candidate")
    parser.add_argument("--broker-port", type=int, default=19092)
    parser.add_argument("--ui-port", type=int, default=18085)
    parser.add_argument("--group", help="Grupo exacto para groups; omitido = todos")
    args = parser.parse_args()
    directory = args.directory.resolve() if args.directory else None
    if args.action == "prepare":
        if directory is None:
            parser.error("prepare requiere --directory (directorio nuevo)")
        prepare(directory, args.project, args.broker_port, args.ui_port)
        return
    if args.action == "install" and directory is None:
        parser.error("install requiere --directory; no instala ni modifica el runtime actual")
    script = directory / "create-topics.sh" if directory else ASSETS / "create-topics.sh"
    if args.action == "inventory":
        print(json.dumps(inventory(script), indent=2))
        return
    compose = bundle_config(directory) if directory else current_config()
    if args.action == "install":
        install(compose, directory)
    elif args.action == "config":
        config = json.loads(run(compose + ["config", "--format", "json"]))
        # Only this subsystem; never print the platform's PostgreSQL/provider credentials.
        print(json.dumps({"services": {k: config["services"][k] for k in SERVICES},
                          "volumes": {"kafka-data": config.get("volumes", {}).get("kafka-data")},
                          "network": config.get("networks", {}).get("event-management-net")}, indent=2))
    elif args.action == "topics":
        print(kafka(compose, "kafka-topics", "--describe"))
    elif args.action == "groups":
        selection = ["--group", args.group] if args.group else ["--all-groups"]
        print(kafka(compose, "kafka-consumer-groups", *selection, "--describe"))
    elif args.action == "verify":
        verify(compose, script)
    elif args.action == "inspect":
        print(run(compose + ["ps", "-a", *SERVICES]))
        ids = run(compose + ["ps", "-aq", "kafka", "kafka-ui"]).split()
        if not ids:
            raise ValueError("Kafka/UI no tienen contenedores en este proyecto.")
        print(run(["docker", "inspect", "--format",
                   '{{.Name}} image={{.Config.Image}} imageId={{.Image}} status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{end}}', *ids]))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, KeyError, subprocess.TimeoutExpired) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
