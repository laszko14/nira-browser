'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const file=path.join(root,'docs/ai/GECKO_DEVICE_TEST_PLAN.md');
const body=fs.readFileSync(file,'utf8');
const cases={
  'ZC-AUD-002':'BLOCKED / SUSPECTED',
  'ZC-AUD-030':'OPEN / SUSPECTED',
  'ZC-AUD-031':'PARTIAL',
  'ZC-AUD-043':'CONDITIONAL_GUARD / LIKELY'
};
function checkPlan(content) {
  const sections=[...content.matchAll(/^### (ZC-AUD-\d{3}) — ([^\n]+)\n([\s\S]*?)(?=^### |^## |$(?![\s\S]))/gm)];
  assert.equal(sections.length,4,'exactly four unresolved device cases');
  assert.deepEqual(sections.map(x=>x[1]).sort(),Object.keys(cases).sort());
  for(const [,id,status,detail] of sections){
    assert.equal(status,cases[id],'historical status must not be silently rewritten');
    assert.match(detail,/\*\*CORE 19B:\*\*/);
    assert.match(detail,/\*\*Device:\*\* NOT_TESTED/);
    assert.match(detail,/\*\*Oracle:\*\*/);
    assert.match(detail,/\*\*Wejście:\*\*/);
    assert.match(detail,/\*\*Obserwuj:\*\*/);
  }
  for(const phrase of [
    'tools/gecko-fixture-g3c/NOT_TESTED_TEMPLATE.json',
    'tools/gecko-fixture-g3c/check-evidence.cjs',
    'tools/gecko-fixture-g2/site/video.html',
    'tools/gecko-fixture-g2/site/index.html',
    'tools/gecko-fixture-g2/site/popup.html',
    '120 Hz','Android 15','NOT_TESTED','cold restart',
    'osobnej zgodzie','realnym Gecko','PLAN_PREPARED'
  ])assert.ok(content.includes(phrase),'missing evidence/safety: '+phrase);
  assert.match(content,/1195[^\n]*nie uruchamiać/);
  assert.match(content,/\*\*nie usuwaj niczego\*\*/);
  assert.match(content,/DEVICE_VERIFIED = NO, APK_VERIFIED = NO/);
}
test('stage 7 device plan: complete scoped scenarios without claiming phone PASS',()=>{
  checkPlan(body);
  for(const relative of [
    'tools/gecko-fixture-g3c/NOT_TESTED_TEMPLATE.json',
    'tools/gecko-fixture-g3c/check-evidence.cjs',
    'tools/gecko-fixture-g2/site/video.html',
    'tools/gecko-fixture-g2/site/index.html',
    'tools/gecko-fixture-g2/site/popup.html'
  ])assert.ok(fs.statSync(path.join(root,relative)).isFile(),'missing existing fixture: '+relative);
});
test('stage 7 mutation: changed historical status or fabricated device PASS fail closed',()=>{
  const wrong=body.replace('### ZC-AUD-031 — PARTIAL','### ZC-AUD-031 — FIXED');
  assert.notEqual(wrong,body);
  assert.throws(()=>checkPlan(wrong),assert.AssertionError);
  const fake=body.replace('**Device:** NOT_TESTED','**Device:** PASS');
  assert.notEqual(fake,body);
  assert.throws(()=>checkPlan(fake),assert.AssertionError);
  const missing=body.replace('### ZC-AUD-002 — BLOCKED / SUSPECTED','### MISSING — BLOCKED / SUSPECTED');
  assert.notEqual(missing,body);
  assert.throws(()=>checkPlan(missing),assert.AssertionError);
});
