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
    exit 2
fi

readonly ENV_FILE="${PROJECT_ROOT}/.env"
readonly COMPOSE_FILE="${PROJECT_ROOT}/infrastructure/docker-compose.yml"
readonly TICKETING_FILE="${PROJECT_ROOT}/infrastructure/docker-compose.itsm-dashboard.yml"
readonly WAIT_TIMEOUT="${EVENTMANAGEMENT_WAIT_TIMEOUT:-240}"
readonly STOP_TIMEOUT="${EVENTMANAGEMENT_STOP_TIMEOUT:-60}"
readonly LOG_TAIL="${EVENTMANAGEMENT_LOG_TAIL:-100}"

COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
if [[ -r "${TICKETING_FILE}" ]]; then
    COMPOSE+=(-f "${TICKETING_FILE}")
fi

START_ORDER=(
    postgres
    opensearch
    kafka
    kafka-init
    servicenow-mock
    gnm-mock
    event-gateway
    enrichment-engine
    integration-worker
    event-state-service
    kafka-ui
    opensearch-dashboards
    itsm-ticketing-dashboard
    event-management-console
)

STOP_ORDER=(
    event-management-console
    itsm-ticketing-dashboard
    kafka-ui
    opensearch-dashboards
    event-gateway
    enrichment-engine
    integration-worker
    event-state-service
    gnm-mock
    servicenow-mock
    kafka-init
    kafka
    opensearch
    postgres
)

# CACF retains the isolated topology and fixtures defined by OS-05.
readonly RUNTIME="${EVENTMANAGEMENT_RUNTIME:-ecosystem}"
case "${RUNTIME}" in
    local|ecosystem) ;;
    cacf-certification)
        COMPOSE=(docker compose -p cacf-certification -f "${PROJECT_ROOT}/infrastructure/docker-compose.cacf-test.yml")
        START_ORDER=(postgres kafka kafka-init next-mock servicenow-mock integration-worker)
        STOP_ORDER=(integration-worker servicenow-mock next-mock kafka-init kafka postgres)
        ;;
    *) echo "ERROR: EVENTMANAGEMENT_RUNTIME inválido: ${RUNTIME}" >&2; exit 2 ;;
esac
readonly -a START_ORDER STOP_ORDER

usage() {
    cat <<EOF
Event Management local runtime CLI

Uso:
  ${SCRIPT_NAME} <acción>
  ${SCRIPT_NAME} <servicio> <acción>

Acciones globales:
  validate   Valida archivos y contrato Compose.
  services   Lista los servicios administrados.
  status     Muestra estado detallado.
  health     Verifica estado y healthchecks.
  start      Enciende en orden, creando contenedores si faltan.
  stop       Detiene en orden sin eliminar contenedores ni datos.
  restart    Ejecuta stop y start controlados.
  reload     Recrea servicios en orden, preservando volúmenes.
  logs       Muestra los últimos ${LOG_TAIL} registros por servicio.
  down       Elimina contenedores y red Compose; conserva volúmenes.

Acciones por servicio:
  start | stop | restart | reload | status | health | logs

Ejemplos:
  ${SCRIPT_NAME} validate
  ${SCRIPT_NAME} start
  ${SCRIPT_NAME} status
  ${SCRIPT_NAME} event-gateway logs
  ${SCRIPT_NAME} integration-worker restart

Variables opcionales:
  EVENTMANAGEMENT_RUNTIME        ecosystem (default) | local | cacf-certification.
  EVENTMANAGEMENT_WAIT_TIMEOUT   Espera máxima por servicio; default ${WAIT_TIMEOUT}s.
  EVENTMANAGEMENT_LOG_TAIL       Líneas de logs; default ${LOG_TAIL}.
  EVENTMANAGEMENT_STOP_TIMEOUT   Espera graceful; default ${STOP_TIMEOUT}s.

Protecciones:
  Esta CLI no implementa down -v, volume rm, system prune ni --remove-orphans.
  No administra contenedores ajenos al proyecto, incluido open-webui.
EOF
}

die() {
    echo "ERROR: $*" >&2
    exit 2
}

require_runtime() {
    command -v docker >/dev/null 2>&1 || die "docker no está instalado."
    command -v jq >/dev/null 2>&1 || die "jq no está instalado."
    docker compose version >/dev/null 2>&1 || die "docker compose no está disponible."
    docker info >/dev/null 2>&1 || die "Docker Engine no está disponible para el usuario actual."
    [[ "${RUNTIME}" != local || -r "${ENV_FILE}" ]] || die "no se puede leer ${ENV_FILE}."
    [[ -r "${COMPOSE_FILE}" ]] || die "no se puede leer ${COMPOSE_FILE}."
    "${COMPOSE[@]}" config --quiet || die "la configuración Compose es inválida."
    local configured service found ordered
    configured="$("${COMPOSE[@]}" config --services)"
    while IFS= read -r service; do
        found=0
        for ordered in "${START_ORDER[@]}"; do
            [[ "$ordered" != "$service" ]] || found=1
        done
        (( found == 1 )) || die "servicio fuera del orden administrado: $service"
    done <<< "$configured"
    for service in "${START_ORDER[@]}" "${STOP_ORDER[@]}"; do
        service_exists "$service" || die "servicio del orden ausente en Compose: $service"
    done
}

service_exists() {
    local requested="$1"
    local configured_service

    while IFS= read -r configured_service; do
        if [[ "${configured_service}" == "${requested}" ]]; then
            return 0
        fi
    done < <("${COMPOSE[@]}" config --services)

    return 1
}

container_id() {
    "${COMPOSE[@]}" ps -aq "$1" | sed -n '1p'
}

service_dependencies() {
    local service="$1"

    "${COMPOSE[@]}" config --format json |
        jq -r           --arg service "${service}"           '.services[$service].depends_on // {}
           | to_entries[]
           | select(.value.required != false)
           | .key'
}

state_fields() {
    docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|{{.State.ExitCode}}' "$1"
}

wait_for_service() {
    local service="$1" deadline=$((SECONDS + WAIT_TIMEOUT))
    local id state health exit_code fields

    while (( SECONDS < deadline )); do
        id="$(container_id "${service}")"
        if [[ -z "${id}" ]]; then
            sleep 2
            continue
        fi

        fields="$(state_fields "${id}")"
        IFS='|' read -r state health exit_code <<<"${fields}"

        if [[ "${service}" == "kafka-init" ]]; then
            if [[ "${state}" == "exited" && "${exit_code}" == "0" ]]; then
                echo "PASS: ${service} terminó correctamente."
                return 0
            fi
            if [[ "${state}" == "exited" && "${exit_code}" != "0" ]]; then
                echo "FAIL: ${service} terminó con código ${exit_code}." >&2
                return 1
            fi
        elif [[ "${state}" == "running" && "${health}" == "healthy" ]]; then
            echo "PASS: ${service} está healthy."
            return 0
        elif [[ "${state}" == "running" && "${health}" == "none" ]]; then
            echo "PASS: ${service} está running sin healthcheck."
            return 0
        elif [[ "${state}" =~ ^(exited|dead)$ ]]; then
            echo "FAIL: ${service} está ${state}, exit=${exit_code}." >&2
            return 1
        fi
        sleep 2
    done

    echo "FAIL: ${service} no quedó disponible en ${WAIT_TIMEOUT}s." >&2
    return 1
}

validate_all() {
    "${COMPOSE[@]}" config --quiet
    echo "PASS: contrato Compose válido."
    echo "ProjectRoot=${PROJECT_ROOT}"
    printf 'Runtime=%s\n' "${RUNTIME}"
}

services_all() {
    "${COMPOSE[@]}" config --services
}

declare -A STARTING_SERVICES=()

start_service() {
    local service="$1"
    local id fields state health exit_code dependency

    if [[ "${STARTING_SERVICES[${service}]:-0}" == "1" ]]; then
        die "se detectó un ciclo de dependencias en ${service}."
    fi

    STARTING_SERVICES["${service}"]=1

    while IFS= read -r dependency; do
        [[ -n "${dependency}" ]] || continue
        service_exists "${dependency}" ||
            die "${service} depende de un servicio inexistente: ${dependency}"
        start_service "${dependency}"
    done < <(service_dependencies "${service}")


    id="$(container_id "${service}")"

    if [[ -n "${id}" ]]; then
        fields="$(state_fields "${id}")"
        IFS='|' read -r state health exit_code <<<"${fields}"

        if [[ "${service}" == "kafka-init" &&
              "${state}" == "exited" &&
              "${exit_code}" == "0" ]]; then
            echo "SKIP: kafka-init ya terminó correctamente."
            unset 'STARTING_SERVICES['"${service}"']'
            return 0
        fi

        if [[ "${state}" == "running" &&
              "${health}" == "healthy" ]]; then
            echo "SKIP: ${service} ya está healthy."
            unset 'STARTING_SERVICES['"${service}"']'
            return 0
        fi

        if [[ "${state}" == "running" &&
              "${health}" == "none" ]]; then
            echo "SKIP: ${service} ya está running."
            unset 'STARTING_SERVICES['"${service}"']'
            return 0
        fi
    fi


    echo "Iniciando ${service}; sus dependencias ya fueron verificadas..."
    "${COMPOSE[@]}" up -d --no-build --no-deps "${service}"
    wait_for_service "${service}"

    unset 'STARTING_SERVICES['"${service}"']'
}

start_all() {
    local service
    echo "Iniciando Event Management en orden controlado..."
    for service in "${START_ORDER[@]}"; do
        service_exists "${service}" || die "START_ORDER contiene un servicio inexistente: ${service}"
        start_service "${service}"
    done
    health_all
}

stop_service() {
    local service="$1"
    echo "Deteniendo ${service}; se conservan contenedor y datos..."
    "${COMPOSE[@]}" stop --timeout "${STOP_TIMEOUT}" "${service}"
    local id fields state health exit_code
    id="$(container_id "$service")"
    [[ -n "$id" ]] || return 0
    fields="$(state_fields "$id")"
    IFS='|' read -r state health exit_code <<< "$fields"
    [[ "$state" =~ ^(exited|created)$ ]] || die "$service no se detuvo: $state"
}

stop_all() {
    local service
    echo "Deteniendo Event Management en orden controlado..."
    for service in "${STOP_ORDER[@]}"; do
        service_exists "${service}" || die "STOP_ORDER contiene un servicio inexistente: ${service}"
        stop_service "${service}"
    done
    "${COMPOSE[@]}" ps -a
}

status_service() {
    local service="$1" id
    "${COMPOSE[@]}" ps -a "${service}"
    id="$(container_id "${service}")"
    if [[ -z "${id}" ]]; then
        echo "Service=${service} State=absent Health=n/a ExitCode=n/a RestartCount=n/a"
        return 1
    fi
    docker inspect --format 'Service={{index .Config.Labels "com.docker.compose.service"}} State={{.State.Status}} Health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} ExitCode={{.State.ExitCode}} RestartCount={{.RestartCount}}' "${id}"
}

status_all() {
    local service failed=0
    "${COMPOSE[@]}" ps -a
    echo
    echo "Resumen operacional:"
    while IFS= read -r service; do
        status_service "${service}" | tail -n 1 || failed=1
    done < <("${COMPOSE[@]}" config --services)
    return "$failed"
}

health_service() {
    local service="$1" id fields state health exit_code
    id="$(container_id "${service}")"
    if [[ -z "${id}" ]]; then
        echo "FAIL: ${service} no tiene contenedor." >&2
        return 1
    fi
    fields="$(state_fields "${id}")"
    IFS='|' read -r state health exit_code <<<"${fields}"
    if { [[ "${service}" == kafka-init && "${state}" == exited && "${exit_code}" == 0 ]]; } ||
       { [[ "${service}" != kafka-init && "${state}" == running && "${health}" =~ ^(healthy|none)$ ]]; }; then
        echo "PASS: ${service} state=${state} health=${health} exit=${exit_code}"
        return 0
    fi
    echo "FAIL: ${service} state=${state} health=${health} exit=${exit_code}" >&2
    return 1
}

health_all() {
    local service failed=0
    while IFS= read -r service; do
        health_service "${service}" || failed=1
    done < <("${COMPOSE[@]}" config --services)
    (( failed == 0 ))
}

reload_service() {
    local service="$1"
    echo "Recreando ${service} sin iniciar dependencias..."
    "${COMPOSE[@]}" up -d --build --no-deps --force-recreate "${service}"
    wait_for_service "${service}"
}

reload_all() {
    local service
    echo "Recreando Event Management en orden controlado..."
    for service in "${START_ORDER[@]}"; do
        reload_service "${service}"
    done
    health_all
}

restart_service() {
    local service="$1"
    stop_service "${service}"
    start_service "${service}"
}

restart_all() {
    stop_all
    start_all
}

logs_service() {
    "${COMPOSE[@]}" logs --tail "${LOG_TAIL}" "$1"
}

logs_all() {
    "${COMPOSE[@]}" logs --tail "${LOG_TAIL}"
}

down_all() {
    echo "Eliminando contenedores y red Compose; los volúmenes nombrados se conservan."
    "${COMPOSE[@]}" down --timeout "${STOP_TIMEOUT}"
    echo "PASS: down completado sin opción --volumes."
    docker volume ls --filter label=com.docker.compose.project=event-management
}

main() {
    local service="all" action
    case "$#" in
        1) action="$1" ;;
        2) service="$1"; action="$2" ;;
        *) usage; exit 2 ;;
    esac

    case "${action}" in
        -h|--help|help) usage; exit 0 ;;
        validate|services|start|stop|restart|reload|status|health|logs|down) ;;
        *) usage; die "acción no válida: ${action}" ;;
    esac

    if [[ "${RUNTIME}" == ecosystem ]]; then
        # Preserve per-service calls against the principal runtime. CACF services
        # can be selected explicitly with EVENTMANAGEMENT_RUNTIME.
        if [[ "${service}" != all ]]; then
            EVENTMANAGEMENT_RUNTIME=local bash "${SCRIPT_DIR}/eventmanagement-services.sh" "$@"
            return
        fi
        local runtime failed=0
        # Check every inventory before any lifecycle mutation.
        for runtime in local cacf-certification; do
            EVENTMANAGEMENT_RUNTIME="$runtime" bash "${SCRIPT_DIR}/eventmanagement-services.sh" validate
        done
        for runtime in local cacf-certification; do
            echo "=== EventManagementOpenSource: $runtime / $action ==="
            if EVENTMANAGEMENT_RUNTIME="$runtime" bash "${SCRIPT_DIR}/eventmanagement-services.sh" "$action"; then
                :
            else
                failed=1
                case "$action" in status|health|logs) ;; *) return 1 ;; esac
            fi
        done
        return "$failed"
    fi

    require_runtime

    if [[ "${service}" != "all" ]]; then
        case "${action}" in
            validate|services|down) die "${action} solo admite alcance global." ;;
        esac
        service_exists "${service}" || die "servicio no válido: ${service}"
        "${action}_service" "${service}"
    else
        "${action}_all"
    fi
}

main "$@"
