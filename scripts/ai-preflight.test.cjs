'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {inspect,selectedPaths,SCOPE}=require('./ai-preflight.cjs');
const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');
const branch='feat/zavro-clean-v3-20261101',sha='75d59b0ba47269b84e4e027556fd59dcbee66287';
const source=p=>fs.readFileSync(path.join(root,p),'utf8');
const run=(override={},read=source)=>inspect({branch,sha,checkoutBranch:branch,checkoutSha:sha,
  scope:'CI_TOOLING',...override},read);
test('read-only source snapshot verifies all required bytes and scoped docs',()=>{
 const x=run();assert.equal(x.status,'SOURCE_SNAPSHOT_VERIFIED');
 assert.equal(x.sha256['AGENTS.md'].length,64);
 assert.ok(x.paths.includes('docs/AI_HANDOFF.md'));
 assert.ok(x.paths.includes('docs/ai/ERROR_CASES.json'));
 assert.ok(!x.paths.includes('docs/ai/APK_RELEASE.md'));
});
test('scope selector is deterministic, no APK on default',()=>{
 assert.equal(new Set(selectedPaths('VIDEO_SEARCH')).size,selectedPaths('VIDEO_SEARCH').length);
 for(const name of Object.keys(SCOPE))assert.ok(selectedPaths(name).includes('AGENTS.md'));
 assert.ok(!selectedPaths('DEFAULT').includes('docs/ai/APK_RELEASE.md'));
 assert.ok(selectedPaths('APK').includes('docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md'));
 assert.throws(()=>selectedPaths('ANY'),/PREFLIGHT_BLOCKED/);
});
test('stale checkout SHA and branch fail closed',()=>{
 assert.throws(()=>run({checkoutSha:'0'.repeat(40)}),/checkout branch\/SHA mismatch/);
 assert.throws(()=>run({checkoutBranch:'main'}),/checkout branch\/SHA mismatch/);
 assert.throws(()=>run({sha:'short'}),/invalid branch or SHA/);
});
test('missing instruction or mismatched handoff fails',()=>{
 assert.throws(()=>run({},p=>p==='AGENTS.md'?null:source(p)),/missing source AGENTS/);
 assert.throws(()=>run({},p=>p==='docs/AI_HANDOFF.md'?
   source(p).replace('branch: '+branch,'branch: feat/zavro-clean-other'):source(p)),/handoff/);
 assert.throws(()=>run({},p=>p==='docs/ai/STARTUP_PREFLIGHT.md'?'invalid preflight':source(p)),/preflight policy/);
});
test('no G1 bypass for missing task-specific document',()=>{
 assert.throws(()=>run({},p=>p==='docs/ai/ERROR_CASES.json'?null:source(p)),/missing source/);
});
