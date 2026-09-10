#!/usr/bin/env python3
"""Read-only ESS runtime evidence, restricted to schema, health, mappings and consumer lag."""
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/os-05-ess/ESS-01'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    def run(args):
        return subprocess.check_output(args, cwd=ROOT, text=True, timeout=45).strip()
    def sql(query):
        return subprocess.run(['docker', 'exec', '-i', 'event-postgres', 'sh', '-c',
            'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
            input=query, capture_output=True, check=True, text=True, timeout=30).stdout.strip()
    evidence = {}
    for name, path in [('health', 'http://127.0.0.1:8084/health'),
                       ('mapping', 'http://127.0.0.1:9200/events-current/_mapping'),
                       ('aliases', 'http://127.0.0.1:9200/events-current/_alias')]:
        with urllib.request.urlopen(path, timeout=15) as response:
            evidence[name] = json.load(response)
    evidence['container'] = json.loads(run(['docker', 'inspect', '--format',
        '{"image":"{{.Image}}","status":"{{.State.Status}}","oom":{{.State.OOMKilled}},"exitCode":{{.State.ExitCode}},"restarts":{{.RestartCount}}}',
        'event-state-service']))
    evidence['columns'] = json.loads(sql("SELECT json_agg(t) FROM (SELECT table_name,column_name,data_type,is_nullable,column_default,character_maximum_length FROM information_schema.columns WHERE table_schema='event_management' AND table_name IN ('event_state','processed_integration_result') ORDER BY table_name,ordinal_position) t"))
    evidence['indexes'] = json.loads(sql("SELECT json_agg(t) FROM (SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='event_management' AND tablename IN ('event_state','processed_integration_result')) t"))
    evidence['constraints'] = json.loads(sql("SELECT json_agg(t) FROM (SELECT conname,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid IN ('event_management.event_state'::regclass,'event_management.processed_integration_result'::regclass)) t"))
    evidence['group'] = run(['docker', 'exec', 'event-kafka', '/opt/kafka/bin/kafka-consumer-groups.sh',
                           '--bootstrap-server', 'kafka:29092', '--group', 'event-state-service', '--describe'])
    (OUT / 'runtime-baseline.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(OUT / 'runtime-baseline.json')


if __name__ == '__main__':
    main()
