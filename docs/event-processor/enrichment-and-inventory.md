# Enrichment e inventario local v1

Enrichment consulta `InventoryPort` y agrega un `EnrichmentResult` inmutable al
contexto. El evento original y su identidad permanecen intactos. La implementación
local `SnapshotInventory` utiliza registros de referencia PostgreSQL capturados
en el mismo SELECT que planes, políticas y blackouts. No consulta proveedores.

Los registros INVENTORY tienen versiones, activación, revisión optimista y
recibos idempotentes mediante `/api/v1/rules`. Su schema separado es
`inventory-record-v1.schema.json`; no se altera el schema DA-04. El namespace de
IDs y el límite de 256 configuraciones activas por tenant son compartidos. Este
inventario local es un subconjunto de referencia para decisiones, no un nuevo
servicio CMDB ni una autoridad de lifecycle.

Ejemplo de registro:

```json
{"id":"ci-router-1","version":1,"type":"INVENTORY","enabled":true,
 "priority":10,"scope":{"node":"router-1"},
 "facts":{"resource.ciId":"ci-001","assignment.group":"network",
          "resource.managed":true,"service.criticality":3},
 "metadata":{"owner":"operations"}}
```

Scope usa igualdad exacta para customerCode/node/nodeAlias/component/instanceId/
monitoringSolution, con el mismo mapeo Gateway documentado para blackouts. Exige
al menos un selector de recurso; no admite inventario global por defecto. Un
customerCode explícito debe coincidir con el tenant del registro.

Plan ENRICHMENT sobre el schema DA-04:

```json
{"id":"inventory-lookup","version":1,"type":"ENRICHMENT","enabled":true,
 "priority":10,"condition":{"field":"resource.node","operator":"EXISTS"},
 "actions":[{"type":"LOOKUP_INVENTORY","parameters":{"required":true}}],
 "metadata":{"owner":"operations"}}
```

Cada plan contiene una consulta tipada. La condición no puede depender de hechos
de enrichment, evitando dependencias circulares o resultados dependientes del
orden de planes. Los planes aplicables comparten una consulta al snapshot local.
Planes no aplicables producen SKIPPED sin consultar inventory.

## Hechos y precedencia

| Hecho | Tipo |
|---|---|
| resource.ciId | String |
| service.name | String |
| assignment.group | String |
| location.site | String |
| resource.class | String |
| resource.managed | Boolean |
| service.criticality | Decimal |

Policy los consulta con prefijo `enrichment.`, por ejemplo
`enrichment.assignment.group EQ "network"`. `policy-fields-v2` agrega estos
campos y resource.node/resource.component al contrato anterior. Se mantienen las
comparaciones estrictas: ausencia no equivale a falso/cero/texto vacío; para
booleanos se permiten igualdad, pertenencia y existencia, no comparación ordenada.

Fuentes coincidentes se ordenan por prioridad descendente, ID y versión
ascendentes. El primer valor gana por campo y conserva fuente, versión, checksum
e instante observado. Valores distintos generan conflictos con referencias a
ambas fuentes y la política de resolución; no hay sobrescritura silenciosa.
Cada campo y valor es validado contra el vocabulario tipado.

## Resultados y entrega

- FOUND produce hechos y procedencia.
- NOT_FOUND opcional conserva ausencia válida sin defaults.
- NOT_FOUND requerido produce FAILED/DEAD_LETTER.
- ERROR opcional produce PARTIAL/degraded; ERROR requerido produce FAILED.
- Sin planes aplicables el resultado es NOT_FOUND, sin afirmar que un CI existe.

La configuración PostgreSQL inaccesible impide establecer el snapshot: se mantiene
el comportamiento durable de reintento, no se procesa contra configuración vacía.
Errores de fuente a través del puerto se representan con códigos controlados.
Los adaptadores externos futuros deberán tener timeout y límites propios; el
adaptador local no hace IO durante la evaluación y recorre como máximo 256 entradas.

El resultado sigue el schema DA-05 cerrado y se conserva en evidencia y
`processing.enrichment.result`. La envoltura existente mantiene status, engine y
processedAt. Una DLQ originada por enrichment identifica ContextEnrichment,
conserva diagnóstico tipado y ejecuta ProcessingAudit. Kafka se confirma después
de persistir evidencia y salida.

## Simulación

`candidateRules` permite evaluar un conjunto de 1..256 definiciones de inventario,
planes, blackouts y políticas, sin activarlas. Es excluyente con `candidateRule`.
El conjunto sustituye el snapshot activo durante esa simulación. Las versiones
candidatas se evalúan aunque tengan enabled=false, sin modificar el registro.
`evaluatedAt` fija el reloj; en su ausencia se usa receivedAt.

Persistencia, simulación, esquema de resultado, precedencia, ausencia/error,
consumo por policy y entrega normalizada/DLQ cuentan con pruebas. Inventory remoto,
CMDB, SearchPort, sincronización y catálogos de mayor capacidad siguen pendientes.
