\set ON_ERROR_STOP on
BEGIN;
CREATE TABLE IF NOT EXISTS event_processor.lifecycle (
 tenant TEXT NOT NULL,
 cycle_id TEXT NOT NULL,
 processing_id VARCHAR(64) NOT NULL REFERENCES event_processor.processing_record(processing_id),
 document JSONB NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant,cycle_id),
 CHECK(document->>'tenant'=tenant AND document->>'cycleId'=cycle_id)
);
CREATE TABLE IF NOT EXISTS event_processor.lifecycle_result (
 result_id TEXT PRIMARY KEY,
 tenant TEXT NOT NULL,
 cycle_id TEXT NOT NULL,
 command_id TEXT NOT NULL,
 payload JSONB NOT NULL,
 disposition TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant,cycle_id) REFERENCES event_processor.lifecycle(tenant,cycle_id)
);
CREATE INDEX IF NOT EXISTS lifecycle_result_cycle ON event_processor.lifecycle_result(tenant,cycle_id);
CREATE TABLE IF NOT EXISTS event_processor.lifecycle_rejection (
 topic TEXT NOT NULL, partition_id INTEGER NOT NULL, offset_id BIGINT NOT NULL,
 reason TEXT NOT NULL, payload_hash TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(topic,partition_id,offset_id)
);
COMMIT;
