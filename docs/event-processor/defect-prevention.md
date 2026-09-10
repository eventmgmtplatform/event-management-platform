# Defect Prevention — Event Processor v1.0.0

Prioridad acordada el 2026-09-09: lógica de negocio antes de identidad y seguridad.
Este registro no convierte capacidades pendientes en PASS.

| ID | Pendiente | Criterio de cierre |
|---|---|---|
| DP-EP-01 | Keycloak/OIDC, issuer, audiencia, validación de tokens y RBAC diferidos por solicitud expresa | Integración y pruebas de identidad/roles antes de declarar seguridad implementada. REST actual no autentica; X-Tenant-Id y X-Actor-Id son datos declarados por el cliente. |
| DP-EP-02 | Recurrencia de blackouts, tags/atributos y matchers especializados | Biblioteca de recurrencia probada, contrato versionado y pruebas DST; mientras tanto se rechazan al validar. |
| DP-EP-03 | Fuentes externas de inventory/CMDB/Search y escalamiento | Enrichment e inventario local versionado implementados con procedencia/conflictos/criticalidad. Pendientes sincronización externa, timeouts de adaptadores y catálogos mayores al límite inicial de 256 configuraciones activas. |
| DP-EP-04 | Estrategias/relaciones adicionales y DA-06 | ATTRIBUTE/GROUP durable implementado. Pendientes PARENT_CHILD/RELATED, TEMPORAL/RULE_BASED/TOPOLOGICAL y deduplicación/lifecycle de eventos coordinada con Event State Service. |
| DP-EP-05 | Sincronización externa de mantenimiento | Registro local SUPPRESSION y vigencia/estado implementados; pendientes importación automática y observación de frescura externa. |
| DP-EP-06 | Comandos adicionales | CREATE_TICKET/default por grupo implementado y validado contra Worker. Pendientes GNM/CACF, cierres, actualizaciones, perfiles múltiples y ciclos sin correlación; no emitir con contratos incompletos. |
| DP-EP-07 | API especializada `/blackouts` y `/correlations` | Contratos administrativos dedicados. Este incremento administra todas las capacidades implementadas mediante el registro compartido `/rules`; IDs comparten namespace por tenant. |
| DP-EP-08 | Escalamiento y recuperación ampliada | Separar capacidad activa de historial del ciclo, pruebas de carga, backup/restore de relaciones+ledger con datos y replay de configuración/grupos más allá de límites iniciales. |

| DP-EP-09 | AIOps real y automatización | CRUD persistente y consumo HTTP de mock interno implementados. Pendientes adaptador certificado de Kyndryl Bridge, su autenticación, historial de evaluaciones y eventual incorporación al pipeline/contratos. Ver aiops-engine.md. |
| DP-EP-10 | Escritura desde interfaz por motor | Backend de blackouts probado; formulario y proxy administrativo aún pendientes. Ejecutar docs/event-processor/frontend-handoffs/blackouts.md en el chat de front y conservar evidencia real de navegador antes de declarar PASS de esa capa. Repetir el esquema de entrega para cada motor. |

DP-EP-03..09 registran límites o extensiones pendientes; no convierten el subconjunto implementado en certificación de todo v1.
DP-EP-01 queda fuera del incremento funcional por decisión del usuario.
