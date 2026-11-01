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
test('T01 3/3 user-reported smoke is only pending follow-up, never full device verified',()=>{
  const entry=source.records[0],summary=summarize(source);
  assert.equal(entry.state,'FOLLOWUP_PENDING');
  assert.equal(entry.task_ref,'T01-GECKO-V1196');
  assert.equal(entry.scope,'GECKO');assert.equal(entry.complexity,'MEDIUM');
  assert.equal(entry.final_sha,'717608d1af23638614499d25dae7682f16a7fd8a');
  assert.ok(Date.parse(entry.completed_at)>=Date.parse(entry.started_at));
  assert.ok(Date.parse(entry.followup_due_at)-Date.parse(entry.completed_at)>=14*86400000);
  assert.equal(entry.followup_checked_at,null);
  assert.ok(FIELDS.every(k=>entry.metrics[k]===null));
  assert.ok(CAUSES.every(k=>entry.metrics.correction_causes[k]===null));
  assert.ok(source.records.slice(2).every(r=>r.state==='NOT_STARTED'));
  assert.deepEqual(validate(source),{started:2,finished:0});
  assert.equal(summary.status,'NO_RESULTS_YET');
  assert.equal(summary.completed_14_day_followups,0);
  assert.equal(summary.totals_for_completed_followups_only,null);
  assert.equal(summary.improved_quality,'NOT_ESTABLISHED');
});
test('T02 reported defects stay unresolved in 14-day follow-up',()=>{
  const entry=source.records[1],s=summarize(source);
  assert.equal(entry.slot,'T02');assert.equal(entry.task_ref,'T02-PANEL-GESTURES-V1196');
  assert.equal(entry.state,'FOLLOWUP_PENDING');
  assert.equal(entry.scope,'UI_GESTURES');assert.equal(entry.complexity,'MEDIUM');
  assert.equal(entry.final_sha,'717608d1af23638614499d25dae7682f16a7fd8a');
  assert.ok(Date.parse(entry.completed_at)>=Date.parse(entry.started_at));
  assert.ok(Date.parse(entry.followup_due_at)-Date.parse(entry.completed_at)>=14*86400000);
  assert.equal(entry.followup_checked_at,null);
  assert.ok(entry.evidence_links.some(x=>x.includes('/commit/f92957eb')));
  assert.ok(entry.evidence_links.some(x=>x.includes('/commit/1ec57d47')));
  assert.ok(FIELDS.every(k=>entry.metrics[k]===null));
  assert.ok(CAUSES.every(k=>entry.metrics.correction_causes[k]===null));
  assert.ok(source.records.slice(2).every(x=>x.state==='NOT_STARTED'));
  assert.deepEqual(validate(source),{started:2,finished:0});
  assert.equal(s.status,'NO_RESULTS_YET');
  assert.equal(s.improved_quality,'NOT_ESTABLISHED');
  assert.equal(s.completed_14_day_followups,0);
});
test('T02 does not silently convert user complaint or early follow-up into success',()=>{
  const state=copy();state.records[1].followup_checked_at=state.records[1].completed_at;
  assert.throws(()=>validate(state),/pending followup has final observation/);
  const invalid=copy();invalid.records[1].followup_due_at=invalid.records[1].completed_at;
  assert.throws(()=>validate(invalid),/at least 14 days/);
  const noSha=copy();noSha.records[1].final_sha=null;
  assert.throws(()=>validate(noSha),/final SHA missing/);
});
test('T02 evidence note includes negative result and no private screenshot',()=>{
  const note=fs.readFileSync('docs/ai/T02_V1196_PANEL_GESTURES.md','utf8');
  assert.match(note,/USER_REPORTED_ISSUES/);
  assert.match(note,/jank lewego panelu/i);
  assert.match(note,/fragment_bookmarks_bottom_sheet.xml/);
  assert.match(note,/Nie kopiowano go/);
  assert.match(note,/NOT_SEPARATELY_REPORTED/);
});
test('pending smoke requires exact 14-day window and forbids premature verdict',()=>{
  let s=copy();
  s.records[0].followup_due_at=s.records[0].completed_at;
  assert.throws(()=>validate(s),/at least 14 days/);
  s=copy();s.records[0].final_sha=null;
  assert.throws(()=>validate(s),/final SHA missing/);
  s=copy();s.records[0].followup_checked_at=s.records[0].followup_due_at;
  assert.throws(()=>validate(s),/pending followup has final observation/);
});
test('completed measured row accepts real zero and sums only eligible tasks',()=>{
  const o=complete(),s=summarize(o);
  assert.equal(s.status,'PARTIAL_PILOT');
  assert.equal(s.started,2);assert.equal(s.completed_14_day_followups,1);
  assert.equal(s.totals_for_completed_followups_only.regressions,0);
  assert.equal(s.totals_for_completed_followups_only.correction_rounds,0);
});
test('no false ZERO before data collection and no invented followup status',()=>{
  let o=copy();o.records[2].metrics.user_interventions=0;
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
  o=complete();o.records[2].slot='T01';
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
