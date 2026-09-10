# ESS administration validation — 2026-09-10

Implemented at http://localhost:8090/administration/ess, accessible from Administration. The existing console and console-catalog-api were rebuilt and deployed; ESS, worker, processor and test environments were not restarted for this change.

## Behavior and access boundary

Tenant event list with cursor pagination, exact event-key lookup, detail, explicit transition history, and a separately enabled operator-global quarantine summary. Source events and correlated situations are labeled separately; event OPEN/CLOSED is distinct from provider confirmations. Null fields display an em dash. Tenant/event changes abort requests and clear prior data. Errors are displayed with retry; there is no mock fallback or replay/rebuild action.

The browser calls GET /api/ess/* on its own origin. The catalog API selects allowlisted tenant tokens from a read-only secret mount and calls only event-state-service:8084. Client-supplied auth headers, unknown queries, duplicate arguments, unsupported methods and routes are rejected. Response fields are projected through an allowlist; arbitrary payloads and upstream error text are excluded. ESS access logging is disabled in Nginx and responses use no-store.

Authorization is the shared local console boundary: configured tenants are available to local console users, and the global operator capability is independently enabled server-side. This is not per-user RBAC. Current integration references include synthetic/provider-mock data and are not production provider certification.

## Executed checks

- Python proxy suite: 11 tests passed, including authorization, query validation, cursor handling, field projection, operator separation, missing configuration and sanitized errors.
- Frontend adapter test file passed: nullable values, decoding, URL encoding, request headers, abort propagation and failure handling.
- TypeScript build and Docker build passed; console and catalog API healthy after deployment.
- Live HTTP checks passed for vit and os11-synthetic: limit=1 cursor traversal, detail/history, cross-tenant 404, invalid queries, forbidden mutations, global quarantine and no-store. Real credential values were checked in memory against responses and served JS/CSS; none were found. See ../../evidences/ess-ui/20260910/live-report.json.
- Browser smoke passed in Spanish/Kyndryl and English/Current: tenant switch visibly clears old rows while loading, real source/correlation rows, detail, nullable fields, empty explicit history, Escape closing dialog, nonexistent event 404 with Retry, and three global quarantine reasons without a tenant selector. Visual presentation inspected in both themes. Restored Spanish/Kyndryl.
- Each current tenant has four rows, so browser Next is correctly disabled for minimum page size 10. Actual next-cursor traversal was verified by the live HTTP suite using limit=1. No claim of browser history pagination with populated transitions, mobile layout, or injected browser network failures is made.

## Reproduce

```sh
python3 -m unittest discover -s testing/services/console-catalog-api -p 'test_ess*.py'
node --test testing/services/event-management-console/ess.test.mjs
python3 testing/services/console-catalog-api/ess_live.py
```

The live test requires the local secret file, but never prints its contents.

## Deployment and rollback

```sh
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml up -d --build --no-deps --wait --wait-timeout 60 console-catalog-api event-management-console
```

Previous image IDs and an image rollback override were preserved in evidences/ess-ui/20260910. Rollback uses locally retained images, disables ESS authorization in the catalog environment, and does not revert source or restart ESS:

```sh
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml -f evidences/ess-ui/20260910/rollback.compose.yml up -d --no-build --no-deps --wait console-catalog-api event-management-console
```
