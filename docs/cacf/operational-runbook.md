# Runbook local y mantenimiento

## Preparación

Requisitos: Docker Engine/Compose, puertos 15439/18083/18183/18184 disponibles,
Java 21 y Maven para tests fuera del contenedor, Python 3 para el E2E. El build
requiere imágenes y dependencias disponibles; mvn -o exige caché Maven poblada.
Ejecutar desde la raíz del repositorio.

```sh
docker compose -p cacf-certification -f infrastructure/docker-compose.cacf-test.yml up -d postgres next-mock servicenow-mock kafka kafka-init
docker compose -p cacf-certification -f infrastructure/docker-compose.cacf-test.yml stop integration-worker
```

Desde services/integration-worker ejecutar:

```sh
mvn -o test -Dcacf.test.jdbc.url=jdbc:postgresql://localhost:15439/cacf_test
```

Volver a la raíz y ejecutar:

```sh
docker compose -p cacf-certification -f infrastructure/docker-compose.cacf-test.yml up -d --build integration-worker
python3 scripts/cacf-local-certification.py
```

El script crea ejecuciones sintéticas y reinicia el worker aislado. No elimina
sus registros. Usa credenciales públicas fijas de laboratorio. El volumen
cacf-test-postgres conserva evidencia; Kafka de este Compose no tiene volumen
persistente declarado: reinicio del worker está probado, pérdida/recreación del
broker no lo está. No usar down -v para una pausa operativa.

## Salud y diagnóstico

```sh
curl --fail http://127.0.0.1:18083/health/ready
curl --fail -H 'X-CACF-Token: cacf-local-test' http://127.0.0.1:18083/metrics
docker compose -p cacf-certification -f infrastructure/docker-compose.cacf-test.yml ps
docker compose -p cacf-certification -f infrastructure/docker-compose.cacf-test.yml logs --tail 100 integration-worker
```

Las métricas son gauges: cacf_executions, cacf_results, cacf_callbacks_duplicate,
cacf_callbacks_late, cacf_outbox_pending y cacf_provider_review. Son conteos SQL
actuales, no contadores monotónicos. Readiness CACF verifica consultas SQL y ruta
cacf-command-admission iniciada; no prueba disponibilidad NEXT/ServiceNow ni
progreso de cada scheduler. Salud compartida añade sus propias comprobaciones.

| Síntoma | Inspección / respuesta operativa |
|---|---|
| 401 | comprobar token externo y header, sin imprimir secretos |
| 404 de todas las APIs | comprobar CACF_ENABLED; en GET/callback puede ser identidad ausente |
| 409 | comparar identidades y ticket; no inventar otro UUID para reintentar una mutación incierta |
| SUBMITTED prolongado | comprobar ACK, submitted_at y plazo sin ACK |
| TIMED_OUT | revisar deadline y evidencia tardía; no revertir estado manualmente |
| provider_review creciente | revisar evidencia/estado del proveedor; no devolver IN_FLIGHT a PENDING a ciegas |
| outbox_pending creciente | comprobar Kafka; el pendiente más antiguo bloquea publicación posterior |
| UNKNOWN | revisar XML conservado; no asumir éxito a partir del status Kafka |
| fallo ServiceNow | revisar ledger/resultados foundation y número de ticket; no repetir PATCH incierto |

Consultas de diagnóstico desde psql (solo lectura):

```sql
SELECT execution_id,state,outcome,accepted_at,deadline_at,completed_at
FROM event_management.automation_execution ORDER BY requested_at DESC LIMIT 20;
SELECT execution_id,operation,status,claimed_at
FROM event_management.automation_provider_dispatch WHERE status='REVIEW';
SELECT sequence_id,execution_id,event_type,created_at
FROM event_management.automation_outbox WHERE NOT published ORDER BY sequence_id;
```

## Migración y activación en runtime principal

Esta generación no se aplicó al runtime principal. El overlay CACF requiere el
Compose base del repositorio y .env del operador. README.md contiene los comandos
acotados de migración y activación. Comprobar backup según política local antes de
aplicar migraciones sobre datos existentes. No se distribuyen .env ni secretos.
Para desactivar CACF usar CACF_ENABLED=false y recrear el worker con la configuración
local correspondiente; conservar tablas y evidencia. Al reactivarlo, deadlines
absolutos siguen vigentes y pueden vencer inmediatamente. El overlay de esta
generación fija true; no basta cambiar una variable que el overlay sobrescribe.
