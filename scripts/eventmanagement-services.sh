#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_NAME="$(basename "$0")"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "${SCRIPT_DIR}/../infrastructure/docker-compose.yml" ]]; then
    readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
elif [[ -f "${SCRIPT_DIR}/infrastructure/docker-compose.yml" ]]; then
    readonly PROJECT_ROOT="${SCRIPT_DIR}"
else
    echo "ERROR: no se encontró infrastructure/docker-compose.yml." >&2
    echo "Coloca este archivo en <proyecto>/scripts/${SCRIPT_NAME}." >&2
    exit 2
fi

readonly ENV_FILE="${PROJECT_ROOT}/.env"
readonly COMPOSE_FILE="${PROJECT_ROOT}/infrastructure/docker-compose.yml"
readonly WAIT_TIMEOUT="${EVENTMANAGEMENT_WAIT_TIMEOUT:-180}"

COMPOSE=(
    docker compose
    --env-file "${ENV_FILE}"
    -f "${COMPOSE_FILE}"
)

usage() {
    cat <<EOF
Uso:
  ${SCRIPT_NAME} start|stop|status|reload
  ${SCRIPT_NAME} <servicio> start|stop|status|reload

Ejemplos:
  ${SCRIPT_NAME} start
  ${SCRIPT_NAME} status
  ${SCRIPT_NAME} event-gateway reload
  ${SCRIPT_NAME} integration-worker stop
EOF
}

die() {
    echo "ERROR: $*" >&2
    exit 2
}

require_runtime() {
    command -v docker >/dev/null 2>&1 || die "docker no está instalado."
    docker compose version >/dev/null 2>&1 || die "docker compose no está disponible."
    [[ -r "${ENV_FILE}" ]] || die "no se puede leer ${ENV_FILE}."
    [[ -r "${COMPOSE_FILE}" ]] || die "no se puede leer ${COMPOSE_FILE}."
    "${COMPOSE[@]}" config --quiet || die "la configuración Compose es inválida."
}

service_exists() {
    local requested="$1"
    "${COMPOSE[@]}" config --services | grep -Fxq -- "${requested}"
}

container_id() {
    "${COMPOSE[@]}" ps -aq "$1" | head -n 1
}

wait_for_service() {
    local service="$1"
    local deadline=$((SECONDS + WAIT_TIMEOUT))
    local id state health exit_code

    while (( SECONDS < deadline )); do
        id="$(container_id "${service}")"

        if [[ -z "${id}" ]]; then
            sleep 2
            continue
        fi

        state="$(docker inspect --format '{{.State.Status}}' "${id}")"
        health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${id}")"
        exit_code="$(docker inspect --format '{{.State.ExitCode}}' "${id}")"

        if [[ "${service}" == "kafka-init" ]]; then
            if [[ "${state}" == "exited" && "${exit_code}" == "0" ]]; then
                echo "PASS: ${service} terminó correctamente."
                return 0
            fi
            if [[ "${state}" == "exited" && "${exit_code}" != "0" ]]; then
                echo "FAIL: ${service} terminó con código ${exit_code}." >&2
                return 1
            fi
        elif [[ "${health}" == "healthy" ]]; then
            echo "PASS: ${service} está healthy."
            return 0
        elif [[ -z "${health}" && "${state}" == "running" ]]; then
            echo "PASS: ${service} está running (sin health check)."
            return 0
        elif [[ "${state}" == "exited" || "${state}" == "dead" ]]; then
            echo "FAIL: ${service} está ${state}, exit=${exit_code}." >&2
            return 1
        fi

        sleep 2
    done

    echo "FAIL: ${service} no quedó disponible en ${WAIT_TIMEOUT}s." >&2
    return 1
}

start_service() {
    local service="$1"
    echo "Iniciando ${service}..."
    "${COMPOSE[@]}" up -d --build "${service}"
    wait_for_service "${service}"
}

start_all() {
    local service failed=0

    echo "Iniciando Event Management desde ${PROJECT_ROOT}..."
    "${COMPOSE[@]}" up -d --build

    while IFS= read -r service; do
        wait_for_service "${service}" || failed=1
    done < <("${COMPOSE[@]}" config --services)

    "${COMPOSE[@]}" ps -a
    (( failed == 0 )) || return 1
}

stop_service() {
    local service="$1"
    echo "Deteniendo ${service} sin eliminar datos..."
    "${COMPOSE[@]}" stop --timeout 30 "${service}"
}

stop_all() {
    echo "Deteniendo Event Management sin eliminar contenedores ni volúmenes..."
    "${COMPOSE[@]}" stop --timeout 30
    "${COMPOSE[@]}" ps -a
}

status_service() {
    local service="$1"
    local id

    "${COMPOSE[@]}" ps -a "${service}"
    id="$(container_id "${service}")"

    if [[ -n "${id}" ]]; then
        docker inspect --format \
            'Service={{ index .Config.Labels "com.docker.compose.service" }} | State={{.State.Status}} | Health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} | ExitCode={{.State.ExitCode}} | RestartCount={{.RestartCount}}' \
            "${id}"
    fi
}

status_all() {
    local service
    "${COMPOSE[@]}" ps -a

    echo
    echo "Resumen operacional:"
    while IFS= read -r service; do
        status_service "${service}" | tail -n 1
    done < <("${COMPOSE[@]}" config --services)
}

reload_service() {
    local service="$1"
    echo "Recargando ${service} mediante recreación aislada..."
    "${COMPOSE[@]}" up -d --build --no-deps --force-recreate "${service}"
    wait_for_service "${service}"
}

reload_all() {
    echo "Recargando Event Management..."
    "${COMPOSE[@]}" restart

    local service failed=0
    while IFS= read -r service; do
        [[ "${service}" == "kafka-init" ]] && continue
        wait_for_service "${service}" || failed=1
    done < <("${COMPOSE[@]}" config --services)

    "${COMPOSE[@]}" ps -a
    (( failed == 0 )) || return 1
}

main() {
    local service="all"
    local action

    case "$#" in
        1)
            action="$1"
            ;;
        2)
            service="$1"
            action="$2"
            ;;
        *)
            usage
            exit 2
            ;;
    esac

    case "${action}" in
        start|stop|status|reload) ;;
        -h|--help|help)
            usage
            exit 0
            ;;
        *)
            usage
            die "acción no válida: ${action}"
            ;;
    esac

    require_runtime

    if [[ "${service}" != "all" ]] && ! service_exists "${service}"; then
        die "servicio no válido: ${service}"
    fi

    if [[ "${service}" == "all" ]]; then
        "${action}_all"
    else
        "${action}_service" "${service}"
    fi
}

main "$@"
