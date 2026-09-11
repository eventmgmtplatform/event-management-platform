# Correlación mínima — aceptación frontend 2026-09-10

Desplegada en http://localhost:8090/correlation mediante el contenedor compartido de consola; 8091 conserva los dashboards. Compilación TypeScript/Vite y pruebas de los tres módulos aprobadas.

## Implementación

- Formulario ATTRIBUTE/GROUP, activeOnly=true, AST resource.node EXISTS/EQ, campos String permitidos, límites de ventana/capacidad. Las estrategias avanzadas no se ofrecen; el guard del modelo se verifica mediante prueba unitaria.
- Registro mixto paginado hasta next=null; carga de definiciones con máximo cuatro peticiones concurrentes. Progreso y errores parciales explícitos.
- Cliente y proxy de Blackouts, ETag/If-Match, Idempotency-Key persistida para reintentos inciertos, conflicto 409 sin sobrescritura. Guardar no activa; activar, desactivar y retirar conservan historia.
- Secuencias de 1..64 eventos. La candidata sustituye el snapshot, el estado inicia vacío por petición. Se muestran todas las decisiones, incluyendo SCOPE_NO_MATCH, grupo nulo, miembros, ciclo y resolución. Consulta real de explain por processingId mediante proxy fijo.
- Traducciones ES/EN y estilos de los temas existentes.

## Aceptación interactiva de navegador

Tenant `correlation-ui-20260910-1205`, regla `by-node`.

1. Tenant requerido, ventana 0 y capacidad 33 rechazadas; alta válida con validación real del backend.
2. Doble clic de alta dejó una sola versión. Guardada sin activar: ninguna decisión activa.
3. Activación v1: GROUP_CREATED, MEMBER_ATTACHED, recuperación parcial sin resolver el grupo, recuperación total, NEW_CYCLE con groupId diferente.
4. Guardar v2 dejó v1 activa; segunda pestaña recibió HTTP 409 REVISION_CONFLICT con borrador conservado.
5. Activación v2 comprobada por regla/version en decisiones. Desactivación dejó de evaluar; retiro terminal bloqueó reactivación. Reapertura comprobó persistencia.
6. Paginación real con 50 registros INVENTORY deshabilitados en la primera página y Correlación en la segunda. Consola: 2 páginas, 51 definiciones, solo by-node en tabla.
7. Candidata EQ other-node mostró SCOPE_NO_MATCH, changed=false y group=null por evento.
8. Explain: HTTP 422 para ID inválido; HTTP 404 para ID válido inexistente. No se sustituyen respuestas por mocks.
9. Revisión visual ES/EN IBM Carbon y regresión de Blackouts: formulario conserva SCHEDULED/IMMEDIATE, sin selectores de SUPPRESSION.

## Evidencia y límites

`evidences/correlation/frontend-20260910/`: report.json, record.json, history.json, pagination-http.json, sequence-http-corroboration.json, fixture-retirement.json y capturas DOM. Las capturas visuales se emitieron en esta tarea; los archivos HTTP identifican explícitamente la corroboración separada de la interacción del navegador.

La regla quedó RETIRED en revisión 7. Los 50 registros de paginación se retiraron mediante API, sin SQL directo ni reglas de prueba activas. Las simulaciones no certifican procesamiento Gateway/outbox ni grupos de producción. No se agregó seguridad, proveedor ni editor de grupos persistidos.

Archivos de implementación: `src/modules/correlation/*`, router, navigation.registry, Header, en.json, nginx.conf y `testing/services/event-management-console/correlation.test.mjs`.
