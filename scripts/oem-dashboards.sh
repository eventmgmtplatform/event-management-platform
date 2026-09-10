#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose --env-file "${ROOT}/.env" -f "${ROOT}/infrastructure/docker-compose.yml" -f "${ROOT}/infrastructure/docker-compose.oem-dashboards.yml")
if [[ -f "${ROOT}/.local/oem-dashboards/runtime.env" ]]; then
  COMPOSE+=(--env-file "${ROOT}/.local/oem-dashboards/runtime.env")
fi
case "${1:-help}" in
  source|config|smoke-test)
    exec "${COMPOSE[@]}" exec -T oem-dashboards-api python cli.py "$@" ;;
  status) exec "${COMPOSE[@]}" ps event-management-console oem-dashboards-api ;;
  health)
    exec "${COMPOSE[@]}" exec -T oem-dashboards-api python -c 'import urllib.request; print(urllib.request.urlopen("http://127.0.0.1:8092/ready", timeout=60).read().decode())' ;;
  start) exec "${COMPOSE[@]}" up -d --build --no-deps oem-dashboards-api event-management-console ;;
  stop) exec "${COMPOSE[@]}" stop oem-dashboards-api ;;
  restart|reload) exec "${COMPOSE[@]}" restart oem-dashboards-api event-management-console ;;
  logs) exec "${COMPOSE[@]}" logs --tail 100 event-management-console oem-dashboards-api ;;
  help|--help|-h)
    cat <<'HELP'
emctl ui oem-dashboards <status|health|start|stop|restart|reload|logs|smoke-test>
emctl ui oem-dashboards source get
emctl ui oem-dashboards source set <postgresql|internal-api|api> [--dashboard events|ticketing|gnm|cacf|delivery|data-collection|all]
emctl ui oem-dashboards source test [--dashboard events|ticketing|gnm|cacf|delivery|data-collection|all]
emctl ui oem-dashboards config show
Source set validates connectivity, saves atomically and rolls back on failed verification.
Configuration is hot-read. Credentials belong to the BFF environment, never source config.
HELP
    ;;
  *) echo "Acción de dashboards no soportada." >&2; exit 2 ;;
esac
