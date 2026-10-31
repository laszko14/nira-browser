'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {buildReport}=require('./ci-evidence.cjs');
const env={DOCS_RESULT:'success',SHARED_RESULT:'success',GITHUB_SHA:'a'.repeat(40),GITHUB_RUN_ID:'123',
 GITHUB_RUN_ATTEMPT:'2',GITHUB_EVENT_NAME:'push',GITHUB_REF:'refs/heads/test'};
test('completed green required results include SHA, run attempt and separate device/APK',()=>{
  const x=buildReport(env);
  assert.equal(x.ok,true);
  assert.match(x.text,/Device: NOT_TESTED; APK: NOT_BUILT/);
  assert.match(x.text,/attempt 2/);
});
test('skipped, cancelled, failed and missing required results cannot be PASS',()=>{
  for(const status of ['skipped','cancelled','failure','in_progress'])
    assert.equal(buildReport({...env,SHARED_RESULT:status}).ok,false);
  assert.throws(()=>buildReport({...env,DOCS_RESULT:''}),/Missing result context/);
});
