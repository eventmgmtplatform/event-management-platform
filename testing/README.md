# Unidad de testing

Actualización de consolidación: sólo el runtime principal queda operativo.
Las instrucciones históricas de laboratorios siguientes requieren recuperación
explícita; véase [consolidación](../docs/environment-consolidation.md).
`happy-path` usa shared por defecto y `emctl` ya no arranca CACF aislado.


Ejecución desde chat: [procedimiento OpenWebUI y API de validación E2E](../services/e2e-tool-api/README.md).
El puente invoca exclusivamente `happy-path` y devuelve el estado y la evidencia del runner.

Base única para código de prueba y casos de uso de Event Management. Las especificaciones
expresan lo esperado; el catálogo expresa cobertura implementada, **no resultados de ejecución**.
El happy path completo usa el laboratorio aislado OS_11 y comprueba confirmaciones reales de los mocks.

## Ejecución desde la raíz del repositorio

```bash
python3 testing/run.py list
python3 testing/run.py unit
python3 testing/run.py console
python3 testing/run.py dashboards
python3 testing/run.py dashboards-integration
python3 testing/run.py harness
python3 testing/run.py unit --service event-processor
python3 testing/run.py blackout
python3 testing/run.py happy-path
python3 testing/run.py cacf-remediated
python3 testing/run.py certification --name processor-rest-certification
```

Python 3 (biblioteca estándar), Java 21 y Maven 3.9 son necesarios. `unit` limpia compilados previos y usa Maven
offline: se requiere el caché de dependencias del proyecto. Si falta, preparar primero
las dependencias de cada servicio con `mvn -f services/<servicio>/pom.xml dependency:go-offline`.
Las pruebas Maven siguen ejecutándose desde el servicio, con fuentes/recursos en
`testing/services/<servicio>` declarados explícitamente en su POM. Los Dockerfiles
siguen construyendo únicamente producción; la suite se ejecuta en el checkout completo.

`blackout` requiere el runtime local levantado, Gateway :8081, Processor :8082,
PostgreSQL/Kafka y migraciones 009–015. Crea un tenant y reglas sintéticos únicos,
publica eventos y conserva auditoría; desactiva sus reglas en `finally`. No reinicia
servicios. No usa fechas fijas para el blackout.

`cacf-remediated` requiere el ambiente aislado:

```bash
docker compose -f testing/environments/cacf.compose.yml up -d --build
python3 testing/run.py cacf-remediated
```

La certificación CACF histórica además reinicia su Worker. La certificación de ciclo
`eventmanagement-test` detiene/arranca ambos runtimes: ejecutarla en una ventana de
laboratorio. Las certificaciones históricas conservan sus alcances y precondiciones;
no son equivalentes al happy path.

## Organización

Kafka: `python3 -m unittest discover -s testing/harness -p test_kafka_admin.py -v`
verifica el instalador sin cambiar el broker. Requiere Docker Compose CLI para
validar/paridad de configuración, no acceso al daemon. El test de paridad usa
el `.env` local del checkout. La certificación real, sólo sobre paquete aislado:
`python3 testing/certifications/kafka-certification.py --directory <paquete> --restart`.
Preparación, alcance y conservación de datos en [manual Kafka](../docs/kafka/README.md).

`dashboards` verifica DTO frontend y contratos/CLI del BFF (Node y Python).
`dashboards-integration` requiere Docker y psycopg; crea PostgreSQL 17 aislado,
verifica cuatro vistas y paridad API/PostgreSQL y elimina su propio contenedor/volumen.
No modifica PostgreSQL de plataforma. Ver [runbook OEM](../docs/dashboards/operational-runbook.md).

| Directorio | Responsabilidad |
|---|---|
| `cases/` | Catálogo, criterios de aceptación y plantilla de casos |
| `e2e/` | Escenarios por fronteras reales, polling acotado y limpieza |
| `services/<servicio>/java` | Suites JUnit de unidad, contratos e integración |
| `services/<servicio>/resources` | Fixtures específicos del servicio |
| `fixtures/events/` | Entradas Gateway compartidas; duplicados antiguos consolidados |
| `mocks/` | WireMock ServiceNow, GNM, NEXT y AIOps |
| `environments/` | Compose de certificación CACF |
| `certifications/` | Scripts funcionales y de recuperación existentes |
| `legacy/console/` | Certificaciones históricas de consola, opt-in |
| `terraform/project-common/` | Pruebas plan de convenciones Terraform |
| `harness/` | Pruebas del runner y consistencia de la base |

La suite `console` requiere Node y dependencias npm instaladas en el servicio;
cubre validación y lectura de snapshots administrativos. Su ruta anterior es un
enlace de compatibilidad.

Los enlaces en `scripts/*certification.py` y `scripts/eventmanagement-test.py`
son compatibilidad con comandos operativos anteriores, sin duplicar código.
El paquete histórico `SN-02.9E-owner-guarded-package` permanece como snapshot de
recuperación, no como fuente de las suites actuales.
Las herramientas de despliegue, reparación, publicación Git y diagnóstico siguen
en sus directorios operativos. Los contratos de producción siguen en `src/main/resources`.

## Integración y pruebas omitidas

`unit` descubre `*Test`; clases `*IT` no se incluyen. Algunas `*Test` con base de datos
usan assumptions y se omiten sin URL aislada. El reporte muestra conteos y
`PASS_WITH_SKIPS`; esto **no certifica integración**. Para integración del Processor:

```bash
cd services/event-processor
mvn -B -ntp test '-Dtest=*Test,*IT' \
  -Dprocessor.test.jdbc.url=jdbc:postgresql://127.0.0.1:15439/cacf_test
```

Requiere PostgreSQL del Compose CACF. ESS usa su servidor aislado :15440/ess_test;
consultar [validación ESS](../docs/event-state-service/validation.md). Nunca sustituir
URLs de prueba por PostgreSQL productivo. Los tests crean y eliminan bases aisladas.

Terraform mantiene su módulo productivo; ejecutar con `terraform -chdir=infrastructure/terraform/modules/common/project-common test -test-directory=../../../../../testing/terraform/project-common`.
No hay suite automatizada de navegador en la consola: scripts históricos inspeccionan
scaffold/health, no reemplazan pruebas de interacción. Queda como cobertura por agregar.

## Evidencias

Cada ejecución del runner crea `evidences/testing/<UTC>-<uuid>/<suite>/` con
`report.json`, logs/snapshots y `SHA256SUMS`. Códigos: 0 aprobado (consultar omisiones),
1 fallo, 2 bloqueo funcional. El reporte se escribe también ante excepciones.
No guardar resultados en `testing/`, `docs/`, código de servicios ni fixtures.
Maven directo escribe en `evidences/testing/maven/<servicio>` y puede sobrescribir
una ejecución anterior; preferir el runner, que genera directorios únicos.
`evidences/` está ignorado por Git. `evidence/` conserva el histórico existente;
no se renombra ni se vuelve a versionar ese histórico.

No subir credenciales, dumps de entorno ni respuestas de proveedores reales.
Los fixtures son sintéticos; un snapshot nuevo debe contener sólo datos del caso.
Los mocks GNM incluyen escenarios históricos de reconciliación: no afirmar happy
path sin seleccionar explícitamente el escenario esperado y comprobar su journal.

## Agregar un caso

1. Copiar [la plantilla](cases/TEMPLATE.md) con un ID estable y alcance explícito.
2. Registrar el caso en [catalog.json](cases/catalog.json), inicialmente `planned`.
3. Reusar fixtures y helpers; definir identidades únicas, polling con deadline,
   aserciones positivas/negativas y limpieza de recursos propios.
4. Agregar ejecución al runner y pruebas de sus aserciones; cambiar a `automated`
   sólo cuando exista implementación. Mantener bloqueos visibles.
5. Ejecutar y conservar resultados únicamente en `evidences/`.

[Changelog](CHANGELOG.md) · [Happy path](cases/UC-001-happy-path.md) · [Blackout](cases/UC-002-blackout.md)

## Orquestación OS_11

```bash
python3 testing/run.py certification --name lifecycle-prepare
python3 testing/run.py happy-path
python3 testing/run.py certification --name lifecycle-restart
```

La preparación usa imágenes locales, compila los cuatro servicios y conserva
volúmenes/offsets del proyecto `os11-lifecycle`. No opera sobre el Compose compartido.
El tenant `os11-synthetic` está preconfigurado únicamente en ese Worker; nodo, reglas,
ticket, incidentId y executionId cambian en cada ejecución. No resetear journals.
Mappings propios se conservan para reconciliación tardía; reglas se desactivan en
finally. Los reportes sólo contienen identidades sintéticas y se guardan en evidences.
No ejecutar `unit` (hace clean) mientras ese laboratorio monte target/quarkus-app.

Regresiones SQL (servidores dedicados indicados en los comandos):

```bash
mkdir -p evidences/os11
mvn -B -ntp -o -f services/event-processor/pom.xml test \
  '-Dtest=*Test,AdminApiIT,AiopsApiIT' \
  -Dprocessor.test.jdbc.url=jdbc:postgresql://127.0.0.1:15439/cacf_test \
  -Dtesting.reportsDirectory="$PWD/evidences/os11/processor" > evidences/os11/processor.log 2>&1
mvn -B -ntp -o -f services/integration-worker/pom.xml test '-Dtest=*Test' \
  -Dcacf.test.jdbc.url=jdbc:postgresql://127.0.0.1:15439/cacf_test \
  -Dtesting.reportsDirectory="$PWD/evidences/os11/worker" > evidences/os11/worker.log 2>&1
mvn -B -ntp -o -f services/event-state-service/pom.xml test '-Dtest=*Test,*IT' \
  -Dess.test.jdbc.url=jdbc:postgresql://127.0.0.1:15440/ess_test \
  -Dtesting.reportsDirectory="$PWD/evidences/os11/ess" > evidences/os11/ess.log 2>&1
```

RestoreReplayIT necesita el procedimiento histórico de backup/restauración y no
forma parte del comando general anterior. No confundir omisiones con cobertura.
[Runbook, secuencia, migraciones y límites](../docs/event-processor/lifecycle-orchestration.md).

## UC-001 en runtime compartido

Tras la activación descrita en `docs/cacf/shared-activation.md`:

```bash
python3 testing/run.py happy-path --runtime shared --restart
```

Reutiliza el mismo escenario OS_11, con endpoints del Compose principal. Verifica
configuración de mocks antes de provisionar reglas. Reinicia los tres consumidores
en NEXT SUBMITTED; no detiene proveedores ni modifica el laboratorio. El valor
predeterminado de `happy-path` es ahora `--runtime shared`; OS_11 requiere recuperación explícita.
