# CACF/GNM en el entorno compartido

Alcance: proveedores locales simulados. No conecta NEXT ni Everbridge reales.
El laboratorio OS_11 conserva sus contenedores, volúmenes y evidencia.

## Configuración

El Compose principal configura CACF en integration-worker, NEXT en
`http://next-mock:8080` (host loopback 8184), GNM en `http://gnm-mock:8080` y
ServiceNow en `http://servicenow-mock:8080`. Las credenciales sintéticas son de
fixture local. No emplearlas para proveedores reales ni exposición pública.
El registry `testing/fixtures/lifecycle/shared-gnm-registry.json` conserva los
mappings existentes y agrega `os11-synthetic` para la certificación.

`emctl` administra NEXT en el orden de dependencias del runtime principal.
La activación vive en Compose base, por lo que los reinicios/recreaciones de
Worker desde CLI conservan CACF/GNM. El overlay histórico docker-compose.cacf.yml
no se necesita para esta activación: su puerto NEXT 8183 coincide con AIOps.

## Activar y verificar

```bash
python3 scripts/cacf-gnm-activate.py
python3 testing/run.py happy-path --runtime shared --restart
bash scripts/emctl next-mock health
```

El activador respalda event_management/event_processor, aplica 008 de forma
aditiva, inicia NEXT, recrea únicamente Worker y verifica readiness. Ejecuta
UC-001 mediante Gateway y callbacks; SQL sólo observa. El checkpoint reinicia
Processor, Worker y ESS después de NEXT SUBMITTED antes de enviar ACK.
Comprueba ticket, GNM confirmado, CACF/NEXT, recuperación, GNM cerrado, ServiceNow
resuelto, ESS/OpenSearch, duplicados sin efectos adicionales y outboxes vacías.

La certificación sólo crea reglas y mappings propios con IDs únicos. Deshabilita
las reglas al terminar y conserva mappings para reconciliación tardía. No elimina
eventos, ejecuciones, offsets ni volúmenes. Las rutas lifecycle siguen siendo
opt-in: cada ruta funcional debe definir grupos y política de resolución.

Si falla, el activador restaura la configuración previa de Worker; conserva NEXT
y las migraciones aditivas. La evidencia local incluye backup, configuración de
rollback con permisos restringidos, logs y report.json; no se publica en Git.

## Orden del trabajo acordado

1. Activación CACF/GNM compartida y certificación con reinicio.
2. API administrativa ESS: consulta por tenant, historial y diagnóstico, contrato
   de autorización/auditoría e integración en consola/pruebas.
3. Consolidar ambientes: inventariar y respaldar laboratorios, recuperar fixtures,
   configuración y evidencia, apuntar suites al entorno único y retirar duplicados
   sólo después de validar recuperación. No se ha ejecutado esta retirada.

## Resultado — 2026-09-10

**PASS** en el runtime compartido con mocks y reinicio de Processor/Worker/ESS
en NEXT SUBMITTED. Se confirmaron cierre GNM, ticket RESOLVED, proyección ESS,
duplicados sin mutaciones adicionales y outboxes vacías.

- Activación: `evidences/cacf-gnm-activation/20260910T151537997686Z/report.json`.
- UC-001: `evidences/testing/20260910T151614580900Z-e2b6d381/happy-path/report.json`.
- Harness: `evidences/testing/20260910T151402251448Z-1aa67d8e/harness/report.json`.

Las reglas sintéticas quedaron deshabilitadas al finalizar. La infraestructura
CACF/GNM queda activa; habilitar rutas de negocio adicionales requiere su perfil
explícito. Los laboratorios no se han eliminado ni consolidado.
