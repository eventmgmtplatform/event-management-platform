"""Bounded latest snapshots from the product's read-only administration APIs."""
import datetime
import json
import logging
import os
import time
import urllib.request
from pathlib import Path

INDEX = 'product-observability-current'
SEARCH = os.getenv('OPENSEARCH_URL', 'http://opensearch:9200')
PRODUCT = os.getenv('PRODUCT_API_URL', 'http://frontend-management-api:8093')
FIELDS = {
    'service': ('id', 'name', 'category', 'status', 'reason', 'version', 'port', 'runtime', 'health', 'restartCount'),
    'api': ('id', 'service', 'endpoint', 'status', 'httpStatus', 'latencyMs', 'checkedAt'),
}

def request(url, method='GET', data=None, content_type='application/json'):
    body = data.encode() if isinstance(data, str) else json.dumps(data).encode() if data is not None else None
    with urllib.request.urlopen(urllib.request.Request(url, data=body, method=method,
            headers={'Content-Type': content_type}), timeout=10) as response:
        return json.load(response)


def documents(kind, snapshot, now):
    rows = snapshot['services' if kind == 'service' else 'apis']
    # Allowlist: never index environment variables, tokens or raw configuration.
    return [dict({k: row[k] for k in FIELDS[kind] if k in row},
                 kind=kind, **{'@timestamp': snapshot['observedAt']}) for row in rows]


def collect():
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    docs = []
    for kind, route in [('service', 'platform'), ('api', 'apis')]:
        try:
            docs.extend(documents(kind, request(PRODUCT + '/api/administration/' + route), now))
            status = 'UP'
        except (OSError, ValueError, KeyError, TypeError):
            status = 'UNREACHABLE'
            logging.warning('Product source unavailable: %s', route)
        docs.append({'kind': 'collector', 'id': route, 'status': status, '@timestamp': now})
    body = ''.join(json.dumps({'index': {'_index': INDEX, '_id': d['kind'] + ':' + d['id']}}) + '\n' + json.dumps(d) + '\n' for d in docs)
    result = request(SEARCH + '/_bulk?refresh=wait_for', 'POST', body, 'application/x-ndjson')
    if result.get('errors'):
        raise RuntimeError('Snapshot indexing failed')
    Path('/tmp/last-success').write_text(str(time.time()))
    logging.info('Indexed %s observations', len(docs))


def main():
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    mapping = json.loads(Path('/app/index-template.json').read_text())
    while True:
        try:
            request(SEARCH + '/_index_template/product-observability', 'PUT', mapping)
            break
        except (OSError, ValueError):
            logging.warning('Waiting for OpenSearch')
            time.sleep(30)
    while True:
        try:
            collect()
        except (OSError, ValueError, RuntimeError):
            logging.exception('Collection failed')
        time.sleep(30)

if __name__ == '__main__':
    main()
