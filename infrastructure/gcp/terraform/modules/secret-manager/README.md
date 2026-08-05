# Secret Manager Terraform Module

Módulo reusable para administrar contenedores de secretos en Google Secret Manager.

## Incluye

- `google_secret_manager_secret`
- replicación automática o administrada por el usuario;
- labels;
- IAM no autoritativo por secreto;
- outputs de metadatos.

## No incluye

- payloads;
- `google_secret_manager_secret_version`;
- contraseñas, tokens o claves privadas;
- rotación automática;
- credenciales permanentes.

Los valores deberán cargarse mediante un procedimiento operacional separado,
autenticado y auditable.
