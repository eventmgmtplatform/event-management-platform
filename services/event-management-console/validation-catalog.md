# Local validation — 2026-09-10

- PostgreSQL migration 023 and restricted catalog login applied successfully.
- Seed preserved 12 existing filters and added 8 disabled demonstrations: 20 total, 3 customers.
- TypeScript compile and shared Docker build pass.
- API integration test: create/read/search/update/delete customers and filters, all five destinations, ticket dependencies, search by target, stale write rejection, invalid write rollback, referenced customer delete protection, foreign Origin rejection: PASS.
- Browser Spanish: 20-filter table, edit dialog with GNM/SNOW/CACF/ChatOps/AIOps fields, save confirmation in PostgreSQL: PASS.
- Browser English: search saved filter returns one result; add APPLID column/remove Origin column: PASS. Customer form fields and save confirmation: PASS.
- Service lifecycle tests: 9 unit tests pass. Browser stop/start/restart of isolated ServiceNow Console Mock each completed and returned expected stopped/healthy state. Original certification mock was not restarted.
- ITSM stays on localhost:8091. Specialized tools include current ITSM, Kafka UI, OpenSearch Dashboards, Open WebUI and explicitly named legacy ITSM on 8088, with five ITSM view shortcuts.
- No PostgreSQL web administration interface was found in deployed containers or Compose configuration; no nonfunctional link was invented.

Scope: real catalog CRUD. The existing catalog is not wired to publish routing-engine rules. Demo destinations are fictitious. No main PostgreSQL restart or running certification stack mutation was used in verification.
