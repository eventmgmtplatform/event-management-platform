# Cloud Build Terraform Module

Reusable module for the Event Management Cloud Build foundation.

## Responsibilities

- Dedicated build execution service account.
- Project-level runtime roles.
- Artifact Registry repository writer binding.
- Optional Secret Manager accessor bindings.
- Optional Cloud Build v2 GitHub connection.
- Optional repositories.
- Optional triggers.

## Security model

The execution identity is separate from `terraform-deployer`.

Default access:

- `roles/logging.logWriter` at project level.
- `roles/artifactregistry.writer` on one repository.

Secret access is opt-in and granted per secret.

The module does not create:

- Secret payloads.
- Secret versions.
- Service account keys.
- Private pools by default.
- Runtime deployment permissions.

## GitHub connection

Connection, repository and triggers remain disabled until all of these are known:

- Real GitHub repository HTTPS URI.
- GitHub App installation ID.
- Existing Secret Manager secret version containing the OAuth credential.

No credentials must be stored in Terraform variables or state.


## DEV GitHub integration

The DEV environment uses a regional Cloud Build repositories v2 connection.

Runtime values are supplied through the ignored `terraform.tfvars` file:

- Connection name.
- GitHub App installation ID.
- OAuth Secret Manager version reference.
- Repository URI.

The OAuth token payload is never stored in Terraform configuration or state.
Only the Secret Manager resource-version reference is configured.

The host connection is authorized interactively once and subsequently adopted
into Terraform state. Repository links are created declaratively by Terraform.

[Historial de cambios del componente](CHANGELOG.md).
