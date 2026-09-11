# Validación 2026-09-10

- OpenWebUI v0.10.2: conexión privada registrada y verificada desde el backend.
- Servicio `eventmanagement-e2e-tool.service` desplegado en interfaz Docker privada.
- Preset `validacion-e2e`, nombre «Validación E2E», qwen3:1.7b, Native, num_ctx 16384,
  herramienta `server:validacion-e2e`, builtin_tools desactivado.
- Mensaje probado literalmente: «ejecuta la prueba de validación E2E».
- Llamada real `ejecutar_validacion_e2e` desde el preset: PASS, exitCode 0.
- Consulta posterior con `consultar_validacion_e2e`: PASS y los mismos runId,
  runtime y reportPath; no inició otra ejecución.
- Chat de verificación: http://localhost:3000/c/01b03a8d-1bec-459a-893e-dc1fe287f6f2
- runId del puente: `0dde88e680ad4ea9ad5c4748df18eaf6`.
- Reporte: `evidences/testing/20260910T191404613373Z-94e72eca/happy-path/report.json`.
- Runtime del reporte: **shared**. Tenant sintético y proveedores mock sobre
  servicios compartidos; no certifica proveedores reales ni UI de navegador.
- Hashes SHA256 verificados para los 16 archivos de evidencia.
- Tres pruebas del puente aprobadas: autenticación y rechazo de comandos,
  identidad/resultado del reporte, concurrencia y recuperación tras interrupción.

El runner cambió su destino por defecto desde la preparación inicial de este
puente. Se fijó `--runtime shared` y se corrigieron las descripciones. No interpretar
las menciones antiguas a OS_11 en chats previos como evidencia del entorno actual.
La primera respuesta del preset resumió mal duración y outboxes: se corrigió la
instrucción para mostrar campos del reporte sin calcular duraciones. El reporte
es la fuente de evidencia; el texto generado por el modelo es una presentación.
