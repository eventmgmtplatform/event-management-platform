# OS_01_01 — Mapeo de campos Zabbix

| Zabbix | Evento normalizado v1.1 |
|---|---|
| `CustomerCode` | `tenant.code` |
| `source` | `source.system` |
| `ClassName` | `source.className` |
| `origin` | `source.originAddress` |
| `Node` | `resource.name` |
| `NodeAlias` | `resource.address` |
| `hostname` | `resource.hostname` |
| `Component` | `resource.component` |
| `SubComponent` | `resource.subcomponents[]` |
| `ApplId` | `resource.applicationId` |
| `AlertKey` | `condition.alertKey` |
| `InstanceSituation` | `condition.situation` |
| `InstanceId` | `condition.instanceId` |
| `InstanceValue` | `condition.instanceValue` |
| `msg` | `summary` |
| `severity` | `sourceSeverity` |
| `Type` | `sourceType` |
| `ExpireTime` | `expirationSeconds` |

Campos derivados: `eventId` (UUID), `eventKey`, `legacyEventKey`, `effectiveSeverity`, `lifecycleAction`, `timestamps.receivedAt` y `timestamps.lastUpdatedAt`.

No se derivan todavía cliente formal, SDC, ubicación, ambiente, CI, servicio, grupo resolutor, prioridad, políticas de deduplicación/correlación, blackout, ticketing, automatización o notificación. Estos valores requieren inventario y reglas explícitas.
