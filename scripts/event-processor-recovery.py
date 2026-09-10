#!/usr/bin/env python3
"""Fresh/upgrade/backup/restore tests using disposable databases in the CACF lab."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ['docker', 'compose', '-p', 'cacf-certification', '-f',
           str(ROOT / 'testing/environments/cacf.compose.yml'), 'exec', '-T', 'postgres']


def call(args, *, data=None):
    return subprocess.run(args, input=data, capture_output=True, check=True, timeout=120).stdout


def sql(database, text):
    return call(COMPOSE + ['psql', '-X', '-A', '-t', '-v', 'ON_ERROR_STOP=1',
                          '-U', 'cacf_test', '-d', database], data=text.encode()).decode().strip()


def main():
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    evidence = ROOT / 'evidence/os-02-event-processor/recovery' / stamp
    evidence.mkdir(parents=True)
    stem = 'ep_recovery_' + uuid.uuid4().hex[:12]
    source, target = stem + '_source', stem + '_restored'
    created = []
    report = {'status': 'RUNNING', 'scope': 'isolated PostgreSQL + production adapters, mock Kafka transport',
              'sourceDatabase': source, 'restoredDatabase': target, 'checks': [], 'targets': {'RPO': 'PENDING', 'RTO': 'PENDING'}}
    try:
        for database in (source, target):
            call(COMPOSE + ['createdb', '-U', 'cacf_test', database])
            created.append(database)
        baseline = (ROOT / 'infrastructure/postgres/init/009-event-processor.sql').read_text()
        upgrade = (ROOT / 'infrastructure/postgres/init/010-processor-outbox-recovery.sql').read_text()
        sql(source, baseline)
        sql(source, """
            INSERT INTO event_processor.processing_record
                (processing_id,input_hash,event_id,tenant,evidence)
            VALUES ('restore-pending','restore-hash','restore-event','restore-tenant','{"baseline":true}');
            INSERT INTO event_processor.output_outbox
                (message_id,processing_id,topic,message_key,payload)
            VALUES ('restore-pending','restore-pending','events.normalized','restore-key','{"source":"recovery-test"}');
        """)
        original = sql(source, "SELECT payload::text FROM event_processor.output_outbox")
        sql(source, upgrade)
        sql(source, upgrade)
        registry = (ROOT / 'infrastructure/postgres/init/011-processor-rule-registry.sql').read_text()
        sql(source, registry)
        sql(source, registry)
        assert sql(source, 'SELECT payload::text FROM event_processor.output_outbox') == original
        assert sql(source, 'SELECT attempts FROM event_processor.output_outbox') == '0'
        # Old writer remains compatible with new additive columns through defaults.
        sql(source, """
            INSERT INTO event_processor.processing_record
                (processing_id,input_hash,event_id,tenant,evidence)
            VALUES ('old-writer','old-hash','old-event','restore-tenant','{}');
            INSERT INTO event_processor.output_outbox
                (message_id,processing_id,topic,message_key,payload,published_at)
            VALUES ('old-writer','old-writer','events.normalized','old-key','{}',now());
            UPDATE event_processor.output_outbox SET attempts=1,last_attempt_at=now(),
                next_attempt_at='2000-01-01T00:00:00Z',last_error_code='PUBLICATION_UNCONFIRMED'
            WHERE message_id='restore-pending';
        """)
        report['checks'].extend(['fresh schema 009', 'upgrade 010 preserves pending payload',
                                  'repeat migration 010', 'old writer compatible with new columns'])
        with (evidence / 'prepare-rules-test.log').open('w') as log:
            subprocess.run(['mvn', '-o', '-B', '-f', str(ROOT / 'services/event-processor/pom.xml'),
                            '-Dtest=RestoreReplayIT#prepareVersionsForBackup',
                            '-Dprocessor.restore.jdbc.url=jdbc:postgresql://127.0.0.1:15439/' + source, 'test'],
                           cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
        report['checks'].append('migration 011 repeatable; production registry creates and activates immutable versions')
        snapshot_query = """
            SELECT json_build_object(
                'records',(SELECT json_agg(p ORDER BY processing_id) FROM event_processor.processing_record p),
                'outbox',(SELECT json_agg(o ORDER BY message_id) FROM event_processor.output_outbox o),
                'rules',(SELECT json_agg(d ORDER BY tenant,rule_id) FROM event_processor.rule_definition d),
                'versions',(SELECT json_agg(v ORDER BY tenant,rule_id,version) FROM event_processor.rule_version v),
                'changes',(SELECT json_agg(h ORDER BY tenant,rule_id,revision) FROM event_processor.rule_change h))::text
        """
        before = json.loads(sql(source, snapshot_query))
        (evidence / 'before-backup.json').write_text(json.dumps(before, indent=2) + '\n')
        started = time.monotonic()
        backup = call(COMPOSE + ['pg_dump', '-U', 'cacf_test', '-d', source,
                                '--format=custom', '--schema=event_processor', '--no-owner', '--no-privileges'])
        report['backupSeconds'] = round(time.monotonic() - started, 3)
        (evidence / 'processor.dump').write_bytes(backup)
        report['backupSha256'] = hashlib.sha256(backup).hexdigest()
        started = time.monotonic()
        call(COMPOSE + ['pg_restore', '-U', 'cacf_test', '-d', target,
                        '--exit-on-error', '--no-owner', '--no-privileges'], data=backup)
        report['restoreSeconds'] = round(time.monotonic() - started, 3)
        after = json.loads(sql(target, snapshot_query))
        assert before == after, 'Restored state, evidence, attempts or pending payload differs'
        (evidence / 'after-restore.json').write_text(json.dumps(after, indent=2) + '\n')
        report['checks'].append('backup/restore exact records, outbox, rule definitions, versions and change history')
        with (evidence / 'restore-replay-test.log').open('w') as log:
            subprocess.run(['mvn', '-o', '-B', '-f', str(ROOT / 'services/event-processor/pom.xml'),
                            '-Dtest=RestoreReplayIT#restoredPendingOutputAndReplayUseProductionAdapters',
                            '-Dprocessor.restore.jdbc.url=jdbc:postgresql://127.0.0.1:15439/' + target, 'test'],
                           cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
        report['checks'].append('production store replay, dispatcher and rule snapshot recover with original identities and checksums')
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['errorType'] = type(error).__name__
        raise
    finally:
        # Drop only exact databases this invocation successfully created; never runtime databases.
        cleanup_errors = []
        for database in reversed(created):
            try:
                call(COMPOSE + ['dropdb', '-U', 'cacf_test', database])
            except Exception as error:
                cleanup_errors.append({'database': database, 'errorType': type(error).__name__})
        report['cleanupErrors'] = cleanup_errors
        if cleanup_errors:
            report['status'] = 'FAIL'
        (evidence / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        (evidence / 'SHA256SUMS').write_text(''.join(
            hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + f.name + '\n'
            for f in sorted(evidence.iterdir()) if f.is_file() and f.name != 'SHA256SUMS'))
        print('Evidence:', evidence, flush=True)
        if cleanup_errors:
            raise RuntimeError('Disposable database cleanup failed; inspect report')


if __name__ == '__main__':
    main()
