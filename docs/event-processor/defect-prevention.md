# Defect Prevention — Event Processor v1.0.0

Prioridad acordada el 2026-09-09: lógica de negocio antes de identidad y seguridad.
Este registro no convierte capacidades pendientes en PASS.

| ID | Pendiente | Criterio de cierre |
|---|---|---|
| DP-EP-01 | Keycloak/OIDC, issuer, audiencia, validación de tokens y RBAC diferidos por solicitud expresa | Integración y pruebas de identidad/roles antes de declarar seguridad implementada. REST actual no autentica; X-Tenant-Id y X-Actor-Id son datos declarados por el cliente. |
| DP-EP-02 | Recurrencia de blackouts, tags/atributos y matchers especializados | Biblioteca de recurrencia probada, contrato versionado y pruebas DST; mientras tanto se rechazan al validar. |
| DP-EP-03 | Fuentes externas de inventory/CMDB/Search y escalamiento | Enrichment e inventario local versionado implementados con procedencia/conflictos/criticalidad. Pendientes sincronización externa, timeouts de adaptadores y catálogos mayores al límite inicial de 256 configuraciones activas. |
| DP-EP-04 | Correlación y deduplicación de lifecycle | Coordinar ownership con Event State Service; selección acotada, relaciones durables, concurrencia, recuperación y simulación de secuencias. |
| DP-EP-05 | Auto-suppression/importación de mantenimiento | Registro local versionado y vigencia de cambios; no consultar proveedores por cada evento. |
| DP-EP-06 | Routing y generación de comandos | Políticas por target/ciclo, outbox y compatibilidad Worker; no emitir acciones incompletas. |
| DP-EP-07 | API especializada `/blackouts` y `/correlations` | Contratos administrativos dedicados. Este incremento administra POLICY y blackout mediante el registro compartido `/rules`; IDs comparten namespace por tenant. |

DP-EP-03..06 son trabajo funcional pendiente del componente, no una exclusión de v1.
DP-EP-01 queda fuera del incremento funcional por decisión del usuario.
