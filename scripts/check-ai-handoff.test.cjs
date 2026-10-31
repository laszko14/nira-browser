'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const cp = require('node:child_process');
const path = require('node:path');

const root = path.join(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const handoff = read('docs/AI_HANDOFF.md');
const template = read('docs/ai/TRANSFER_TEMPLATE.md');
const archivePath = 'docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md';
const originalBlob = 'ebb553e63d69158da4f4fe89c4e5ae673bbe53f6';

function checkpoint(text) {
  const blocks = [...text.matchAll(/```yaml\n([\s\S]*?)\n```/g)];
  assert.equal(blocks.length, 1, 'single active checkpoint block');
  const fields = Object.create(null);
  for (const line of blocks[0][1].split('\n')) {
    const match = line.match(/^([a-z_]+):(?:\s+(.*))?$/);
    if (match) {
      assert.ok(!Object.hasOwn(fields, match[1]), 'duplicate key ' + match[1]);
      fields[match[1]] = (match[2] ?? '').trim();
    }
  }
  return fields;
}

test('legacy handoff is byte-identical to the original Git blob', () => {
  const blob = cp.execFileSync('git', ['hash-object', archivePath], {cwd: root, encoding: 'utf8'}).trim();
  assert.equal(blob, originalBlob);
  // The exact original Git blob ID is verified above. Shallow CI clones
  // need not contain the historical parent commit for this invariant.
  assert.ok(fs.statSync(path.join(root, archivePath)).size > 300000);
});

test('fresh chat reconstructs the task from the short current handoff alone', () => {
  assert.ok(Buffer.byteLength(handoff, 'utf8') < 8000, 'current handoff must stay short');
  const data = checkpoint(handoff);
  const required = ['task_id', 'workflow_mode', 'repo', 'product_line', 'branch', 'base_sha', 'observed_head_sha', 'status', 'acceptance_source', 'required_ci', 'device', 'apk', 'next_action'];
  for (const key of required) assert.ok(data[key], 'missing ' + key);
  assert.match(data.task_id, /^[A-Z][A-Z0-9-]{3,}$/);
  assert.ok(['staged', 'autonomous_task'].includes(data.workflow_mode));
  assert.equal(data.repo, 'laszko14/nira-browser');
  assert.match(data.branch, /^improve\/zavro-clean-chatgpt-github-process-/);
  for (const k of ['base_sha','observed_head_sha']) assert.match(data[k], /^[0-9a-f]{40}$/);
  assert.match(data.required_ci, /process-docs/);
  assert.match(handoff, /blockers:/);
  assert.match(handoff, /changed_paths:/);
  assert.match(handoff, /verified_before_push:/);
  assert.match(handoff, /evidence_links:/);
  assert.match(handoff, /ai\/archive\/AI_HANDOFF_LEGACY_2026-11-01\.md/);
  assert.match(handoff, /CI_PENDING.*migawką|status: CI_PENDING/);
  assert.match(handoff, /ETAP [1-9]\/9/);
});

test('handoff template supports a next-chat restart without copying the archive', () => {
  for (const placeholder of ['task_id', 'workflow_mode', 'branch', 'observed_head_sha', 'base_sha', 'acceptance_source', 'required_ci', 'next_action', 'blockers']) {
    assert.ok(template.includes('['+placeholder+']'), 'missing transfer field '+placeholder);
  }
  assert.match(template, /porównaj aktualny HEAD/);
  assert.match(template, /Bez merge i APK/);
  assert.doesNotMatch(handoff, /# P16\/16 —/);
});
