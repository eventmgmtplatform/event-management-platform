# Auto-suppression — cierre backend local

El motor, persistencia y administración versionada ya existen. La certificación de
este corte comprueba sus límites sin agregar otra tabla, API duplicada ni interfaz.
Se administra tipo SUPPRESSION por `/api/v1/rules`, y el catálogo de lectura está en
`/api/catalog/views/auto-suppression`. Configuración e historial viven en el registro
PostgreSQL del Processor. No hay sincronización con proveedores externos.

Regla habilitada + estado ACTIVE/APPROVED + scope y ventana coincidentes produce
SUPPRESS_INTEGRATIONS. CANCELLED/COMPLETED no son elegibles. Origen admite MANUAL,
MAINTENANCE, CHANGE; no altera por sí solo la decisión. La referencia externa se
conserva en auditoría. Ventana finita, inicio incluido, fin excluido; scope exacto
por tenant/recurso. Recuperación, correlación y auditoría continúan.

Crear una nueva versión no sustituye la activa. Una cancelación guardada necesita
activarse para reemplazar la versión anterior; alternativamente se puede deshabilitar
el registro inmediatamente. Idempotencia, revisión optimista y retiro terminal
mantienen los contratos de la API administrativa compartida.

## Reproducción de aceptación

`python3 testing/certifications/processor-auto-suppression-certification.py`

Requiere runtime local, Processor 8082, catálogo por proxy 8090, Gateway 8081 y
Docker/PostgreSQL/Kafka. Crea tenants/reglas/eventos sintéticos, verifica CRUD versionado,
estados, ventanas, catálogo y el recorrido real. El escenario compartido de mantenimiento
`testing/e2e/blackout.py` admite capability=SUPPRESSION y conserva BLACKOUT como default.
Las etiquetas históricas del escenario dicen blackout; capability y etapa auditada
identifican inequívocamente AutoSuppression en esta ejecución.

Prueba que una ruta apta generaría un comando sin supresión, que el evento real queda
suprimido, que recovery continúa, que el recurso fuera del scope no coincide y que
la desactivación deja de suprimir. Comprueba salida normalizada publicada, explain
igual a auditoría persistida y ausencia de comandos. Limpieza desactiva reglas propias;
retiene versiones y eventos como evidencia bajo `evidences/auto-suppression/`.

DP-EP-05 mantiene pendiente importación y frescura externa; DP-EP-01 mantiene seguridad
diferida. La [entrega de frontend](frontend-handoffs/auto-suppression.md) queda pendiente
de ejecutar en su chat; pruebas de backend no certifican escrituras desde navegador.
