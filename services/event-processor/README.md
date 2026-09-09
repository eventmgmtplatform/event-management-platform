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

Aplicar las migraciones 009-event-processor.sql y 010-processor-outbox-recovery.sql
antes del arranque en bases ya inicializadas. Son aditivas e idempotentes; no
modifican event_state. La readiness comprueba las columnas de recuperación.
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
Cada salida conserva attempts, last_attempt_at, next_attempt_at y un código de error
seguro. El backoff exponencial (local: 1s inicial, 30s máximo) sobrevive reinicios;
no se descartan mensajes por superar un número arbitrario de intentos. Un error
bloquea sólo la misma pareja topic/key, no las claves independientes.
La secuencia de inserción impide adelantar filas pendientes ya comprometidas de la
misma clave entre dispatchers. Esto no impone orden temporal a eventos de origen
fuera de orden ni a transacciones todavía no comprometidas. Lifecycle multirréplica
sigue PENDING. El shutdown deja de tomar nuevas filas y permite terminar la actual.

## Validación

```bash
mvn -o -B -f services/event-processor/pom.xml test
mvn -o -B -f services/event-processor/pom.xml -Dprocessor.test.jdbc.url=jdbc:postgresql://127.0.0.1:15439/cacf_test test
python3 scripts/eventmanagement-test.py
```

Las pruebas JDBC requieren las migraciones 009 y 010 en la base aislada cacf_test.
Sin URL se omiten explícitamente, no cuentan como aprobadas.

## Rollback compatible

Conservar imagen/contenedor anterior event-enrichment-engine detenido. Detener
event-event-processor antes de arrancar el anterior: nunca ambos a la vez.
El predecessor comparte el grupo; no resetear offsets. Antes de retroceder,
verificar output_outbox sin pendientes para no abandonar salidas ya confirmadas.
La migración no se revierte destructivamente: preservar evidencia y salidas.

## Pruebas de recuperación

```bash
python3 scripts/event-processor-recovery.py
python3 scripts/event-processor-dependency-recovery.py
```

La primera crea bases temporales sólo en el contenedor PostgreSQL del laboratorio
CACF, prueba fresh/upgrade, backup/restore y replay con adaptadores productivos y
transporte Kafka simulado. Conserva dump/checksum/evidencia y elimina exclusivamente
las bases que creó. No es certificación DR integral ni restaura el estado de otros servicios.
La segunda detiene brevemente event-postgres local, publica un evento sintético,
comprueba live=200, ready=503 y lag pendiente, y restaura PostgreSQL en finally.
Después exige una sola salida durable y lag=0. Ejecutarla durante ventana local
de pruebas porque PostgreSQL es compartido. No llama a proveedores reales.

Los tiempos medidos se guardan en evidence/os-02-event-processor/recovery/;
RPO/RTO y rendimiento productivos siguen PENDING al no tener objetivos aprobados.
