# Sistema — entrega incremental 2026-09-10

## Bloque 1 terminado y desplegado

- Sistema ordenado: Administración, Ticketing, Notifications, Automations, AIOps Extensions, Cliente, Secrets.
- AIOps y Cliente conservan sus pantallas/rutas; Automations conserva /automations.
- Dashboard general deriva Sistema/Plataforma del registro de navegación para evitar divergencias. Conservados cambios de GLPI de la tarea paralela.
- Secrets /system/secrets conectado a MockSecrets local persistente; alta, listado de metadatos, reemplazo con revisión; valor nunca devuelto. Backend en services/mock-secrets y Compose independiente.
- Tests HTTP y build correctos; aceptación de formulario en navegador correcta. No se provisionaron recursos cloud, no se modificaron Worker, Processor o base GLPI.

## Siguiente bloque autorizado, aún NO implementado

Ticketing/Notifications son entradas preparadas explícitamente como Próximamente. Automations sigue siendo la pantalla preparada. Faltan formularios de instancias ServiceNow/GLPI, GNM, CACF con combobox de referencias de MockSecrets, JSON opcional y persistencia de configuración en PostgreSQL. No se publican como funcionales. Posteriormente conectar composición del enriquecimiento/resolución de instancias a consumidores y validar integración sin cambiar semántica de comandos existentes.

Contrato de metadata disponible para futuro selector: GET /api/mock-secrets?tenant=..., con id, tenant, environment, name, kind, revision, enabled y updated_at. El selector debe filtrar tipo/entorno y guardar referencia; nunca recuperar el valor en navegador. Antes de un resolver de Worker, definir su frontera de acceso y política de revisiones. MockSecrets actual conserva solo el último valor.

Motivo del corte: consulta inicial reportó 98% consumido en cupo principal compartido, aproximadamente 2% restante y otra tarea activa de GLPI. Se cerró un incremento probado sin iniciar formularios a medias. El porcentaje no permite estimar tokens exactos ni garantizar que toda la solución cabe.
