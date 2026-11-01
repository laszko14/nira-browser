'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {validate,summarize,CAUSES,FIELDS}=require('./ai-pilot-metrics.cjs');
const source=JSON.parse(fs.readFileSync('docs/ai/PILOT_16.json','utf8'));
const copy=()=>JSON.parse(JSON.stringify(source));
function complete(){const o=copy(),r=o.records[0];r.state='FOLLOWUP_COMPLETE';
  r.task_ref='ZC-EXAMPLE-1';r.scope='JS_PROTOCOL';r.complexity='MEDIUM';
  r.started_at='2026-11-02T10:00:00.000Z';r.completed_at='2026-11-03T10:00:00.000Z';
  r.followup_due_at='2026-11-17T10:00:00.000Z';r.followup_checked_at='2026-11-18T10:00:00.000Z';
  r.final_sha='b'.repeat(40);
  r.evidence_links=['https://github.com/laszko14/nira-browser/commit/'+'b'.repeat(40)];
  for(const k of FIELDS)r.metrics[k]=k==='reopened'?false:0;
  for(const k of CAUSES)r.metrics.correction_causes[k]=0;
  return o;}
test('T01 is active without inventing device results or success',()=>{
  assert.equal(source.records[0].state,'ACTIVE');
  assert.equal(source.records[0].task_ref,'T01-GECKO-V1196');
  assert.equal(source.records[0].scope,'GECKO');
  assert.equal(source.records[0].complexity,'MEDIUM');
  assert.ok(source.records[0].started_at);
  assert.ok(FIELDS.every(k=>source.records[0].metrics[k]===null));
  assert.ok(CAUSES.every(k=>source.records[0].metrics.correction_causes[k]===null));
  assert.ok(source.records.slice(1).every(r=>r.state==='NOT_STARTED'));
  assert.deepEqual(validate(source),{started:1,finished:0});
  const s=summarize(source);
  assert.equal(s.status,'NO_RESULTS_YET');
  assert.equal(s.sample_target,16);
  assert.equal(s.totals_for_completed_followups_only,null);
  assert.equal(s.improved_quality,'NOT_ESTABLISHED');
});
test('active task cannot declare unverified completion or silent zeroes',()=>{
  const o=copy();
  o.records[0].completed_at='2026-11-01T12:00:00.000Z';
  assert.throws(()=>validate(o),/unearned completion/);
  const p=copy();
  p.records[0].metrics.regressions=0;
  assert.equal(summarize(p).improved_quality,'NOT_ESTABLISHED');
  assert.equal(summarize(p).completed_14_day_followups,0);
});
test('completed measured row accepts real zero and sums only eligible tasks',()=>{
  const o=complete(),s=summarize(o);
  assert.equal(s.status,'PARTIAL_PILOT');
  assert.equal(s.started,1);assert.equal(s.completed_14_day_followups,1);
  assert.equal(s.totals_for_completed_followups_only.regressions,0);
  assert.equal(s.totals_for_completed_followups_only.correction_rounds,0);
});
test('no false ZERO before data collection and no invented followup status',()=>{
  let o=copy();o.records[0].metrics.user_interventions=0;
  assert.throws(()=>validate(o),/NOT_STARTED/);
  o=complete();o.records[0].followup_checked_at='2026-11-04T00:00:00.000Z';
  assert.throws(()=>validate(o),/before observation window/);
  o=complete();o.records[0].evidence_links=[];
  assert.throws(()=>validate(o),/source links/);
  o=complete();o.records[0].metrics.messages_chars=null;
  assert.throws(()=>validate(o),/missing measures/);
});
test('cause accounting, negative counts and untrusted links fail closed',()=>{
  let o=complete();o.records[0].metrics.correction_rounds=2;
  assert.throws(()=>validate(o),/do not sum/);
  o=complete();o.records[0].metrics.avoidable_ci_runs=-1;
  assert.throws(()=>validate(o),/invalid/);
  o=complete();o.records[0].evidence_links=['https://example.com/token'];
  assert.throws(()=>validate(o),/public repository URL/);
  o=complete();o.records[1].slot='T01';
  assert.throws(()=>validate(o),/unique and ordered/);
});
test('pilot status never means causality, even after 12 followups',()=>{
  const o=complete(),ready=o.records[0];
  for(let i=1;i<12;i++){const r=JSON.parse(JSON.stringify(ready));r.slot='T'+String(i+1).padStart(2,'0');r.task_ref='ZC-EXAMPLE-'+(i+1);o.records[i]=r;}
  const s=summarize(o);assert.equal(s.status,'READY_FOR_MANUAL_REVIEW');
  assert.equal(s.improved_quality,'NOT_ESTABLISHED');
  assert.equal(s.baseline_comparability,'NOT_ESTABLISHED');
});
test('historical baseline list is source-linked and totals exactly observed window',()=>{
  const doc=fs.readFileSync('docs/ai/PILOT_MEASUREMENT_9.md','utf8');
  for(const number of [41155970845,41157504579,41159604037,41185523285,
    41186656199,41189174430,41190777518,41195961219,41197363222])
    assert.ok(doc.includes('https://github.com/laszko14/nira-browser/actions/runs/'+number));
  assert.match(doc,/4141 s \(69 min 1 s\)/);
  assert.match(doc,/NOT_MEASURED/);
  assert.match(doc,/nie wynik pilotażu/i);
});
