# Resultado de validación — 2026-09-10

**PASS: nueva imagen desplegada, certificación local con reinicio aprobada y
comando combinado de CLI aprobado. No es un release ni una certificación V1.**

- Rama: `feature/os-05-core-event-state-service`.
- HEAD de origen: `887baeaf51f1c9274786388dc36b175dce30b3bb`.
- Cambios sin commit ni publicación.
- Imagen activa:
  `sha256:53ae603ef4a3de66fd4a5caef359d7be415a78f3ed9a6e711a6d580d15d4b4c2`.
- Maven verify desde CLI: **17 pruebas, 0 fallos, 0 errores, 0 omitidas**:
  4 de contrato, 6 de frontera de procesamiento y 7 de PostgreSQL/JTA real.
- Consumidor ESS activo; lag cero en las seis particiones de integration.results.
- Ambas ejecuciones retiraron sus reglas sintéticas; conservaron datos y auditoría.

## Ejecuciones aprobadas

| Ejecución | Resultado | Evidencia local excluida de Git |
|---|---|---|
| Build, respaldo, DDL 016 y despliegue | PASS | `evidence/os-05-ess/deployment/20260910T061720851074Z/report.json` |
| E2E con reinicio de ESS | 7 checks PASS | `evidence/os-05-ess/runtime/4f8b4cf88b9645ac9ba1a7c70109213a/report.json` |
| `bash scripts/emctl event-state-service test` | Java + 6 checks de servicio PASS | `evidence/os-05-ess/runtime/170e43180c2242039e92870e9ad8a7af/report.json` |

El recorrido validado es gateway → Kafka → Processor → Worker → ServiceNow mock
→ integration.results → ESS → PostgreSQL/OpenSearch. Se comprobaron referencias
de comando/resultado, igualdad de estado y proyección, duplicados sin mutación,
segundo resultado GNM sintético, cuarentena durable sin mutación del agregado,
lag cero y persistencia tras reinicio. La ejecución CLI omite explícitamente el
reinicio, que fue aprobado en la ejecución separada del despliegue.

## Repetición

```bash
bash scripts/emctl event-state-service test
# También reinicio en la misma ejecución:
python3 testing/certifications/event-state-certification.py --verify --restart
```

Requiere PostgreSQL aislado `ess-cert-postgres` en loopback:15440, disponible al
cerrar esta validación. La base aleatoria de cada IT se elimina al terminar.
La plantilla y los prerrequisitos están en `docs/event-state-service/README.md`.

## Historial del bloqueo resuelto

El primer intento detectó un mensaje histórico sin resultId en partición 0,
offset 1348, que bloqueaba indefinidamente el consumidor aunque health=UP.
Se implementó cuarentena persistente y se aplicó DDL 016 con respaldo. La primera
certificación del despliegue agotó 90s mientras procesaba el backlog y restauró
correctamente la imagen anterior. El segundo intento fue rechazado en permisos
y el usuario pausó el trabajo. Tras reanudarlo, el despliegue con espera de 240s
pasó y dejó la nueva imagen activa, sin resetear offsets ni borrar volúmenes.
Las ejecuciones fallidas se conservan como evidencia histórica.

## Límites

El PASS corresponde al servicio actual y a proveedores locales simulados:
ServiceNow mock y un resultado GNM sintético, no una ejecución real de GNM.
Lifecycle OPEN/CLOSE/reopen de V1, outbox, ordenamiento de resultados antiguos,
reconciliación, API tenant-scoped y publicación Kafka de cuarentena siguen
pendientes en `docs/event-state-service/gaps.md`. No se apagaron las dependencias
compartidas para probar fallos: esas fronteras se validaron mediante pruebas
controladas y PostgreSQL/JTA aislado.
