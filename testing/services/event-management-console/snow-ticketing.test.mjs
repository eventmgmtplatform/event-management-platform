import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../../../services/event-management-console/package.json', import.meta.url));
const ts = require('typescript');
const source = readFileSync(new URL('../../../services/event-management-console/src/modules/ticketing/services/snow-ticketing.adapter.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { parseIncident, snowTicketingRepository: repo } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const seed = JSON.parse(readFileSync(new URL('../../mocks/servicenow-console/seed.json', import.meta.url), 'utf8'));
test('seed preserves all twelve incidents and six statuses', () => {
  const tickets = seed.map(parseIncident);
  assert.equal(tickets.length, 12);
  assert.equal(new Set(tickets.map(t => t.status)).size, 6);
  assert.throws(() => parseIncident({ ...seed[0], state: '999' }));
});
test('search goes to the same-origin isolated API with the selected state', async t => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    assert.ok(url.startsWith('/api/now/table/incident?'));
    const params = new URL(url, 'http://localhost').searchParams;
    assert.equal(params.get('sysparm_query'), 'numberLIKEINC0019284^state=1');
    return Response.json({ result: [seed[0]] });
  });
  const result = await repo.search({ number: 'inc0019284', status: 'Open' }, new AbortController().signal);
  assert.equal(result[0].id, 'INC0019284');
});
test('API failure does not silently load browser seed data', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 503 }));
  await assert.rejects(repo.search({ number: '', status: 'All' }, new AbortController().signal));
});
