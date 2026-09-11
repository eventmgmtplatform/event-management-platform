# Continuous Delivery Terraform Module

## Purpose

This module establishes the Continuous Delivery foundation for OPEN EVENT MANAGEMENT.

It creates the minimum Google Cloud identity and access required for a future delivery runtime to consume immutable container artifacts and operate against the normalized release contract introduced by OS_08_13.

The module deliberately does **not** deploy application containers or create a runtime.

## Scope

The module provides:

- A dedicated Continuous Delivery service account.
- Read-only access to the configured Artifact Registry repository.
- Normalized deployable-service contracts.
- A reusable release contract exposed through Terraform outputs.
- Runtime-independent metadata for ports, health checks, dependencies, and logical secret references.

## Out of Scope

OS_08_13 does not create or manage:

- Compute Engine instances.
- GKE clusters or Kubernetes workloads.
- Cloud Run services.
- Google Cloud Deploy resources.
- Docker or Docker Compose execution.
- Application container deployment.
- Runtime health-check execution.
- Automatic rollback execution.
- Secret payloads or credentials.

Those capabilities belong to later runtime and deployment specifications.

## Resources

The module intentionally creates exactly two Terraform resources:

1. `google_service_account.execution` — dedicated execution identity for Continuous Delivery.
2. `google_artifact_registry_repository_iam_member.reader` — read-only access to the selected Artifact Registry repository.

No broad administrative roles are required by this module.

## Service Contract

The `services` map describes each deployable application's container name, port, health strategy, runtime dependencies, and logical secret references.

The current dev composition defines:

- `event-gateway`
- `integration-worker`
- `event-state-service`

This contract describes runtime requirements without selecting a runtime implementation.

## Secret Handling

Secret references are logical identifiers only. The module must not contain or expose passwords, tokens, API keys, credentials, or secret payloads.

## Release Model

OS_08_13 uses immutable container image identity:

```text
REGION-docker.pkg.dev/PROJECT/REPOSITORY/SERVICE@sha256:DIGEST
```

Mutable tags such as `latest`, environment tags, branch tags, and Build IDs are not deployment identities.

The release manifest under `deploy/` is the authoritative runtime-independent release contract.

## Provider Requirements

- Terraform `>= 1.15.0, < 2.0.0`
- HashiCorp Google provider `>= 7.0, < 8.0`

The provider is configured by the calling environment.

## Security Principles

- Dedicated execution identity.
- Least privilege.
- Read-only artifact consumption.
- No runtime creation.
- No broad IAM roles.
- No embedded secret payloads.
- Separation between build, delivery, and runtime responsibilities.
- Immutable release identity.

## Validation

Typical repository checks:

```text
terraform fmt -recursive -check infrastructure/gcp/terraform
terraform -chdir=infrastructure/gcp/terraform/environments/dev validate
git diff --check
```

Release-contract validation:

```text
python3 deploy/scripts/validate_release_manifest.py deploy/manifests/dev/release-template.yaml
```

## OS_08_13 Boundary

This module is the **Continuous Delivery Foundation**, not the final deployment implementation.

```text
Cloud Build
    |
    v
Artifact Registry
    |
    v
Immutable Release Manifest
    |
    v
Continuous Delivery Contract
    |
    v
Future Runtime
```

The future runtime must consume this contract rather than redefining artifact identity, health semantics, dependencies, or rollback provenance.

[Historial de cambios del componente](CHANGELOG.md).
