# Changelog

## 2026-09-10

- Servicio de usuario persistente, token privado y conexión OpenAPI desde la red
  Docker de OpenWebUI. Instalación reproducible con `scripts/e2e-tool-deploy.py`.
- Preset privado «Validación E2E», basado en qwen3:1.7b, con herramienta seleccionada,
  modo Native y contexto 16384. El registro del servidor por sí solo no habilitaba
  la herramienta en el chat.
- Inicio y consulta esperan hasta 20 segundos para devolver resultados completos
  cuando la prueba termina dentro de ese plazo.
- Runtime shared fijado explícitamente: el runner fue migrado desde OS_11; las
  descripciones ahora reflejan tenant sintético y mocks sobre servicios compartidos.
- Verificación de autenticación, rechazo de comandos, reportes, concurrencia,
  espera y bloqueo tras interrupción: tres tests aprobados.
