# OS_01_01 — Contrato Zabbix Message Bus

Estado: implementado y validado en `feature/os-01-01-laboratorio-event-management`.

## Checkpoints

- `2b138db`: persistencia Kafka e idempotencia de resultados.
- `f8b4a43`: controles seguros del runtime local.
- `cc23e2c`: contrato nativo Zabbix Message Bus.
- `3927b0a`: ocho pruebas automatizadas del normalizador.

## Ingreso

- Endpoint: `POST /api/v1/events`
- Topic: `events.raw`
- Contrato nativo: `schemaVersion=1.1`
- Contrato anterior compatible: `schemaVersion=1.0`

El payload nativo exige: `source`, `InstanceSituation`, `AlertKey`, `InstanceValue`, `hostname`, `ClassName`, `Type`, `origin`, `msg`, `severity`, `Component`, `InstanceId`, `Node`, `NodeAlias`, `ApplId`, `CustomerCode`, `SubComponent` y `ExpireTime`.

## Lifecycle

| Type | InstanceValue | Acción | Severidad efectiva |
|---:|---:|---|---:|
| `1` | `0` | `OPEN` | `sourceSeverity` |
| `0` | `1` | `CLOSE` | `0` |

`severity` no determina apertura o recuperación. En una recuperación se conserva `sourceSeverity=5`, mientras `effectiveSeverity=0`. Las combinaciones contradictorias se rechazan con HTTP 400.

## Identidad

Identidad canónica:

```text
CustomerCode:source:AlertKey:Node:InstanceId
```

Identidad heredada:

```text
AlertKey:Node:InstanceId:source
```

La identidad canónica es la Kafka key para v1.1. En v1.0 se conserva `eventId` como key. `SDC` y `SD2` permanecen separados; no existe alias automático.

## Salida v1.1

Incluye `eventId`, `eventKey`, `legacyEventKey`, `tenant.code`, `source`, `resource`, `condition`, `summary`, `sourceSeverity`, `effectiveSeverity`, `sourceType`, `lifecycleAction`, `expirationSeconds`, `timestamps` y `originalEvent`.

`SubComponent` se divide por comas, se limpia y se convierte a `resource.subcomponents[]`. El payload original se conserva como copia independiente.

## Validaciones certificadas

- Apertura y recuperación HTTP 202.
- Dos mensajes con la misma identidad y Kafka key.
- Compatibilidad legacy v1.0.
- Rechazo de lifecycle contradictorio.
- Separación SDC/SD2.
- Gateway saludable, sin reinicios.
- JUnit: 8 pruebas, 0 fallas, 0 errores.

## Fuera de alcance

Enriquecimiento, correlación, blackouts, maintenance, persistencia del evento Zabbix en `event_state`, procesamiento `events.raw -> events.normalized`, DLQ del gateway y autenticación productiva.
