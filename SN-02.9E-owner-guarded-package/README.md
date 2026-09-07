# SN-02.9E — Owner-Guarded Recovery Package

Paquete de implementación incremental para cerrar `TECHNICAL_DEBT_SN_006` sin reintentar a ciegas una creación de ticket.

## Baseline obligatoria

- Rama: `feature/os-01-02-servicenow-core-foundation`
- Commit para el checkpoint operativo: `74d75b0f4dec94a2b1d0aaaa06ec18aa2d61385e`
- Migración 005 creada y aplicada.
- `evidence/` debe permanecer fuera de Git.

## Modelo operativo

| Modo | Consume comandos nuevos | Escanea pendientes | Acción sobre claims vencidos |
|---|---:|---:|---|
| `standby` | No | No | Ninguna |
| `pull_restart` | Después de la barrera | Sí | Lookup en ServiceNow y reconciliación |
| `active` | Sí | No | Los duplicados vigentes se suprimen |

`pull_restart` no significa volver a ejecutar `POST`. Primero consulta ServiceNow usando una identidad determinista. Solo una respuesta certificada como `NOT_FOUND` permite considerar una creación posterior.

## Orden de ejecución

1. Ejecutar `scripts/00-preflight.sh`.
2. Ejecutar `scripts/01-install-owner-guarded-ledger.sh`.
3. Ejecutar `scripts/02-source-certification.sh`.
4. Ejecutar `scripts/03-install-servicenow-lookup.sh` después de certificar SN-02.9E en runtime.
5. Ejecutar `scripts/04-install-operational-control.sh` desde la baseline certificada de reconciliación (`74d75b0`).
6. Devolver la salida completa antes del reload de la imagen con control persistente.

El script 04 instala el estado durable, la API autenticada y la barrera de
admisión. También separa liveness de readiness para que `standby` mantenga el
contenedor vivo sin aceptar trabajo. El coordinador que barre el backlog en
`pull_restart` sigue siendo el siguiente checkpoint y no debe simularse con un
arranque directo del consumidor Kafka.

No ejecutar los LAB finales ni crear un commit si falla una aserción.

## Criterio de cierre SN-006

La deuda solamente puede marcarse `RESOLVED` cuando pasen todos los controles de `docs/acceptance-matrix.md`, especialmente caída después del `POST`, takeover atómico, lookup, publicación terminal única, cambio `standby → pull_restart` y reinicio del componente con backlog.
