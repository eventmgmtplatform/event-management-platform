# OS_00_04.GIT — Git Repository Governance, Publication Baseline & Maintenance

Version: 1.0.0
Baseline date: 2026-09-07
Repository: `eventmgmtplatform/event-management-platform`
Local path: `/opt/event-management-platform`
Governance branch: `feature/os-00-04-git-governance`
Starting checkpoint: `fc51edcaa23a3181c031c0f233f46a58beb4f4b3`

## Purpose

This directory is the permanent Git governance baseline for OPEN EVENT MANAGEMENT. It defines how workstreams are created, inspected, staged, committed, published, certified, indexed and maintained without depending on chat history.

The initial recovery objective was completed on 2026-09-07: all useful local work was captured, generated evidence was excluded, all nominal workstream branches were published, every local branch obtained an upstream, and every local branch tip became reachable from `origin`.

## Canonical documents

- `governance.md` — normative repository rules and safety gates.
- `branch-strategy.md` — branch classes and lifecycle.
- `commit-convention.md` — commit naming baseline derived from repository history.
- `publication-runbook.md` — operational publication procedure.
- `repository-index.md` — certified branch/checkpoint inventory.
- `CHANGELOG.md` — Git governance and publication history.

## Automation

- `scripts/git/repository-audit.sh` — read-only local/remote repository audit.
- `scripts/git/publication-preflight.sh` — read-only publication safety preflight.

## Certified checkpoints at baseline

| Checkpoint | SHA | Meaning |
|---|---|---|
| Integration current state | `fc51edcaa23a3181c031c0f233f46a58beb4f4b3` | Cross-workstream state captured and published |
| D06 release | `909f3b3712eac7525154fb74497bb4ffab68353c` | Certified GNM/Everbridge D06 release |
| D06 remediation | `909f3b3712eac7525154fb74497bb4ffab68353c` | D06 remediation pointer at baseline |
| ServiceNow foundation | `85af2ff1d7d37c2b656e72bef99a1a512deb0589` | ServiceNow/Console foundation checkpoint |
| OS_08_13 | `2b138dbcbd595adc6ab48119eac0dbc0839153e7` | CD foundation branch including later OS_08_14 hardening commit |
| main | `8e508809c16557553887f4a240741b75f3eeadc4` | Synchronized, not yet promoted to current integration state |
| develop | `8e508809c16557553887f4a240741b75f3eeadc4` | Synchronized, not yet promoted to current integration state |

## Baseline health

At capture time: `SYNC_FAILURES=0`, `UNREACHABLE_LOCAL_TIPS=0`, no non-ignored untracked files, no tracked sensitive/generated matches, and no remote-only branches. There were 26 local branches and 26 corresponding origin branches.

## Explicitly pending policy

This baseline does **not** invent decisions that repository history does not prove. Formal promotion (`integration -> develop -> main`), PR policy, merge/rebase/squash policy, GitHub branch protection, CODEOWNERS, global release tagging and product-wide semantic versioning remain pending decisions.

[Historial de cambios del componente](CHANGELOG.md).
