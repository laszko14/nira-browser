'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),{validate}=require('./error-learning.cjs');
const {classify,readOnce}=require('./capture-ci-errors.cjs');
const sha='7ea23d0de749a59f322d1735f12507b218b3c725';
const source=()=>JSON.parse(fs.readFileSync('docs/ai/ERROR_CASES.json','utf8'));
// Isolate mutation fixtures from real growing ledger: registry growth must not break tests.
const copy=()=>{const d=source();d.events=d.events.slice(0,1);d.coverage=d.coverage.slice(0,1);d.coverage[0].observed_count=1;return d;};
const event=(id,over={})=>({
 id,task_id:'ZC-V31-ERROR-LEARNING',kind:'USER_REPORT',
 fingerprint:'incomplete-error-capture',summary:'User reported another identical failure',
 source_sha:sha,observed_at:null,run_id:null,job_id:null,attempt:null,
 cause:'undetermined',state:'OPEN',duplicate_of:null,fix_sha:null,
 regression_test:null,evidence_links:[],...over
});
test('canonical registry validates without inventing missing historical reports',()=>{
 const current=source();assert.equal(validate(current).recorded,current.events.length);
 const d=copy();assert.deepEqual(validate(d),{tasks:1,recorded:1,open:1});
 assert.match(d.events[0].summary,/2 z 10/);
});
test('missing case count is rejected',()=>{
 const d=copy();d.coverage[0].observed_count=2;assert.throws(()=>validate(d),/coverage mismatch/);
});
test('duplicate event ID and unregistered task are rejected',()=>{
 const d=copy();d.coverage[0].observed_count=2;d.events.push({...d.events[0]});
 assert.throws(()=>validate(d),/duplicate id/);
 d.events[1]=event('ERR-20261101-0002',{task_id:'INVALID-TASK'});
 assert.throws(()=>validate(d),/unknown task/);
});
test('repeat requires explicit link to first event',()=>{
 const d=copy();d.coverage[0].observed_count=2;d.events.push(event('ERR-20261101-0002'));
 assert.throws(()=>validate(d),/repeat needs duplicate_of/);
 d.events[1].duplicate_of=d.events[0].id;
 assert.equal(validate(d).recorded,2);
});
test('cannot close task with open errors or assert fix without SHA',()=>{
 const d=copy();d.coverage[0].state='COMPLETE';
 assert.throws(()=>validate(d),/unresolved/);
 d.events[0].state='FIXED';assert.throws(()=>validate(d),/FIXED requires/);
 d.events[0].fix_sha=sha;assert.equal(validate(d).open,0);
});
test('CI must retain job/run/attempt and source evidence',()=>{
 const d=copy();d.events[0]=event('ERR-20261101-0001',{kind:'CI_FAILURE',
   run_id:13,job_id:14,attempt:1,
   evidence_links:['https://github.com/laszko14/nira-browser/actions/runs/13']});
 assert.equal(validate(d).recorded,1);
 d.events[0].attempt=null;assert.throws(()=>validate(d),/CI provenance/);
 d.events[0].attempt=1;d.events[0].evidence_links=[];
 assert.throws(()=>validate(d),/CI run link/);
});
test('capture records first failing step and exact run/job without raw logs',()=>{
 const run={id:12,head_sha:sha,run_attempt:2,event:'push',
   repository:{full_name:'laszko14/nira-browser'}};
 const jobs=[{id:13,name:'unit',status:'completed',conclusion:'failure',
   steps:[{name:'Compile',conclusion:'success'},{name:'Robolectric',conclusion:'failure'}]},
   {id:14,name:'lint',status:'completed',conclusion:'success'},
   {id:15,name:'Verify required jobs and summarize evidence (NO APK)',
     status:'in_progress',conclusion:null}];
 const r=classify(run,jobs,sha,12);
 assert.equal(r.failures.length,1);
 assert.equal(r.failures[0].first_failing_step,'Robolectric');
 assert.equal(r.failures[0].event_id,'CI-12-2-13');
 assert.equal(r.failures[0].raw_logs,undefined);
 assert.equal(r.failures[0].cause,'undetermined');
 assert.ok(!Object.hasOwn(r.failures[0],'token'));

});
test('capture rejects stale SHA, incomplete listing, duplicate jobs',()=>{
 const run={id:12,head_sha:sha,run_attempt:1,repository:{full_name:'laszko14/nira-browser'}};
 const job={id:13,name:'unit',status:'completed',conclusion:'success'};
 assert.throws(()=>classify(run,[job], '0'.repeat(40),12),/stale run/);
 assert.throws(()=>classify(run,[],sha,12),/missing jobs/);
 assert.throws(()=>classify(run,[job,job],sha,12),/duplicates/);
 assert.throws(()=>classify(run,[{...job,status:'in_progress'}],sha,12),/job not completed/);
});
test('read-only API captures and rejects missing pagination',async()=>{
 const run={id:12,head_sha:sha,run_attempt:1,repository:{full_name:'laszko14/nira-browser'}};
 const job={id:13,name:'unit',status:'completed',conclusion:'failure'};
 const fake=async url=>({ok:true,json:async()=>url.endsWith('/12')?run:{jobs:[job],total_count:1}});
 assert.equal((await readOnce(12,sha,'',fake)).failures.length,1);
 const missing=async url=>({ok:true,json:async()=>url.endsWith('/12')?run:{jobs:[job],total_count:2}});
 await assert.rejects(readOnce(12,sha,'',missing),/incomplete job page/);
});

test('historical SOURCE cancellation does not fabricate three extra CI failures',()=>{
 const historical='0821b3aa06d45f41c47ee9a1c99fd0ea895b47b2';
 const run={id:41334916448,head_sha:historical,run_attempt:1,event:'push',
   repository:{full_name:'laszko14/nira-browser'}};
 const jobs=[
  {id:124932586787,name:'process-docs',status:'completed',conclusion:'failure',
    steps:[{name:'Validate V3.1 persistent error learning and negative tests',conclusion:'failure'}]},
  {id:124932631666,name:'AndroidTest',status:'completed',conclusion:'cancelled'},
  {id:124932631676,name:'lint',status:'completed',conclusion:'cancelled'},
  {id:124932631765,name:'Robolectric',status:'completed',conclusion:'cancelled'}];
 const r=classify(run,jobs,historical,41334916448);
 assert.equal(r.capture_status,'FAILURES_OBSERVED');
 assert.deepEqual(r.failures.map(x=>x.job_id),[124932586787]);
 assert.deepEqual(r.interruptions.map(x=>x.job_id),[124932631666,124932631676,124932631765]);
 assert.ok(r.interruptions.every(x=>!Object.hasOwn(x,'kind')));
});
test('interrupted-only CI is not silently classified as successful',()=>{
 const run={id:12,head_sha:sha,run_attempt:1,event:'push',
   repository:{full_name:'laszko14/nira-browser'}};
 const r=classify(run,[
  {id:13,name:'unit',status:'completed',conclusion:'cancelled'},
  {id:14,name:'lint',status:'completed',conclusion:'skipped'},
  {id:15,name:'build',status:'completed',conclusion:'skipped'},
  {id:16,name:'release',status:'completed',conclusion:'skipped'},
  {id:17,name:'review',status:'completed',conclusion:'action_required'}
 ],sha,12);
 assert.equal(r.capture_status,'INCOMPLETE_RUN');
 assert.deepEqual(r.failures,[]);
 assert.deepEqual(r.interruptions.map(x=>x.job_id),[13,14,17]);
});
