# Appearance and product navigation — 2026-09-10

## Delivered

- Header selector: Actual / Kyndryl; remembered as `console.theme`. Default remains the existing dark theme. Both themes use 14 px body type, fixed 28 px desktop / 24 px narrow headings, compact toolbars and tables, 34 px controls and visible keyboard focus.
- Kyndryl uses warm red `#ff462d` and warm neutral `#f2f1ee`, observed in the public Kyndryl website stylesheet. Dark text on bright red buttons, darker red links, neutral surfaces and squared controls. The current theme retains its original blue/dark palette.
- This is a CSS theme inspired by Carbon's productive typography and component conventions. `@carbon/react` was evaluated but not installed: replacing the existing custom controls is a separate component migration. No claim of Carbon component or brand certification.
- Destination badges are compact; assignment groups/Teams/extension references can be selected as individual columns, viewed in the editor, or inspected via badge tooltips. Screen-reader text retains destinations.
- Middleware shows the fixed existing Kafka cluster through Kafbat's API: topic inventory, purpose, partitions, replication, message count, cleanup policy, missing topics and configuration drift. The Manage Kafka button opens the already installed full administration console at port 8085. No Kafka configuration or message was changed by this implementation.
- Blackouts, Inventory Services, Policies, AIOps Extensions and AutoSuppression are distinct read-only dashboards with PostgreSQL records, tenant/search filters and details. Empty and unavailable states are explicit. Runtime rules retain their existing versioned publication API; these dashboards do not edit/activate rules or bypass that API.
- Inventory Services uses customer inventory resources. It is distinct from Docker service inventory in Administration.

## Verification

- TypeScript/shared Docker production build: pass.
- 12 management API unit tests: pass, including missing Kafka topic, partition drift and under-replication classification.
- Live API: all five catalog views return HTTP 200; their main-runtime catalogs currently contain zero records. No demo rules inserted.
- Live Middleware: ONLINE, one broker, eight non-internal topics found; ninth required product topic `events.state.requested` is absent and visibly reported as missing. Kafka and certification containers were not restarted or modified.
- Browser: Kyndryl table layout and Middleware verified visually. Current theme + English verified on AutoSuppression. Theme/language survive reload and route changes.

## References and assets

- Carbon React/Vite compatibility: https://carbondesignsystem.com/developing/frameworks/react/
- Productive type scale: https://carbondesignsystem.com/elements/typography/type-sets/
- Kyndryl palette source: https://www.kyndryl.com/us/en (public clientlib-site stylesheet).
- Apache Kafka logo asset: https://kafka.apache.org/images/apache-kafka.png. Apache Kafka and its logo are trademarks of the Apache Software Foundation; used to identify the Kafka administration module. Navigation includes a small vector rendering of the mark. No external asset request is required at runtime.
- Mobile 390 × 844: theme/language selectors, collapsed navigation open/close and table scrolling verified; viewport reset afterwards. Positioned accessible labels are contained in the table scroller.
