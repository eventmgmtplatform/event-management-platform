# Event Management Release Contract

## Overview

The `deploy` directory contains the runtime-independent release contract for **Event Management OpenSource**.

Its objective is to define **what** is going to be deployed, not **how** it will be deployed.

This contract is intentionally independent from:

* Google Cloud Platform
* AWS
* Microsoft Azure
* Docker Compose
* Kubernetes
* Cloud Run
* GKE
* Compute Engine

Any runtime implementation must consume this contract without modifying its structure.

---

# Directory Layout

```text
deploy/
├── README.md
├── manifests/
│   └── dev/
│       └── release-template.yaml
└── schemas/
    └── release-manifest.schema.json
```

---

# Architecture

```
Cloud Build
        │
        ▼
Artifact Registry
        │
        ▼
Release Manifest
        │
        ▼
JSON Schema Validation
        │
        ▼
Continuous Delivery
        │
        ▼
Runtime
```

The runtime is intentionally outside the scope of OS_08_13.

---

# Release Manifest

A Release Manifest represents an immutable software release.

It contains:

* release metadata
* artifact registry information
* immutable image references
* runtime contract
* health contract
* service dependencies
* logical secret references
* rollback policy

It never contains deployment instructions.

---

# Immutable Images

Every service must be identified by its immutable SHA-256 digest.

Example:

```
us-central1-docker.pkg.dev/<project>/<repository>/<service>@sha256:<digest>
```

The digest is the deployment identity.

The following values are **not** deployment identities:

* latest
* dev
* qa
* prod
* branch names
* Cloud Build IDs

Those values are only metadata.

---

# Runtime Contract

Each service defines:

* container name
* exposed port
* health strategy
* dependencies
* logical secrets

The runtime decides how those values are implemented.

For example:

Docker Compose

* container_name
* depends_on

Kubernetes

* Deployment
* Service
* Probes

Cloud Run

* Revision
* Container
* Startup Probe

Compute Engine

* Docker Compose
* Systemd

The contract remains unchanged.

---

# Health Contract

Two health strategies are currently supported.

## functional

Used by services exposing a functional endpoint.

Required fields:

* type
* path

Example:

```
type: functional
path: /api/v1/gateway
```

---

## smallrye

Used by Quarkus services exposing SmallRye Health.

Required fields:

* type
* path
* livePath
* readyPath

Example:

```
type: smallrye
path: /health
livePath: /health/live
readyPath: /health/ready
```

---

# Secret References

The Release Manifest never stores secrets.

Only logical references are allowed.

Example:

```
secretRefs:

- database_credentials

- servicenow_credentials
```

The runtime resolves those references using its own secret management implementation.

Examples:

* Google Secret Manager
* AWS Secrets Manager
* Azure Key Vault
* HashiCorp Vault

---

# Traceability

Each service stores:

* Artifact Registry repository
* immutable digest
* immutable image reference
* Cloud Build ID
* source commit

This allows complete traceability between source code, build and deployment.

---

# Rollback

Rollback is manifest-based.

Rollback must use:

* previous approved manifest
* previous immutable digest

Rollback must never:

* rebuild an old commit
* guess a previous image
* deploy latest
* retag an image

---

# Current Scope (OS_08_13)

Implemented:

* Release Manifest
* JSON Schema
* Artifact traceability
* Runtime contract
* Health contract
* Secret references
* Rollback contract

Not implemented:

* Compute Engine deployment
* Docker Compose execution
* Cloud Run deployment
* GKE deployment
* Kubernetes manifests
* Runtime health execution
* Automatic rollback
* Progressive deployment

Those capabilities belong to subsequent specifications.

---

# Design Principles

* Immutable releases
* Runtime independence
* Cloud independence
* Declarative deployment
* Infrastructure as Code
* GitOps compatibility
* Complete traceability
* Least privilege
* No mutable deployment identities
* Rollback by manifest

---

# Project Status

Current implementation:

```
Cloud Build
        │
        ▼
Artifact Registry
        │
        ▼
Release Manifest
        │
        ▼
JSON Schema Validation
        │
        ▼
Continuous Delivery Contract
```

The runtime layer will be introduced after the Continuous Delivery contract has been fully certified.

[Historial de cambios del componente](CHANGELOG.md).
