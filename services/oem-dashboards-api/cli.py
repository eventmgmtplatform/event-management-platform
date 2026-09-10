"""Source settings are hot-read by the BFF; atomic replacement needs no container restart."""
import argparse
import fcntl
import json
import os
import tempfile
from pathlib import Path

from dashboard import CONFIG_PATH, DOMAINS, MODES, DashboardError, load_dashboard, parse_query, read_config, validate_config


def atomic_write(path, content):
    fd, temporary = tempfile.mkstemp(prefix=".dashboard-", dir=path.parent)
    try:
        with os.fdopen(fd, "w") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def probe(config, domain="all"):
    from delivery import parse_delivery_query
    from collection import parse_collection_query
    for key in DOMAINS if domain == "all" else (domain,):
        load_dashboard(config, key, (parse_collection_query if key == "data-collection" else parse_delivery_query if key == "delivery" else parse_query)({"limit": ["1"]}))


def set_source(path, domain, mode, tester=probe):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path.with_suffix(".lock"), "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        config = read_config(path)
        if domain == "all":
            config.update(default=mode, sources={})
        else:
            config["sources"][domain] = mode
        validate_config(config)
        tester(config, domain)  # Failure leaves the existing file untouched.
        old = path.read_text() if path.exists() else None
        if old is not None:
            atomic_write(path.with_suffix(".previous.json"), old)
        atomic_write(path, json.dumps(config, indent=2) + "\n")
        try:
            tester(read_config(path), domain)
        except Exception:
            if old is None:
                path.unlink(missing_ok=True)
            else:
                atomic_write(path, old)
            raise
    return config


def main():
    parser = argparse.ArgumentParser(description="OEM Dashboards: fuentes de lectura; API significa API interna OEM.")
    parser.add_argument("--config", type=Path, default=CONFIG_PATH)
    commands = parser.add_subparsers(dest="command", required=True)
    source = commands.add_parser("source").add_subparsers(dest="action", required=True)
    source.add_parser("get")
    test = source.add_parser("test")
    test.add_argument("--dashboard", choices=("all", *DOMAINS), default="all")
    change = source.add_parser("set")
    change.add_argument("mode", choices=(*MODES, "api"))
    change.add_argument("--dashboard", choices=("all", *DOMAINS), default="all")
    commands.add_parser("config").add_argument("action", choices=("show",))
    commands.add_parser("smoke-test")
    args = parser.parse_args()
    try:
        config = read_config(args.config)
        if args.command == "source" and args.action == "set":
            config = set_source(args.config, args.dashboard, "internal-api" if args.mode == "api" else args.mode)
        elif args.command == "smoke-test" or (args.command == "source" and args.action == "test"):
            probe(config, getattr(args, "dashboard", "all"))
            print("OK: fuentes consultadas, contrato válido.")
            return 0
        print(json.dumps(config, indent=2))  # Configuration contains modes only, never secrets.
        return 0
    except (DashboardError, OSError):
        print("ERROR: configuración o fuente no disponible; cambio no aplicado o revertido. Verifica entorno, permisos, contrato y migración 018.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
