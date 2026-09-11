# MockSecrets local — 0.1.0

Bóveda sintética para desarrollar Sistema/Secrets. No usa Google Secret Manager ni servicios cloud. Almacena valores cifrados con Fernet en SQLite, volumen `mock-secrets-data`; la clave aleatoria reside en un volumen separado `mock-secrets-keys`. Ambos deben conservarse para recuperar el mock. No es un almacén de credenciales reales: comparte la frontera local de la consola sin identidad/RBAC multiusuario. El cifrado protege el contenido persistido, pero los dos volúmenes en el mismo host no son una frontera equivalente a un gestor de secretos.

Identidad compuesta UNIQUE(tenant, environment, name), referencia UUID y revisión optimista. Valor write-only por HTTP: GET retorna únicamente metadatos. POST crea; PUT reemplaza el valor actual e incrementa revisión, requiere If-Match. No se mantiene historial de valores ni endpoint público para resolver plaintext. No se promete rotación en Worker: el consumidor aún no está conectado.

API interna same-origin:
- GET /api/mock-secrets?tenant=...: items, truncated (límite 500), mode MOCK_ONLY.
- POST /api/mock-secrets: tenant, environment, name, kind password/token, value.
- PUT /api/mock-secrets/{UUID}: mismo cuerpo y If-Match de revisión actual.
- Escrituras requieren Content-Type application/json y X-Console-Action: mock-secrets. Esta cabecera es un control de intención de UI, no autenticación.

No imprime cuerpos o valores. POST duplicado devuelve 409; PUT obsoleto 409, falta de revisión 428. Ante respuesta incierta, refrescar y comparar metadatos antes de repetir; no reintento automático. Entorno local exclusivamente; no está certificado para datos de producción.

Despliegue (desde raíz):

```sh
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml -f infrastructure/docker-compose.mock-secrets.yml up -d --build --no-deps --wait mock-secrets event-management-console
```

Pruebas:

```sh
python3 -m unittest discover -s services/mock-secrets -p test_vault.py
```

Verificación 2026-09-10: prueba HTTP PASS (persistencia, cifrado, metadata sin valor, tenant, colisión y revisión); TypeScript/build PASS; ambos contenedores healthy. Navegador: alta sample-password para tenant mocksecrets-ui y reemplazo a revisión 2, formulario de contraseña vacío tras envío. Fixture contiene solo datos sintéticos y queda disponible para probar selectores futuros.
