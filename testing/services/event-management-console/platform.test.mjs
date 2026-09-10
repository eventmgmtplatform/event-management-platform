import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../../../services/event-management-console/package.json', import.meta.url));
const ts = require('typescript');
const source = readFileSync(new URL('../../../services/event-management-console/src/modules/administration/platform.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { parseSnapshot, demoSnapshot, readPlatform } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
test('accepts valid snapshots and an empty inventory', () => {
  assert.equal(parseSnapshot(demoSnapshot()).services.length, 11);
  assert.deepEqual(parseSnapshot({ observedAt: new Date().toISOString(), services: [] }).services, []);
});
test('rejects malformed metadata, duplicate IDs and unsupported health states', () => {
  for (const invalid of [null, {}, { observedAt: 'invalid', services: [] }]) assert.throws(() => parseSnapshot(invalid));
  for (const patch of [{ status: 'toString' }, { status: 'UP' }, { port: 65536 }, { port: 2.5 }, { name: '' }, { version: 1 }]) {
    const value = demoSnapshot(); Object.assign(value.services[0], patch);
    assert.throws(() => parseSnapshot(value));
  }
  const value = demoSnapshot(); value.services.push(value.services[0]);
  assert.throws(() => parseSnapshot(value));
});
test('API failures never fall back to simulated data', async (t) => {
  for (const response of [new Response('error', { status: 503 }), new Response('<html/>', { headers: { 'content-type': 'text/html' } }), Response.json({ invalid: true })]) {
    t.mock.method(globalThis, 'fetch', async () => response);
    await assert.rejects(() => readPlatform(new AbortController().signal));
    t.mock.restoreAll();
  }
  t.mock.method(globalThis, 'fetch', async () => Response.json(demoSnapshot()));
  assert.equal((await readPlatform(new AbortController().signal)).services.length, 11);
});
