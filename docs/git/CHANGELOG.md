# Git Governance Changelog

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
