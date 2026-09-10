\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA IF NOT EXISTS dashboard_demo;
CREATE TABLE IF NOT EXISTS dashboard_demo.records (
 domain text NOT NULL CHECK (domain IN ('events','ticketing','gnm','cacf')),
 id text NOT NULL, tenant text NOT NULL CHECK (tenant = 'DEMO-DASHBOARDS'),
 status text NOT NULL, event_id text NOT NULL, reference text,
 updated_at timestamptz NOT NULL, severity integer, tally bigint, outcome text,
 PRIMARY KEY(domain,id)
);
-- Opt-in UI demo records, never visible to domain workers or their outboxes.
INSERT INTO dashboard_demo.records (domain,id,tenant,status,event_id,reference,updated_at,severity,tally,outcome) VALUES
('events','demo-dashboard:events:001','DEMO-DASHBOARDS','OPEN','DEMO-EVENT-001','DEMO-EVT-001',CURRENT_TIMESTAMP - INTERVAL '7 minutes',2,2,NULL),
('events','demo-dashboard:events:002','DEMO-DASHBOARDS','OPEN','DEMO-EVENT-002','DEMO-EVT-002',CURRENT_TIMESTAMP - INTERVAL '14 minutes',3,3,NULL),
('events','demo-dashboard:events:003','DEMO-DASHBOARDS','OPEN','DEMO-EVENT-003','DEMO-EVT-003',CURRENT_TIMESTAMP - INTERVAL '21 minutes',4,4,NULL),
('events','demo-dashboard:events:004','DEMO-DASHBOARDS','CLOSED','DEMO-EVENT-004','DEMO-EVT-004',CURRENT_TIMESTAMP - INTERVAL '28 minutes',0,5,NULL),
('events','demo-dashboard:events:005','DEMO-DASHBOARDS','CLOSED','DEMO-EVENT-005','DEMO-EVT-005',CURRENT_TIMESTAMP - INTERVAL '35 minutes',0,6,NULL),
('events','demo-dashboard:events:006','DEMO-DASHBOARDS','OPEN','DEMO-EVENT-006','DEMO-EVT-006',CURRENT_TIMESTAMP - INTERVAL '42 minutes',2,7,NULL),
('events','demo-dashboard:events:007','DEMO-DASHBOARDS','OPEN','DEMO-EVENT-007','DEMO-EVT-007',CURRENT_TIMESTAMP - INTERVAL '49 minutes',3,8,NULL),
('events','demo-dashboard:events:008','DEMO-DASHBOARDS','CLOSED','DEMO-EVENT-008','DEMO-EVT-008',CURRENT_TIMESTAMP - INTERVAL '56 minutes',0,9,NULL),
('ticketing','demo-dashboard:ticketing:001','DEMO-DASHBOARDS','CREATED','DEMO-EVENT-001','DEMO-INC-001',CURRENT_TIMESTAMP - INTERVAL '7 minutes',NULL,NULL,NULL),
('ticketing','demo-dashboard:ticketing:002','DEMO-DASHBOARDS','CREATED','DEMO-EVENT-002','DEMO-INC-002',CURRENT_TIMESTAMP - INTERVAL '14 minutes',NULL,NULL,NULL),
('ticketing','demo-dashboard:ticketing:003','DEMO-DASHBOARDS','PENDING','DEMO-EVENT-003','DEMO-INC-003',CURRENT_TIMESTAMP - INTERVAL '21 minutes',NULL,NULL,NULL),
('ticketing','demo-dashboard:ticketing:004','DEMO-DASHBOARDS','FAILED','DEMO-EVENT-004','DEMO-INC-004',CURRENT_TIMESTAMP - INTERVAL '28 minutes',NULL,NULL,NULL),
('ticketing','demo-dashboard:ticketing:005','DEMO-DASHBOARDS','RECONCILE','DEMO-EVENT-005','DEMO-INC-005',CURRENT_TIMESTAMP - INTERVAL '35 minutes',NULL,NULL,NULL),
('ticketing','demo-dashboard:ticketing:006','DEMO-DASHBOARDS','CREATED','DEMO-EVENT-006','DEMO-INC-006',CURRENT_TIMESTAMP - INTERVAL '42 minutes',NULL,NULL,NULL),
('gnm','demo-dashboard:gnm:001','DEMO-DASHBOARDS','OPEN_CONFIRMED','DEMO-EVENT-001','DEMO-GNM-001',CURRENT_TIMESTAMP - INTERVAL '7 minutes',NULL,NULL,NULL),
('gnm','demo-dashboard:gnm:002','DEMO-DASHBOARDS','OPEN_CONFIRMED','DEMO-EVENT-002','DEMO-GNM-002',CURRENT_TIMESTAMP - INTERVAL '14 minutes',NULL,NULL,NULL),
('gnm','demo-dashboard:gnm:003','DEMO-DASHBOARDS','PENDING','DEMO-EVENT-003','DEMO-GNM-003',CURRENT_TIMESTAMP - INTERVAL '21 minutes',NULL,NULL,NULL),
('gnm','demo-dashboard:gnm:004','DEMO-DASHBOARDS','FAILED','DEMO-EVENT-004','DEMO-GNM-004',CURRENT_TIMESTAMP - INTERVAL '28 minutes',NULL,NULL,NULL),
('gnm','demo-dashboard:gnm:005','DEMO-DASHBOARDS','CLOSED_CONFIRMED','DEMO-EVENT-005','DEMO-GNM-005',CURRENT_TIMESTAMP - INTERVAL '35 minutes',NULL,NULL,NULL),
('gnm','demo-dashboard:gnm:006','DEMO-DASHBOARDS','RETRY','DEMO-EVENT-006','DEMO-GNM-006',CURRENT_TIMESTAMP - INTERVAL '42 minutes',NULL,NULL,NULL),
('cacf','demo-dashboard:cacf:001','DEMO-DASHBOARDS','RECEIVED','DEMO-EVENT-001','DEMO-RUN-001',CURRENT_TIMESTAMP - INTERVAL '7 minutes',NULL,NULL,NULL),
('cacf','demo-dashboard:cacf:002','DEMO-DASHBOARDS','SUBMITTED','DEMO-EVENT-002','DEMO-RUN-002',CURRENT_TIMESTAMP - INTERVAL '14 minutes',NULL,NULL,NULL),
('cacf','demo-dashboard:cacf:003','DEMO-DASHBOARDS','IN_PROGRESS','DEMO-EVENT-003','DEMO-RUN-003',CURRENT_TIMESTAMP - INTERVAL '21 minutes',NULL,NULL,NULL),
('cacf','demo-dashboard:cacf:004','DEMO-DASHBOARDS','COMPLETED','DEMO-EVENT-004','DEMO-RUN-004',CURRENT_TIMESTAMP - INTERVAL '28 minutes',NULL,NULL,'REMEDIATED'),
('cacf','demo-dashboard:cacf:005','DEMO-DASHBOARDS','COMPLETED','DEMO-EVENT-005','DEMO-RUN-005',CURRENT_TIMESTAMP - INTERVAL '35 minutes',NULL,NULL,'UNKNOWN'),
('cacf','demo-dashboard:cacf:006','DEMO-DASHBOARDS','TIMED_OUT','DEMO-EVENT-006','DEMO-RUN-006',CURRENT_TIMESTAMP - INTERVAL '42 minutes',NULL,NULL,'TIMEOUT'),
('cacf','demo-dashboard:cacf:007','DEMO-DASHBOARDS','SUBMISSION_FAILED','DEMO-EVENT-007','DEMO-RUN-007',CURRENT_TIMESTAMP - INTERVAL '49 minutes',NULL,NULL,NULL)
ON CONFLICT (domain,id) DO NOTHING;
CREATE OR REPLACE VIEW dashboard_read.events AS
SELECT event_key::text AS id, tenant::text, lifecycle_status::text AS status,
       event_id::text, ticket_number::text AS reference, last_updated_at AS updated_at,
       effective_severity AS severity, tally, NULL::text AS outcome
FROM event_management.event_state
UNION ALL
SELECT id,tenant,status,event_id,reference,updated_at,severity,tally,outcome
FROM dashboard_demo.records WHERE domain = 'events';
CREATE OR REPLACE VIEW dashboard_read.ticketing AS
SELECT event_key::text AS id, tenant::text, servicenow_status::text AS status,
       event_id::text, ticket_number::text AS reference, last_updated_at AS updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, NULL::text AS outcome
FROM event_management.event_state
WHERE servicenow_status <> 'NOT_REQUIRED' OR ticket_number IS NOT NULL
UNION ALL
SELECT id,tenant,status,event_id,reference,updated_at,severity,tally,outcome
FROM dashboard_demo.records WHERE domain = 'ticketing';
CREATE OR REPLACE VIEW dashboard_read.gnm AS
SELECT event_key::text AS id, tenant::text, gnm_status::text AS status,
       event_id::text, notification_id::text AS reference, last_updated_at AS updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, NULL::text AS outcome
FROM event_management.event_state
WHERE gnm_status <> 'NOT_REQUIRED' OR notification_id IS NOT NULL
UNION ALL
SELECT id,tenant,status,event_id,reference,updated_at,severity,tally,outcome
FROM dashboard_demo.records WHERE domain = 'gnm';
CREATE OR REPLACE VIEW dashboard_read.cacf AS
SELECT execution_id::text AS id, customer_code::text AS tenant, state::text AS status,
       event_id::text, provider_execution_id::text AS reference, updated_at,
       NULL::integer AS severity, NULL::bigint AS tally, outcome::text
FROM event_management.automation_execution
UNION ALL
SELECT id,tenant,status,event_id,reference,updated_at,severity,tally,outcome
FROM dashboard_demo.records WHERE domain = 'cacf';
COMMIT;
