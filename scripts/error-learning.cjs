'use strict';
const fs=require('node:fs');
const SHA=/^[a-f0-9]{40}$/;
const ID=/^[A-Z][A-Z0-9-]{3,79}$/;
const TYPES=['CI_FAILURE','USER_REPORT','REGRESSION','REVIEW'];
const CAUSES=['production','test_harness','workflow','environment','intentional_red','undetermined'];
const STATES=['OPEN','FIXED','ACCEPTED_RISK'];
const EVIDENCE=/^https:\/\/github\.com\/laszko14\/nira-browser\/(?:actions\/runs\/[1-9][0-9]*|pull\/[1-9][0-9]*|issues\/[1-9][0-9]*|commit\/[a-f0-9]{40})$/;
function fail(s){throw Error('Error learning: '+s)}
function fields(o,keys){return o&&typeof o==='object'&&!Array.isArray(o)&&
  Object.keys(o).sort().join()===keys.slice().sort().join()}
function validate(data){
  if(!fields(data,['schema','coverage','events'])||data.schema!=='zavro.error-learning/1')fail('schema');
  if(!Array.isArray(data.coverage)||!Array.isArray(data.events))fail('arrays');
  const tasks=new Map(),ids=new Map(),first=new Map();
  for(const t of data.coverage){
    if(!fields(t,['task_id','observed_count','state'])||!ID.test(t.task_id)||
      tasks.has(t.task_id)||!Number.isSafeInteger(t.observed_count)||t.observed_count<0||
      !['ACTIVE','COMPLETE'].includes(t.state))fail('invalid coverage');
    tasks.set(t.task_id,t);
  }
  for(const e of data.events){
    if(!fields(e,['id','task_id','kind','fingerprint','summary','source_sha',
      'observed_at','run_id','job_id','attempt','cause','state','duplicate_of',
      'fix_sha','regression_test','evidence_links']))fail('event fields');
    if(!ID.test(e.id)||ids.has(e.id)||!tasks.has(e.task_id))fail('duplicate id or unknown task');
    if(!TYPES.includes(e.kind)||typeof e.fingerprint!=='string'||
      !/^[a-z0-9][a-z0-9._:-]{3,119}$/.test(e.fingerprint))fail('fingerprint');
    if(typeof e.summary!=='string'||e.summary.length<12||e.summary.length>320||
      /[\r\n]/.test(e.summary))fail('invalid summary');
    if(e.source_sha!==null&&!SHA.test(e.source_sha))fail('source SHA');
    if(e.observed_at!==null&&(!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(e.observed_at)||
      !Number.isFinite(Date.parse(e.observed_at))))fail('observed time');
    if(!Array.isArray(e.evidence_links)||new Set(e.evidence_links).size!==e.evidence_links.length||
      !e.evidence_links.every(s=>typeof s==='string'&&EVIDENCE.test(s)))fail('evidence');
    if(e.kind==='CI_FAILURE'){
      if(!SHA.test(e.source_sha||'')||!Number.isSafeInteger(e.run_id)||e.run_id<1||
        !Number.isSafeInteger(e.job_id)||e.job_id<1||
        !Number.isSafeInteger(e.attempt)||e.attempt<1)fail('CI provenance');
      if(!e.evidence_links.includes('https://github.com/laszko14/nira-browser/actions/runs/'+e.run_id))
        fail('CI run link');
    }else if([e.run_id,e.job_id,e.attempt].some(x=>x!==null))fail('non-CI provenance');
    if(!CAUSES.includes(e.cause)||!STATES.includes(e.state))fail('cause/status');
    if(e.fix_sha!==null&&!SHA.test(e.fix_sha))fail('fix SHA');
    if(e.state==='FIXED'&&(!e.fix_sha||!e.regression_test))fail('FIXED requires SHA and test');
    if(e.state==='OPEN'&&e.fix_sha!==null)fail('OPEN with fix SHA');
    if(e.regression_test!==null&&
      (typeof e.regression_test!=='string'||
        !/^(?:scripts|app\/src\/test)\/[a-zA-Z0-9_./-]{5,180}$/.test(e.regression_test)||
        e.regression_test.includes('..')))fail('test reference');
    const key=e.task_id+'|'+e.fingerprint;
    if(first.has(key)&&e.duplicate_of!==first.get(key))fail('repeat needs duplicate_of');
    if(e.duplicate_of!==null){
      const prior=ids.get(e.duplicate_of);
      if(!prior||prior.task_id!==e.task_id||prior.fingerprint!==e.fingerprint||
        prior.duplicate_of!==null)fail('invalid duplicate reference');
    }
    if(!first.has(key))first.set(key,e.id);
    ids.set(e.id,e);
  }
  for(const t of data.coverage){
    const events=data.events.filter(e=>e.task_id===t.task_id);
    if(events.length!==t.observed_count)fail('coverage mismatch '+t.task_id);
    if(t.state==='COMPLETE'&&events.some(e=>e.state==='OPEN'))fail('unresolved task '+t.task_id);
  }
  return {tasks:data.coverage.length,recorded:data.events.length,
    open:data.events.filter(e=>e.state==='OPEN').length};
}
function main(){
  const arg=process.argv.slice(2);
  if(arg.length>1||(arg[0]&&arg[0]!=='validate'))fail('usage: node scripts/error-learning.cjs validate');
  process.stdout.write(JSON.stringify(validate(JSON.parse(fs.readFileSync('docs/ai/ERROR_CASES.json','utf8'))))+'\n');
}
if(require.main===module){try{main()}catch(e){console.error(e.message);process.exitCode=1}}
module.exports={validate};
