# Resultado de validación — 2026-09-10

## API administrativa desplegada — PASS

Entrega de lectura con tokens vinculados a tenant y permiso separado de operador.
34 pruebas ESS (13 unitarias + 21 PostgreSQL/HTTP/JTA), cero fallos u omitidas.
10 checks de servicio con plantilla 1.2.0, incluido `admin-api` y reinicio.
CLI `bash scripts/emctl event-state-service admin` comprobada: sólo devuelve el tenant configurado.

- Despliegue sólo ESS: `evidences/os-05-ess/lifecycle-deployment/20260910T152356961496Z/report.json`.
- Servicio: `evidences/os-05-ess/runtime/f37d7c2d9c7e402293521a05618e1d38/report.json`.
- Contrato y límites: [API administrativa](admin-api.md).

Processor/Worker y laboratorios se conservaron. API y CLI están disponibles;
interfaz visual, autenticación central, auditoría durable y mutaciones quedan pendientes.


## Runtime compartido actualizado — PASS

Despliegue coordinado Processor/Worker/ESS aprobado el 2026-09-10 desde
`051243ea016190f22bc78e886ab042b2a7a1c069`, con cambios locales sin consolidar.
Respaldo de event_management/event_processor; migraciones aditivas 016/017/020/021.
Construcción offline desde snapshots temporales, sin tocar targets del laboratorio.

- 29 pruebas ESS: 13 unitarias y 16 PostgreSQL/JTA, sin fallos ni omitidas.
- 9 checks de servicio PASS: CLI, readiness, Gateway→Processor→Worker→ESS→Search,
  duplicados, segunda integración, cuarentena, OPEN/CLOSE/reapertura,
  replay/stale y reinicio.
- Evidencia de despliegue: `evidences/os-05-ess/lifecycle-deployment/20260910T144900182328Z/report.json`.
- Evidencia de servicio: `evidences/os-05-ess/runtime/c6e206ef2e0d424dbb5ccd338f2e558c/report.json`.
- Harness con regresión de particiones Kafka vacías: PASS,
  `evidences/testing/20260910T144831042772Z-20620c4b/harness/report.json`.

El primer despliegue restauró correctamente las tres imágenes al detectar un
falso atraso: particiones vacías con offset no inicializado. La prueba ahora
admite `-` únicamente si LOG-END-OFFSET=0; lag real o desconocido con datos falla.

Imágenes aprobadas:

- event-state-service: `sha256:84a143f3d11529a55e2d03493ebb96bcf9f1565fc45972c3ff438de92d70d09a`.
- integration-worker: `sha256:7a931b4bb4c8f2d96ddec809243cf14a08f33739159e858a7d2d5e4a551b4519`.
- event-processor: `sha256:e20fb80d937e04767babec7c69b57532913e500c8d0c03d57a03d85f4ed0ed0b`.

Alcance: ServiceNow mock y resultado GNM sintético para consolidación. Esta ejecución no activó NEXT ni tenants GNM. Posteriormente se activaron y
certificaron en el runtime compartido: véase [CACF/GNM compartido](../cacf/shared-activation.md). No incorpora API administrativa ESS, outbox ESS ni rebuild.

Las secciones siguientes conservan el historial previo y no reemplazan este resultado.


## Incremento OS_11: laboratorio aislado

`evidences/os11/summary.json` registra **307 pruebas Java**: Processor 100,
Worker 178, ESS 29; cero fallos, errores u omitidas. UC-001 tiene PASS en
`evidences/testing/20260910T082012414921Z-1fce70d8/happy-path/report.json`;
el reinicio tiene PASS en
`evidences/os11/restart/ee536714c94c44f4b2da4f3aa9c04951/report.json`.
Blackout y harness también tienen PASS registrado.

El flujo incluye ticket, GNM confirmado, automatización, recuperación, cierre
GNM y ServiceNow RESOLVED confirmado, proyección ESS y duplicados sin mutaciones
adicionales. Alcance: `os11-lifecycle`, proveedores simulados. No acredita
despliegue compartido, proveedores reales ni todos los puntos de fallo.

## Repetición tras reorganizar testing

- UC-001: PASS, `evidences/testing/20260910T085038208827Z-0a7c3aba/happy-path/report.json`.
- CLI compartida: FAIL de prerrequisito, antes de fixtures y Maven,
  `evidences/os-05-ess/runtime/06e495ecddc0442cbfa59229d148afe2/report.json`.
  Las tres tablas requeridas existen; falta el grupo `event-state-service-lifecycle`.
  Revisar el despliegue coordinado antes de repetir. No se promovieron imágenes.
- Harness: 17 pruebas PASS, incluidas 4 de preflight ESS; evidencia
  `evidences/testing/20260910T085134821254Z-6c33543a/harness/report.json`.
- Los intentos previos confinados por el sandbox fallaron por permisos locales;
  se conservaron sus reportes y se repitieron con acceso autorizado.

## Baseline compartida: evidencia histórica

Las cifras, imagen y lag siguientes corresponden a la ejecución histórica,
no a una inspección actual ni al incremento OS_11.

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

Antes de repetir, consultar `lifecycle-status.md`: el runner actual exige el
incremento lifecycle en el runtime compartido y bloquea Maven si su target está
montado por un contenedor activo. Requiere PostgreSQL aislado `ess-cert-postgres` en loopback:15440, disponible al
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
Esta baseline no evaluó OPEN/CLOSE/reopen. Outbox propio de ESS, ordenamiento
genérico de resultados antiguos, reconciliación, API tenant-scoped y publicación
Kafka de cuarentena siguen pendientes en `docs/event-state-service/gaps.md`. No se apagaron las dependencias
compartidas para probar fallos: esas fronteras se validaron mediante pruebas
controladas y PostgreSQL/JTA aislado.
