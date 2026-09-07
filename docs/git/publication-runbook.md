# Publication Runbook

## Purpose

Repeatable operational flow for publishing Event Management OpenSource work without leaking generated material or losing local work.

## Phase A — Discovery

1. `git fetch --prune origin`
2. Record current branch and exact HEAD.
3. Run `git status --short`.
4. Inspect local branches, upstreams and remote refs.
5. Identify the owning workstream and expected starting checkpoint.

## Phase B — Content classification

Classify every changed/untracked path as one of:

- versionable source/config/documentation;
- generated operational evidence;
- build/cache/runtime artifact;
- local configuration;
- credential/sensitive material;
- unknown — publication blocked until classified.

Never delete evidence merely to make Git clean. Ignore or archive it appropriately.

## Phase C — Preflight

Run `scripts/git/publication-preflight.sh` before staging/commit. Review secret-like matches manually. Validate symlinks, deletions and large files.

## Phase D — Controlled staging

Prefer explicit staging:

```bash
git add -- path/a path/b path/c
```

Then verify:

```bash
git diff --cached --name-status
git diff --cached --stat
git diff --cached --check
git diff --cached
```

`git diff --cached --check` whitespace warnings MUST be evaluated. They do not automatically justify mutating functional content.

## Phase E — Commit gate

Before `git commit` confirm:

- expected branch;
- expected pre-commit HEAD;
- exact staged scope;
- no forbidden evidence/generated paths;
- no unintended unstaged tracked changes;
- no unintended non-ignored untracked files;
- no real secrets;
- functional validation appropriate to the workstream.

Use a semantic commit subject.

## Phase F — Push

Use normal push:

```bash
git push -u origin <branch>
```

Force push is prohibited by default. Do not push tags unless the release/tag decision explicitly requires it.

## Phase G — Remote certification

After push:

```bash
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/$(git branch --show-current))
test "$LOCAL" = "$REMOTE"
```

Also verify:

- working tree clean;
- upstream configured;
- protected checkpoints unchanged;
- local branch tips reachable from origin.

## Phase H — Governance update

Update `repository-index.md` and `CHANGELOG.md` when a new checkpoint, branch class, release/remediation or publication-policy decision becomes material.

## Recovery publication pattern

The 2026-09-07 recovery established the reference pattern:

1. audit `.gitignore`;
2. classify all untracked content;
3. exclude `evidence/` and generated markers;
4. stage exact audited paths;
5. review staged diff and secret-like added lines;
6. commit on isolated integration branch;
7. verify protected D06 SHA unchanged;
8. normal push;
9. publish nominal historical workstream refs;
10. configure upstreams;
11. prove zero unreachable local tips.

## Stop conditions

Stop publication immediately on:

- unexpected branch/HEAD;
- remote branch divergence;
- unknown untracked file;
- real secret/credential;
- tracked evidence/state/plan artifact;
- unexplained deletion;
- protected checkpoint movement;
- failed functional certification required by the workstream.
