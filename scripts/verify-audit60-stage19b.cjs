#!/usr/bin/env node
'use strict';

// Stage 19B is a documentation/CI evidence gate, NOT a physical Gecko/device test.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const register = read('docs/audits/zavro-clean-repair/REPAIR_REGISTER_19.md');
const report = read('docs/audits/zavro-clean-repair/FINAL_AUDIT_19B_60.md');

const regRows = register.split('\n').filter((x) => /^\| ZC-AUD-\d{3} \|/.test(x));
const matrix = report.split('## Macierz 60/60\n')[1]?.split('\n## Ustalenia bez pełnego zamknięcia')[0];
assert.ok(matrix, 'Missing bounded 60/60 matrix');
const reportRows = matrix.split('\n').filter((x) => /^\| ZC-AUD-\d{3} \|/.test(x));
const id = (x) => Number(x.match(/^\| ZC-AUD-(\d{3}) \|/)[1]);
const complete = (a) => a.length === 60 && [...new Set(a.map(id))].sort((x, y) => x - y).join(',') === Array.from({ length: 60 }, (_, i) => i + 1).join(',');
assert.ok(complete(regRows), 'Registry IDs are not exactly 001..060');
assert.ok(complete(reportRows), 'Report IDs are not exactly 001..060');

const verdict = new Map();
const counts = new Map();
const regById = new Map(regRows.map(x => [id(x), x]));
for (const line of reportRows) {
  const parts = line.split('|').map(x => x.trim());
  const n = id(line), v = parts[2], stage = parts[3];
  assert.ok(['PASS', 'FAIL', 'BLOCKED', 'NOT_TESTED'].includes(v), 'Invalid verdict: ' + n);
  assert.ok(regById.get(n).includes('| ' + stage + ' |'), 'Stage mismatch for ' + n);
  assert.ok(/https:\/\/github\.com\/laszko14\/nira-browser\/blob\/[a-f0-9]{40}\/docs\/audits\/zavro-clean-repair\/REPAIR_REGISTER_19\.md#L\d+/.test(line), 'Stage evidence missing: ' + n);
  for (const match of line.matchAll(/\[test\]\(https:\/\/github\.com\/laszko14\/nira-browser\/blob\/[a-f0-9]{40}\/(app\/src\/[^)]+)\)/g)) {
    assert.ok(fs.existsSync(path.join(root, match[1])), 'Missing test: ' + match[1]);
  }
  verdict.set(n, v);
  counts.set(v, (counts.get(v) || 0) + 1);
}
assert.equal(counts.get('PASS'), 56);
assert.equal(counts.get('FAIL'), 1);
assert.equal(counts.get('BLOCKED'), 2);
assert.equal(counts.get('NOT_TESTED'), 1);
assert.equal(verdict.get(2), 'BLOCKED');
assert.equal(verdict.get(30), 'BLOCKED');
assert.equal(verdict.get(31), 'FAIL');
assert.equal(verdict.get(43), 'NOT_TESTED');
assert.ok(/ZC-AUD-002 — BLOCKED \/ REAL_GECKO_REQUIRED/.test(report));
assert.ok(/ZC-AUD-030 — OPEN/.test(report));
assert.ok(/ZC-AUD-031 — PARTIAL/.test(report));
assert.ok(/ZC-AUD-043 — CONDITIONAL_GUARD/.test(report));
assert.ok(report.includes('Android 15/Gecko = NOT_TESTED'), 'Device caveat missing');
assert.ok(report.includes('nie oznacza PASS na telefonie') || report.includes('Nie oznacza PASS na telefonie'));
assert.ok(/(?:Brak|Bez) merge do/.test(report), 'No-merge scope missing');
const confidence = new Map();
for (const line of regRows) {
  const c = line.split('|').map(x => x.trim())[3];
  confidence.set(c, (confidence.get(c) || 0) + 1);
}
assert.deepEqual(Object.fromEntries(confidence), { CONFIRMED: 53, SUSPECTED: 3, LIKELY: 4 });
console.log('PASS: 60/60 unique IDs; stages and linked test files; 56 PASS CORE, 1 FAIL closure, 2 BLOCKED, 1 NOT_TESTED; Gecko/device exclusions preserved.');
