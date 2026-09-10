# Blackouts v1 — frontend validation, 2026-09-10

Deployed at http://localhost:8090/blackouts using the existing console container and Processor API. No backend, SQL schema, consumers, CORS, authentication or provider mocks were changed.

## Implementation

- `src/modules/blackouts/BlackoutsPage.tsx`: table from catalog, explicit tenant selection, manual SCHEDULED/IMMEDIATE form, validation, separate save/activation, disable/terminal retire, active definition, latest/active versions, history and active/draft simulation.
- `src/modules/blackouts/blackouts.api.ts`: same-origin adapter, quoted ETag/If-Match, idempotency key per logical operation, exact preserved body/actor/revision on retry, timeout/error handling and explicit ISO timestamps.
- `src/modules/blackouts/blackouts.css`: existing theme variables and responsive fields.
- `src/app/router.tsx`, `src/layout/Header.tsx`, `src/shared/i18n/en.json`: route integration, data source badge and translations.
- `nginx.conf`: allowlisted `/api/processor/v1/rules`, rule/history/transitions and simulations routes to `event-processor:8082/api/v1`. Query strings, status and conditional headers pass through; no mock administration routes exposed.

Writes require an explicitly selected tenant. `X-Actor-Id: local-console` is a local declaration, not authenticated identity. Scope selectors are exact conjunctions, with all-tenant scope called out at confirmation. The form accepts explicit offsets or Z and IANA timezone; it does not interpret ambiguous local times or offer recurrence.

Saving never activates. When v1 is active, saving v2 leaves v1 active. Mutation results are refreshed from the server. Conflicts preserve the draft and require loading/reconciling the current revision. Uncertain operations lock further writes and persist their exact request in sessionStorage so a reload can retry the same logical action. Rule data remains authoritative on the server. Simulations show actual Blackout.match, directive, checksum and stage evidence, with candidate replacement explicitly labeled.

Legacy catalog rows are read-only; their enabled flag is not advertised as a running engine rule. RETIRED is distinct from disabled. Catalog truncation is visible; no fabricated table pagination is offered. History uses its real cursor.

## Browser acceptance

The full walkthrough is recorded in `evidences/blackouts/frontend-20260910/browser-report.json`. Cases 1–8 and 10 used real Processor writes and simulations from the browser, plus a scoped outage injection for failure handling. Case 9 used an explicitly named legacy fixture because the current database had no legacy rows. A double-click creation and a persisted same-key retry each produced only one creation history entry. The retry fault happened before persistence; lost-response-after-commit was not injected.

Real browser writes were corroborated by a read-only HTTP evidence script: manual-router has two versions and six changes; retry-immediate has one version and two changes. Both ended RETIRED. Full definitions, checksums, quoted ETags and history are in `server-after-browser.json` beside the browser report.

The proxy fixture was temporary and restored before the final image build. It only faulted the synthetic tenant and served a visibly labeled legacy fixture for an explicit test URL. It did not stop Processor or change catalog data. No synthetic rule remains active.

## Reproduce checks

```sh
node --test testing/services/event-management-console/blackouts.test.mjs
python3 testing/services/event-management-console/blackouts-live-evidence.py
```

The second command verifies records already created by the browser; it is not a substitute for the browser walkthrough. Unit checks cover strict instant handling, invalid windows/tenant/type, exact repeated mutation headers/body and failure propagation. TypeScript and Docker builds passed. The final deployment was inspected in Spanish/Kyndryl and English/Current, including translated history headings and retired controls; Spanish/Kyndryl was restored.

For fault testing only, `blackouts-proxy-fixture.py install` injects the documented scoped Nginx fixture; always run `restore` afterward. This helper requires Docker access and is not part of the deployed application.

## Deployment

```sh
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml up -d --build --no-deps --wait --wait-timeout 60 event-management-console
```

Only the shared UI container was recreated. The ITSM interface remains on 8091. No physical deletion or ID reuse was added. Existing concurrent workspace changes were preserved.
