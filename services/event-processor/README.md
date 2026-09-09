# Event Processor — base incremental v1.0.0

Sustituye enrichment-engine en el puerto 8082 por decisión ADR-001.
Estado: FOUNDATION; no equivale al motor de reglas completo ni a producción lista.

## Contratos y operación

- Entrada events.raw v1.0 y v1.1 del gateway; copia pública preservada.
- Representación interna PROBLEM/OK/RESOLVED, severidad entera 0..5.
- Salida events.normalized, misma eventKey (v1.0 eventId).
- Grupo enrichment-engine conservado para continuidad de offsets.
- Contrato inválido o colisión de eventId: events.dlq con código, hash y referencia
  topic/partition/offset; nunca copia el payload inválido.
- /health/live y /health/ready; alias /api/v1/enrichment conservado.
- Dependencias requeridas: PostgreSQL y Kafka. No se llama a proveedores.

Pipeline de 12 etapas, decisiones y evidencia inmutables; algoritmos pendientes
marcados SKIPPED/PENDING. Se conserva PENDING_RULES como marcador de compatibilidad.
La simulación usa el mismo pipeline sin dependencias de escritura; aún no hay API
administrativa/simulación pública hasta definir contratos y autorización.

## Persistencia y limitaciones

Aplicar infrastructure/postgres/init/009-event-processor.sql antes del arranque
en bases ya inicializadas. Es aditiva e idempotente; no modifica event_state.
processing_record y output_outbox se guardan en una sola transacción. Después se
confirma el offset de entrada. Dispatcher toma bloqueos SKIP LOCKED y publica con
acks=all; sólo entonces marca published_at. Un crash en esa ventana puede duplicar
la entrega, conservando contenido/processingId; no se afirma exactly-once.

La identidad de procesamiento SHA-256 con campos de longitud explícita está basada
en tenant+eventId. Es identidad de replay, no deduplicación de occurrences DA-06.
El hash del payload detecta colisión; en esta base igualdad exige los mismos bytes.
Dos mensajes con mismo ID y distinta serialización se rechazan conservadoramente.

Outbox batch 10/poll 1000ms, pool 1..5: valores operativos locales configurables,
no objetivos de capacidad productiva. No hay retención ni limpieza automática.
No se garantiza orden relativo por key entre múltiples dispatchers en esta base;
la validación multirréplica de lifecycle/comandos sigue PENDING.

## Validación

```bash
mvn -o -B -f services/event-processor/pom.xml test
mvn -o -B -f services/event-processor/pom.xml -Dprocessor.test.jdbc.url=jdbc:postgresql://127.0.0.1:15439/cacf_test test
python3 scripts/eventmanagement-test.py
```

Las pruebas JDBC requieren la migración 009 en la base aislada cacf_test.
Sin URL se omiten explícitamente, no cuentan como aprobadas.

## Rollback compatible

Conservar imagen/contenedor anterior event-enrichment-engine detenido. Detener
event-event-processor antes de arrancar el anterior: nunca ambos a la vez.
El predecessor comparte el grupo; no resetear offsets. Antes de retroceder,
verificar output_outbox sin pendientes para no abandonar salidas ya confirmadas.
La migración no se revierte destructivamente: preservar evidencia y salidas.
