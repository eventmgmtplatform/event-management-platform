#!/usr/bin/env python3
"""
Validate an Event Management release manifest against the OS_08_13 JSON Schema.

Usage:
    python3 deploy/scripts/validate_release_manifest.py \
        deploy/manifests/dev/release-template.yaml
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker


DEFAULT_SCHEMA = Path("deploy/schemas/release-manifest.schema.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate an Event Management release manifest."
    )
    parser.add_argument(
        "manifest",
        type=Path,
        help="Path to the YAML release manifest.",
    )
    parser.add_argument(
        "--schema",
        type=Path,
        default=DEFAULT_SCHEMA,
        help=f"Path to the JSON Schema (default: {DEFAULT_SCHEMA}).",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"ERROR: schema file not found: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"ERROR: invalid JSON schema {path}: {exc}")


def load_yaml(path: Path) -> dict:
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"ERROR: manifest file not found: {path}")
    except yaml.YAMLError as exc:
        raise SystemExit(f"ERROR: invalid YAML manifest {path}: {exc}")

    if not isinstance(data, dict):
        raise SystemExit("ERROR: manifest root must be a YAML object.")

    return data


def format_path(parts: list[object]) -> str:
    return ".".join(str(part) for part in parts) or "<root>"


def main() -> int:
    args = parse_args()

    schema = load_json(args.schema)
    manifest = load_yaml(args.manifest)

    Draft202012Validator.check_schema(schema)

    validator = Draft202012Validator(
        schema,
        format_checker=FormatChecker(),
    )

    errors = sorted(
        validator.iter_errors(manifest),
        key=lambda error: list(error.absolute_path),
    )

    if errors:
        print(
            f"FAIL: {args.manifest} does not comply with {args.schema}.",
            file=sys.stderr,
        )
        for error in errors:
            location = format_path(list(error.absolute_path))
            print(f"- {location}: {error.message}", file=sys.stderr)
        return 1

    print(f"PASS: {args.manifest} complies with {args.schema}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
