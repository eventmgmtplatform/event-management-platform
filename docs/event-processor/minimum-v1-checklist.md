# Revisión de cierre — v1.0.0 mínima local

Alcance de trabajo: lógica de negocio local, sin seguridad, con Defect Prevention activo.
Este corte mínimo no equivale a cerrar todos los DA ni a certificar producción.

| Capacidad | Disponible | Límite actual |
|---|---|---|
| Contratos y reglas | Gateway 1.0/1.1, DSL tipada, snapshots/configuración versionada, CRUD administrativo y simulación | APIs especializadas en DP; registro compartido `/rules` |
| Enrichment/inventory | Catálogo local, planes, hechos tipados, procedencia y conflictos | Proveedores externos diferidos |
| Blackout/auto-suppression | Vigencia finita, scope, estado y directivas | Recurrencia/importación externa diferidas |
| Correlación | ATTRIBUTE/GROUP, miembros/ciclos, concurrencia y persistencia | Otras estrategias y escala diferidas |
| Policy/routing/comandos | CREATE_TICKET/default por grupo; ledger y outbox atómicos | Cierres/actualizaciones y otros proveedores diferidos |
| AIOps | CRUD persistente y consumo REST de mock mediante puerto propio | Consulta manual, protocolo interno; Bridge real y pipeline diferidos |
| Operación | Health, replay, reinicio, DLQ, auditoría y despliegue local | Recuperación ampliada y aceptación final pendientes |

## Trabajo necesario antes de declarar el corte mínimo cerrado

1. **Cerrar la decisión funcional de DA-06.** Verificar identidad, duplicados de negocio,
   occurrences y recovery con la autoridad de Event State Service. La deduplicación de
   transporte y de comandos ya existe; no demuestra por sí sola el lifecycle completo.
   Implementar únicamente la brecha necesaria, o fijar explícitamente el comportamiento
   admitido en el corte mínimo y sus pruebas.
2. **Aceptar el recorrido integrado de negocio.** Ejecutar fixtures sintéticos con reglas
   activas en un ambiente aislado: enrichment/inventory → suppression/blackout → correlation
   → policy/routing → outbox → Worker → mock. Comprobar creación única, bloqueo, recovery y
   nuevo ciclo. Hay pruebas unitarias/transaccionales y REST, pero no declarar por ellas
   certificado ese recorrido completo. Incluir CRUD/consulta/reinicio de AIOps como flujo
   independiente; no inventar su activación automática.
3. **Verificar recuperación de los nuevos datos.** Backup/restore con relaciones,
   ledger de comandos y configuración/auditoría AIOps poblados, seguido de replay. Conservar
   evidencia de ausencia de duplicados y retención de configuración. El respaldo del
   despliegue existe; la restauración ampliada con estos datos sigue pendiente.
4. **Cerrar matriz de aceptación y evidencia del corte.** Vincular los casos anteriores
   a resultados reales y revisar los escenarios funcionales de referencia seleccionados.
   Publicar estado PASS/FAIL/PENDING y Defect Prevention restante. No afirmar paridad
   histórica total, RPO/RTO o certificación integral sin ejecución.

## Fuera de este corte por alcance mínimo

Seguridad/Keycloak/OIDC/RBAC; proveedores reales (incluido Bridge); algoritmos de
correlación adicionales; recurrencias; otros comandos; APIs administrativas especializadas;
escalamiento y observabilidad avanzada. Mantener [Defect Prevention](defect-prevention.md)
con criterios de cierre. Estos pendientes no deben disparar desarrollo adicional para
el esqueleto AIOps solicitado.
