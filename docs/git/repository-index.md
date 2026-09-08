# Repository Index

Baseline captured: 2026-09-07
Repository: `eventmgmtplatform/event-management-platform`

## Global baseline state

- Local branches: 26
- Corresponding origin branches: 26
- Synchronization failures: 0
- Local tips unreachable from origin: 0
- Remote-only branches: 0
- Non-ignored untracked paths: 0
- Tracked sensitive/generated check: no matches
- Merge commits in captured history: 0
- Historical tags: 1
- Force push used during recovery: no

## Branch inventory

| Branch | Baseline SHA | State |
|---|---|---|
| `develop` | `8e508809c16557553887f4a240741b75f3eeadc4` | local/origin exact |
| `feature/gcp-terraform-bootstrap` | `c10a9a7e8c935eba78e172a4942daae00f465166` | local/origin exact |
| `feature/os-01-01-laboratorio-event-management` | `741f631080e1d496f8f50abf9647c22b856ef464` | local/origin exact |
| `feature/os-01-02-servicenow-core-foundation` | `85af2ff1d7d37c2b656e72bef99a1a512deb0589` | local/origin exact |
| `feature/os-08-05-1-gcp-bootstrap-secret-manager-api` | `de81511421a8a486436daffd710b042bd0f67abe` | local/origin exact |
| `feature/os-08-05-2-gcp-bootstrap-cloud-build-apis` | `3db90c3f0a99878a8744f6cfbd924d024e168111` | local/origin exact |
| `feature/os-08-06-project-common` | `7c0e54dce5fc8485fc43844bff52bfd594a90266` | local/origin exact |
| `feature/os-08-07-gcp-networking` | `3aeb90ccbd122ebf8d95c9844267231e7419ba02` | local/origin exact |
| `feature/os-08-08-2-gcp-iam-secret-manager` | `35d6b40baa51becc18b2c4c26e57a156e3374058` | local/origin exact |
| `feature/os-08-08-3-gcp-iam-cloud-build` | `c6899b3a4cb01a8e73b52ef6fc628efd62daa922` | local/origin exact |
| `feature/os-08-08-4-gcp-iam-cloud-build-service-agent` | `7186231c67de540b0402556c797d50d857b2c6bc` | local/origin exact |
| `feature/os-08-08-5-gcp-cloud-build-p4sa-secret-manager` | `b23b20375d17e8754072a4d46fe7965b29c6f0d7` | local/origin exact |
| `feature/os-08-08-6-revoke-cloud-build-p4sa-secret-manager` | `ea9530133f6f7d1ecb81b9eb83f71148fc7f1069` | local/origin exact |
| `feature/os-08-08-gcp-iam` | `5a5ca507f1c84e9705f231c57279222d456c1c93` | local/origin exact |
| `feature/os-08-08-gcp-iam-artifact-registry` | `25040e795edbaec899b8c49a60ec139a91ceaed1` | local/origin exact |
| `feature/os-08-09-gcp-artifact-registry` | `133af4258aa615e79e117451582a3181533af093` | local/origin exact |
| `feature/os-08-10-gcp-cloud-storage` | `3dcd749baf12d6ea516693c5e655a7d1b1577809` | local/origin exact |
| `feature/os-08-11-gcp-secret-manager` | `c6e915d4f77ad31294bd3915360c44ad3a6c4706` | local/origin exact |
| `feature/os-08-12-1-normalize-build-layout` | `0814802073d0c3fa026400c1db82ee47b885d84e` | local/origin exact |
| `feature/os-08-12-2-cloud-build-pipelines` | `1e13b6103547272a70e34f4c51c617036cd03ec4` | local/origin exact |
| `feature/os-08-12-gcp-cloud-build` | `0814802073d0c3fa026400c1db82ee47b885d84e` | local/origin exact |
| `feature/os-08-13-continuous-delivery-foundation` | `2b138dbcbd595adc6ab48119eac0dbc0839153e7` | local/origin exact |
| `integration/event-management-current-state` | `fc51edcaa23a3181c031c0f233f46a58beb4f4b3` | certified current-state publication |
| `main` | `8e508809c16557553887f4a240741b75f3eeadc4` | local/origin exact; promotion pending |
| `release/os-06-d06-gnm-core-v1.0.0` | `909f3b3712eac7525154fb74497bb4ffab68353c` | certified/frozen D06 checkpoint |
| `remediation/os-06-d06-gnm-core` | `909f3b3712eac7525154fb74497bb4ffab68353c` | local/origin exact |

## Governance workstream

`feature/os-00-04-git-governance` was created from `fc51edcaa23a3181c031c0f233f46a58beb4f4b3` after the baseline audit. It is intentionally not part of the 26-branch pre-governance snapshot above until its first publication is certified.

## Tag inventory

| Tag | Target | Purpose |
|---|---|---|
| `terraform-project-common-v0.1.0` | commit containing `feature/os-08-06-project-common` checkpoint | Initial project-common Terraform module |

No repository-wide tagging policy is inferred from this single historical tag.

## Ignored local material observed

At baseline, ignored material included `.env`, an owner-guarded recovery ZIP, `evidence/`, Compose backups, Terraform `.terraform/`, plans, state and tfvars, Java targets, and SN-UI generated backups/markers. These are intentionally outside the repository unless a future governance decision explicitly changes classification.

## Pending governance decisions

- formal promotion path into `develop` and `main`;
- PR requirement;
- merge vs squash vs rebase;
- GitHub branch protections;
- CODEOWNERS;
- general release tagging;
- product-wide semantic versioning.

## CACF local — 2026-09-08

- Workstream: OS_05_CACF.IMP.
- Rama: `feature/os-05-cacf-core-foundation`.
- Checkpoint de implementación publicado: `3d45006cbf23e2bef556da32946ac18035d4a7bb`.
- Upstream: `origin/feature/os-05-cacf-core-foundation`.
- Alcance: CACF, dependencias ServiceNow, Compose local y documentación de código.
- Validación: 171 pruebas sin fallos y E2E local con mocks; véase
  `docs/cacf/validation.md`. No certifica proveedores reales.
- PKC y materiales de library excluidos de esta publicación.
- No se promovieron main/develop ni se crearon tags de release.
- Los conteos del inventario anterior corresponden a la baseline de 2026-09-07;
  no representan una auditoría global nueva.
