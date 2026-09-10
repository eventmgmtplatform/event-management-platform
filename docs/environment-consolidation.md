# Consolidación de ambientes — 2026-09-10

Un único runtime operativo del producto: proyecto Compose `event-management`.
Open WebUI es una aplicación independiente y se conserva. Consola/dashboard
legacy que pertenecen al runtime principal no se retiraron; su integración visual
se trabaja por separado. No se eliminaron contenedores, volúmenes ni imágenes.

## Recuperación conservada

Evidencia privada: `evidences/environment-consolidation/20260910T153247676978Z/`.
El directorio tiene permisos 0700 y está excluido de Git. Puede contener credenciales,
datos sintéticos y configuración; no adjuntar el archivo privado a una PR.

- 21 contenedores seleccionados, de OS_11, CACF, dos candidatos Kafka y ESS test DB.
- 3 bases exportadas: lifecycle, cacf_test y ess_test.
- Cada dump se restauró en una base temporal del mismo servidor PostgreSQL y se
  compararon tablas/conteos. Se eliminó únicamente esa base temporal de verificación.
- 22 volúmenes archivados con lectura del archivo comprimido comprobada.
- Bind mounts archivados, incluidos binarios quarkus-app/configuración del laboratorio.
- Contratos, escenarios y journals de mocks que seguían activos exportados por HTTP.
- `containers.private.json` conserva imágenes, configuración y mounts originales.
- `report.json` relaciona bases, volúmenes y fuentes con cada archivo; SHA256SUMS
  permite verificar integridad. No se certificó arrancar Kafka/OpenSearch desde
  volúmenes restaurados; sus originales permanecen conservados para recuperación.

Los consumidores de laboratorio se detuvieron antes de exportar bases. Después
se detuvieron las dependencias y se archivaron los volúmenes sin escritores activos.
Las bases sintéticas y offsets del laboratorio NO se mezclaron con los datos ni
grupo consumidor del runtime principal. Configuración, pruebas y mocks necesarios
para el flujo de negocio ya fueron incorporados y certificados en el principal.

## Operación habitual

```bash
bash scripts/emctl validate
bash scripts/emctl services
bash scripts/emctl health
python3 testing/run.py happy-path
python3 testing/run.py happy-path --restart
python3 testing/run.py blackout
python3 testing/certifications/event-state-certification.py
```

`EVENTMANAGEMENT_RUNTIME` predetermina local; ecosystem es un alias de local.
No vuelve a levantar CACF aislado. `happy-path` predetermina shared. Las entradas
lifecycle-restart y eventmanagement-test apuntan al entorno principal; lifecycle-prepare
verifica el entorno existente sin recompilar o recrear otro stack.

`cacf-remediated` reutiliza UC-001 compartido, que incluye éxito/duplicados CACF.
La suite histórica específica de fallos CACF se conserva en `testing/legacy/` y
está marcada como archivada en el catálogo. No se afirma que UC-001 cubra todos
sus casos de timeout/fallo. Para recuperarla se requiere habilitar explícitamente
su laboratorio. Los fixtures originales permanecen bajo testing/fixtures y mocks.

## Pruebas SQL aisladas bajo demanda

Las pruebas unitarias que no requieren PostgreSQL siguen en `testing/run.py unit`.
Las IT que exigen 15439/15440 conservan ese requisito; nunca redirigir sus CREATE/DROP
de bases a la base compartida. Para una sesión de IT se pueden iniciar únicamente
las bases de pruebas conservadas y detenerlas al finalizar:

```bash
docker start ess-cert-postgres cacf-certification-postgres-1
# Ejecutar las IT según testing/README.md, con sus URLs aisladas.
docker stop ess-cert-postgres cacf-certification-postgres-1
```

Son dependencias de prueba temporales, no otro ambiente de negocio permanente.
`emctl event-state-service test` incluye Java/IT y requiere esa preparación;
para salud repetible sin IT utilizar event-state-certification.py como arriba.

## Recuperación excepcional de un laboratorio

No forma parte del arranque habitual. Antes de recuperarlo, verificar SHA256SUMS
y los nombres/mounts de report.json; comprobar puertos libres. Los contenedores
están detenidos, por lo que `docker start` sobre los nombres exactos conserva sus
configuraciones y volúmenes. Arrancar dependencias primero y consumidores después.
Usar las imágenes y binds capturados; no ejecutar build con código distinto y
presentarlo como restauración del checkpoint original.

Si hay que restaurar desde los archivos, crear destinos NUEVOS para no sobrescribir
los originales. Restaurar dumps con pg_restore --exit-on-error --no-owner --no-privileges
y comparar los conteos registrados. Para volúmenes, extraer el archivo correspondiente
en un volumen nuevo y usar un override Compose explícito con la misma versión de
imagen; validar arranque/offsets antes de considerarlo recuperado. No conectar el
broker recuperado a productores del entorno compartido. Después de la investigación,
detener de nuevo el laboratorio. El contexto e instrucciones antiguas se conservaron
en testing/legacy y testing/environments; no son el camino operativo predeterminado.

## Frontend pendiente

La integración visual de ESS continuará en el chat del frontend. Prompt listo:
[ess-admin-integration-prompt.md](frontend/ess-admin-integration-prompt.md).

## Certificación posterior a la consolidación

PASS: UC-001 compartido con reinicio, blackout, API/ESS (9 checks sin repetir
reinicio), harness y CLI predeterminada (`Runtime=local`).
Resumen: `evidences/environment-consolidation/summary.json`. Los reportes de
intentos limitados por permisos se conservaron y no se presentan como éxitos.

La inspección posterior mostró únicamente event-management y la aplicación
independiente Open WebUI activos. Ninguna suite ejecutada levantó laboratorios.
