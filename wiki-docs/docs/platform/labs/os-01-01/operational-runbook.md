# OS_01_01 — Runbook operativo

## Runtime

```bash
cd /opt/event-management-platform
./scripts/eventmanagement-services.sh start
./scripts/eventmanagement-services.sh status
./scripts/eventmanagement-services.sh event-gateway reload
./scripts/eventmanagement-services.sh event-state-service reload
./scripts/eventmanagement-services.sh stop
```

El script resuelve la raíz del repositorio y usa explícitamente `.env`. `stop` preserva volúmenes. `kafka-init` terminado con `Exited (0)` es correcto.

## Health

```bash
curl -fsS http://localhost:8081/api/v1/gateway | jq .
curl -fsS http://localhost:8084/health/ready | jq .
```

## Compilación y pruebas

```bash
cd services/event-gateway
./mvnw clean test
./mvnw package
```

Resultado certificado: 8 pruebas, 0 fallas, 0 errores.

## Enviar fixtures

```bash
curl -fsS -H 'Content-Type: application/json' --data-binary \
  @testing/fixtures/events/sdc/zabbix-messagebus-problem.json \
  http://localhost:8081/api/v1/events | jq .

curl -fsS -H 'Content-Type: application/json' --data-binary \
  @testing/fixtures/events/sdc/zabbix-messagebus-recovery.json \
  http://localhost:8081/api/v1/events | jq .
```

## Idempotencia

Tabla: `event_management.processed_integration_result`. Una entrega idéntica conserva `version=1` y una sola reclamación. La protección persiste tras recrear `event-state-service`. Una colisión de `resultId` se rechaza, no modifica PostgreSQL/OpenSearch y no confirma el offset.

## Diagnóstico

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml logs \
  --no-color --timestamps --tail 250 event-gateway event-state-service

docker exec event-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --group event-state-service --describe
```

## Seguridad

Las credenciales PostgreSQL de Compose se parametrizan mediante `${POSTGRES_DB}`, `${POSTGRES_USER}` y `${POSTGRES_PASSWORD}`. La credencial histórica existe desde `30817ba`; su rotación y saneamiento del historial son tareas separadas.
