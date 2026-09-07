# Git Governance Policy

Status: BASELINE v1.0.0
Workstream: `OS_00_04.GIT`

## 1. Normative language

`MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT` and `MAY` are normative requirements for repository maintenance.

## 2. Core principles

1. Useful project work MUST be recoverable from `origin`.
2. Generated operational evidence MUST remain outside Git.
3. Credentials, local configuration, Terraform state/plans/secret-bearing tfvars and private keys MUST NOT be committed.
4. Certified release pointers MUST NOT be moved incidentally.
5. Publication MUST be reproducible and verified by exact SHA.
6. Force push MUST NOT be used in normal project publication.
7. Workstream history SHOULD retain nominal branch names in `origin`, even when commits are already reachable through later branches.
8. Repository state MUST be inspected before staging and after publication.

## 3. Protected conceptual references

The following branch classes require explicit promotion or remediation decisions:

- `main`
- `develop`
- `release/*`
- certified `integration/*` checkpoints

A workstream MUST NOT modify these pointers merely to publish unrelated work.

## 4. Staging policy

Before commit, the operator MUST review:

- current branch and HEAD;
- tracked modifications;
- untracked non-ignored files;
- ignored files relevant to the workstream;
- staged paths;
- generated/evidence paths;
- credential-like content;
- unexpected large files;
- deletion and symlink changes.

Controlled publications SHOULD stage an explicit path universe. `git add .` SHOULD NOT be used for certified or recovery publications.

## 5. Ignore baseline

The project `.gitignore` currently excludes, among other items:

- `.env`, `.env.*` except `.env.example`;
- private key/certificate/keystore material;
- `credentials.json` and service-account JSON;
- `.terraform/`, `*.tfstate*`, `*.tfplan`, secret-bearing tfvars;
- Java/Maven targets and binary packages;
- logs and temporary files;
- local persistent Docker data;
- compressed backups/packages;
- `evidence/`;
- SN-UI generated backups and marker files.

Ignoring a file is not proof that it is safe. Publication preflight MUST still inspect staged content.

## 6. Evidence policy

`evidence/` is operational evidence, not source. It MUST NOT be committed. Evidence MAY remain on the local host or be archived in an external controlled evidence store. Scripts MAY generate evidence under ignored paths.

## 7. Secret policy

Real credentials MUST NOT be committed. Environment-variable references and explicit development placeholders may be versioned when they do not contain production secrets. Secret-like matches MUST be manually classified before publication.

## 8. Publication policy

Normal publication is:

`workstream -> validation -> controlled staging -> semantic commit -> normal push -> local/remote SHA certification -> index update`

Every relevant local branch SHOULD have a same-named upstream branch. After publication, local branch tip and upstream tip MUST match unless divergence is explicitly documented.

## 9. Certified checkpoints

A certified checkpoint is immutable by convention. New work MUST proceed on a new `feature/`, `remediation/` or `integration/` branch. A release branch is not automatically equivalent to a product-wide release.

## 10. Repository maintenance

`OS_00_04.GIT` owns repository indexing, branch/upstream maintenance, publication controls, checkpoint tracking and Git governance documentation. Functional implementation remains in its owning workstream.

## 11. Current exceptions and pending decisions

Repository history contains zero merge commits at baseline and only one historical tag (`terraform-project-common-v0.1.0`). Therefore this baseline intentionally does not prescribe merge commits, squash, rebase, PR enforcement or global tagging until separately decided and recorded.
