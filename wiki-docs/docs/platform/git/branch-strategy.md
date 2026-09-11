# Branch Strategy

## Observed baseline

The repository history is predominantly linear and checkpoint-oriented. Workstream branches preserve named milestones while later branches contain earlier commits. At the 2026-09-07 baseline all 26 local branch tips had exact upstreams and all were reachable from `origin`.

## Branch classes

### `feature/*`
Primary development/workstream branch. Examples:

- `feature/os-01-01-laboratorio-event-management`
- `feature/os-01-02-servicenow-core-foundation`
- `feature/os-08-13-continuous-delivery-foundation`
- `feature/os-00-04-git-governance`

A feature branch MAY contain multiple related commits and MUST be published under its nominal name when it represents a project workstream/checkpoint.

### `remediation/*`
Isolated correction/recovery branch used when a certified or important baseline needs remediation without mutating the certified pointer. Current example: `remediation/os-06-d06-gnm-core`.

### `release/*`
Certified release/checkpoint reference. Current example: `release/os-06-d06-gnm-core-v1.0.0`. Release references MUST NOT be moved casually.

### `integration/*`
Cross-workstream consolidation/checkpoint. Current example: `integration/event-management-current-state` at `fc51edc...`. Integration does not automatically imply production release.

### `develop`
Long-lived development promotion branch. At baseline it is synchronized with origin at `8e50880...`, but it has not yet been promoted to the current integration state.

### `main`
Long-lived primary branch. At baseline it is synchronized with origin at `8e50880...`. Promotion policy into `main` remains pending.

## Naming

Preferred workstream naming:

`feature/os-<domain>-<sequence>-<short-purpose>`

Existing historical names are preserved; they MUST NOT be renamed merely for stylistic consistency.

## Creation rule

A new workstream SHOULD branch from an explicitly identified and synchronized baseline. The starting branch and exact SHA MUST be recorded before `git switch -c`.

## Upstream rule

Every maintained local branch SHOULD have a same-named `origin/<branch>` upstream. Publication is certified when the local SHA equals the remote SHA.

## Promotion model — pending

Two models are plausible but not yet decided:

1. `feature -> develop -> release -> main`
2. `feature/workstream -> certification -> integration/current-state -> develop -> main/release`

The second more closely resembles current accumulated practice, but no promotion MUST occur until gates are formally approved.
