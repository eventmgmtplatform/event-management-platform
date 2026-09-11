# Commit Convention

## Baseline

Repository history demonstrates de facto Conventional Commit style. The governance baseline formalizes that observed trend without rewriting historical commits.

## Format

`<type>(<scope>): <imperative description>`

Scope MAY be omitted when historical/project context justifies it, but scoped commits are preferred for new work.

## Types observed and accepted

- `feat` — new capability or functional work.
- `fix` — correction or hardening.
- `docs` — documentation-only change.
- `test` — tests/certification code.
- `build` — build/container/build-layout changes.
- `chore` — repository, operational or cross-cutting maintenance.

## Scope guidance

Prefer project/workstream scopes where useful:

- `os-01-01`
- `os-08-13`
- `os-08-14`
- `servicenow`
- `d06`
- `terraform`
- `cloud-build`
- `integration`
- `git`

## Examples from history

- `feat(os-08-13): add continuous delivery foundation`
- `fix(os-08-14): harden runtime persistence and result idempotency`
- `feat(servicenow): add durable command idempotency`
- `feat(d06): implement GNM notification core and Everbridge lifecycle`
- `chore(integration): capture accumulated project workstreams`

## New governance examples

- `docs(git): establish repository governance baseline`
- `chore(git): refresh repository publication index`
- `fix(git): harden publication preflight`

## Rules

1. Subject MUST describe the change, not merely the activity (`update files` is insufficient).
2. A commit MUST NOT claim certification that has not been performed.
3. Cross-workstream recovery commits SHOULD use a neutral scope such as `integration`.
4. Release tags are not automatically created from commit subjects.
5. Large unrelated functional changes SHOULD NOT be mixed into Git-governance commits.
