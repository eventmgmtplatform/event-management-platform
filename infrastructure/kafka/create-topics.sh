#!/bin/bash

set -euo pipefail

KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka:29092}"
KAFKA_TOPICS_COMMAND="/opt/kafka/bin/kafka-topics.sh"

echo "============================================================"
echo "EVENT MANAGEMENT - KAFKA TOPIC INITIALIZATION"
echo "============================================================"
echo "Bootstrap server: ${KAFKA_BOOTSTRAP_SERVER}"
echo "============================================================"

topic_exists() {
    local requested_topic="$1"
    local configured_topic

    while IFS= read -r configured_topic; do
        if [[ "${configured_topic}" == "${requested_topic}" ]]; then
            return 0
        fi
    done < <(
        "${KAFKA_TOPICS_COMMAND}"             --bootstrap-server "${KAFKA_BOOTSTRAP_SERVER}"             --list
    )

    return 1
}

create_topic() {
    local topic_name="$1"
    local partitions="$2"
    local replication_factor="$3"
    local cleanup_policy="$4"
    local retention_ms="$5"

    echo
    echo "Validando topic: ${topic_name}"

    if topic_exists "${topic_name}"; then

        echo "EXISTENTE: ${topic_name}"
        return 0
    fi

    "${KAFKA_TOPICS_COMMAND}" \
        --bootstrap-server "${KAFKA_BOOTSTRAP_SERVER}" \
        --create \
        --topic "${topic_name}" \
        --partitions "${partitions}" \
        --replication-factor "${replication_factor}" \
        --config "cleanup.policy=${cleanup_policy}" \
        --config "retention.ms=${retention_ms}"

    echo "CREADO: ${topic_name}"
}

# 7 días
RETENTION_STANDARD="604800000"

# 30 días
RETENTION_HISTORY="2592000000"

# 14 días
RETENTION_DLQ="1209600000"

create_topic "events.raw"              3 1 "delete"  "${RETENTION_STANDARD}"
create_topic "events.normalized"       3 1 "delete"  "${RETENTION_STANDARD}"
create_topic "events.lifecycle"        3 1 "delete"  "${RETENTION_HISTORY}"
create_topic "integration.commands"    6 1 "delete"  "${RETENTION_STANDARD}"
create_topic "integration.results"     6 1 "delete"  "${RETENTION_HISTORY}"
create_topic "integration.callbacks"   3 1 "delete"  "${RETENTION_STANDARD}"
create_topic "event.journal"           3 1 "delete"  "${RETENTION_HISTORY}"
create_topic "events.dlq"              1 1 "delete"  "${RETENTION_DLQ}"

echo
echo "============================================================"
echo "TOPICS DISPONIBLES"
echo "============================================================"

"${KAFKA_TOPICS_COMMAND}" \
    --bootstrap-server "${KAFKA_BOOTSTRAP_SERVER}" \
    --list |
    sort

echo "============================================================"
echo "INICIALIZACIÓN FINALIZADA"
echo "============================================================"
