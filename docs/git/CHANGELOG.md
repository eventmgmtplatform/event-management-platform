# Git Governance Changelog

## 2026-09-09 — Consolidación autorizada hacia main

- El usuario autoriza promover a main todos los commits pendientes de las ramas.
- Inventario: 33 commits únicos fuera de main (`8e50880`); 32 contenidos en
  `1c3ede3` y un commit documental CACF (`3a657ae`).
- Merge conservando ambos historiales, sin rebase, squash ni force push.
- Se preserva el código funcional de `1c3ede3` sin cambios.
- Reportes de administración y cachés Python se excluyen de Git sin borrarlos.
- Evidencia y bundle previo: `evidence/os-00-04-main-consolidation/20260909T103330Z/`.
- No se promueve develop ni se mueven referencias release/remediation históricas.
- Rama funcional posterior: `feature/os-06-core-event-processor`.

## 2026-09-08 — Publicación CACF local

- Publicado `3d45006cbf23e2bef556da32946ac18035d4a7bb` en la rama
  `feature/os-05-cacf-core-foundation`, con upstream del mismo nombre.
- Registrada la regla global del usuario: documentación técnica junto al código;
  PKC y materiales exclusivos de library fuera de Git.
- Conservados cambios concurrentes de administración de servicios fuera de CACF.
- Sin promoción a main/develop, tags ni cambios Cloud Build/Terraform.


## [1.0.0] — 2026-09-07

### Recovery and publication baseline

- Audited the repository before publication of accumulated work.
- Expanded `.gitignore` so all `evidence/` is excluded rather than only `evidence/os-08-10/`.
- Excluded SN-UI generated backups and marker files.
- Classified useful untracked work separately from generated operational artifacts.
- Verified no tracked evidence and no real credential material in the controlled staged universe.
- Created `integration/event-management-current-state` from certified D06 SHA `909f3b3712eac7525154fb74497bb4ffab68353c`.
- Captured 37 audited paths in commit `fc51edcaa23a3181c031c0f233f46a58beb4f4b3` with subject `chore(integration): capture accumulated project workstreams`.
- Published the integration branch using normal push; local and remote SHA matched exactly.
- Preserved D06 release/remediation pointers at `909f3b3712eac7525154fb74497bb4ffab68353c`.
- Published 13 previously local-only nominal workstream branch refs.
- Configured upstreams for all maintained local branches, including existing ServiceNow foundation.
- Certified zero branches without upstream and zero local tips unreachable from origin.
- Captured post-recovery baseline with `SYNC_FAILURES=0`, no remote-only branches and clean working tree.

### Governance foundation

- Established `OS_00_04.GIT — Git Repository Governance, Publication Baseline & Maintenance` as the permanent Git maintenance workstream.
- Created `feature/os-00-04-git-governance` from integration checkpoint `fc51edcaa23a3181c031c0f233f46a58beb4f4b3`.
- Formalized observed Conventional Commit practice.
- Formalized branch classes, controlled staging, evidence/secret exclusion, normal-push and exact-SHA certification rules.

### Pending

- Promotion policy to `develop`/`main`.
- PR and merge strategy.
- GitHub protection rules and CODEOWNERS.
- General release/tagging and product semantic-versioning policy.

## Unreleased — 2026-09-10 — OS_11

- Registra la rama `remediation/os-11-01-lifecycle-orchestration` y el checkpoint funcional `c2e568949de7eae26b787a20d4cf88f82e50d474`.
- Publicación de alcance backend mediante checkout aislado, conservando el trabajo concurrente y los checkpoints protegidos.
