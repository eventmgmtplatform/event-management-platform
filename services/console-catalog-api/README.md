# Console catalog API

Local management API for `/criteria-filters` and `/customers` in the existing console on port 8090. Python + psycopg + PostgreSQL, behind the console's Nginx; no host port and no Docker socket. ITSM remains on port 8091.

Reuses `event_management.delivery_filter` and `delivery_filter_target` from migration 022. Migration 023 adds `customer_configuration` and an append-only API audit trail. Customer codes already present in the filter catalog are backfilled; metadata is left blank when unknown. Existing importers remain compatible (no new foreign key on their catalog). The API validates customer references, serializes customer deletion against filter creation and rejects deletion while filters reference a customer.

## Local deployment

Migration 022 must already be applied. From the project root:

```sh
python3 scripts/dashboards/bootstrap-console-catalog.py
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml up -d --build --no-deps --wait console-catalog-api frontend-management-api event-management-console
```

Bootstrap is idempotent, preserves existing rows, creates a restricted database login and stores its generated password only in `.local/console-catalog/db-password` (ignored, mode 0600). The API mounts this file read-only. Its database role has CRUD on the three catalog tables and insert-only audit access. Container DAC_OVERRIDE permits reading the owner's 0600 bind-mounted secret; all other capabilities are dropped. Compose scripts manage the new service in their normal start/stop ordering.

## Contract

- `GET /api/catalog/{customers|filters}?q=...`: literal case-insensitive substring search across stored fields; filter search includes targets. At most 2,000 results; narrower search required beyond that limit.
- `GET /api/catalog/{customers|filters}/{id}`: one record.
- `POST /api/catalog/{customers|filters}`: create (201).
- `PUT /api/catalog/{customers|filters}/{id}`: replace editable fields and targets, with the last observed `updated_at`.
- `DELETE /api/catalog/{customers|filters}/{id}`: remove, with the last observed `updated_at`.

Writes require JSON and `X-Console-Action: 1`; foreign Origin is rejected. Mutations and audit writes share one database transaction. Stale writes return 409, input errors 400, missing records 404, unavailable PostgreSQL 503. Credentials and SQL errors are never returned. This inherits the console's localhost-only access boundary; this is not an authenticated multi-user administration service for public deployment.

Filters retain all existing target attributes (behavior, actionReference, assignmentGroup, delaySeconds, dependsOnTicketing). GNM/CACF/ChatOps expose ticket waiting; ChatOps uses assignmentGroup for its Teams destination, and extensions use actionReference. Criteria use the existing allowed field/operator model with string, number and boolean values. State values: 0 enabled, 1 disabled, 2 audit. New filters default to disabled.

Saving updates the configuration catalog and the existing ITSM Delivery view. Migration 022 explicitly defines this catalog as **not a runtime routing engine**: saving does not publish a processor rule, call a provider or trigger integrations. Customer defaults are copied only when the user adds a filter target; changing a customer does not silently rewrite existing filters.

Seed adds eight disabled `demo-console:*` filters and two fictional customers, bringing the existing 12-filter catalog to 20. Existing demo data is preserved. Local storage holds language and visible columns only; records live in PostgreSQL.

## Verification

```sh
python3 testing/services/console-catalog-api/api-test.py
```

The HTTP integration test creates unique temporary customer/filter records and cleans them up, verifies all five destinations, persistence, search including assignment group, update conflicts, invalid mutation atomicity, customer reference protection and Origin checks. Audit entries remain as evidence.

## Product read dashboards

Migration 024 grants SELECT only on existing product catalogs and processor rule/AIOps registries. `GET /api/catalog/views/{blackouts|inventory-services|policies|aiops-extensions|auto-suppression}` returns read-only data. Blackouts and Policies identify whether records come from the legacy catalog or the versioned Event Processor registry. Active rule versions are preferred; disabled records show the latest version. No runtime mutations or rule history bypass are introduced.

## ESS read proxy

`GET /api/ess/config`, `/events`, `/event`, `/history`, `/operator/quarantine` proxy only the fixed ESS read API. `ESS_ALLOWED_TENANTS` selects configured tenants; `ESS_ADMIN_CREDENTIALS_FILE` references a read-only server secret; `ESS_OPERATOR_QUARANTINE=true` independently enables the global summary with its distinct operator token. Tokens never go to the browser. This retains the shared local-console authorization boundary, not per-user RBAC. See `../event-management-console/validation-ess.md` for tests and rollback.
