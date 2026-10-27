import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {readFileSync} from 'node:fs';
import {newCheckpoint,validateRequest,validateCheckpoint,advance,validateTrigger,readVerifiedFastTest,ReadOnlyAPI,storeCheckpoint,progress,OrchestrationError} from '../orchestrate.mjs';

const config=JSON.parse(readFileSync(new URL('../build-control.json',import.meta.url),'utf8'));
const mk=(v='clean')=>({schema_version:1,kind:'orchestrator-diagnostic',mode:'DIAGNOSTIC_ONLY',repo:'laszko14/nira-browser',variant:v,branch:config.targets[v].branch,sha:config.targets[v].pinned_sha,build_requested:false,changed_files:[{filename:v==='clean'?'app/src/test/js/video-metadata-v1174.test.cjs':'app/src/test/js/zavro-x-mpv-v1167-integrity.test.cjs',status:'added'}],test_run_id:v==='clean'?40469224355:40469257622,fast_probe_path:v==='clean'?'devtools/smart-ci-probes/clean-v1174-js-20261027-01.json':'devtools/smart-ci-probes/mpv-v1167-js-20261027-01.json'});
const safeEvidence=(request)=>({run_id:request.test_run_id,target_sha:request.sha,variant:request.variant,scope:'js',verified:true});
const auditFor=(r)=>({sha:r.sha,static_audit:'PASS',android_compilation_executed:false});
const inspection={repo:'laszko14/nira-browser',checked_prs:[84,85,88,89].map(n=>({number:n,sha:'a'.repeat(40)})),mutation_allowed:false,merge_conflicts_claimed:false};
const prep=r=>({implementation_applied:false,isolation_only:true,branch_plan:'tools/zavro-isolated-'+r.variant+'-'+r.sha.slice(0,12)});
const next=(s,r,action,extra)=>{
 let e=extra??(action==='ANALYZE'?{tasks:['js']}:action==='INSPECT'?inspection:action==='PREPARE'?prep(r):action==='VERIFY_TEST'?safeEvidence(r):action==='SKIP_BUILD'?{android_build_started:false,reason:'NO_BUILD_REQUESTED'}:{status:'DIAGNOSTIC_ONLY_TEST_VERIFIED',android_build_started:false,apk_created:false,implementation_applied:false});
 return advance(s,r,config,action,e);
};
const reject=(fn,code)=>assert.throws(fn,e=>e instanceof OrchestrationError&&e.code===code);
const rejectAsync=(fn,code)=>assert.rejects(fn,e=>e instanceof OrchestrationError&&e.code===code);
test('clean and MPV schemas pinned to exact reviewed SHA',()=>{for(const v of ['clean','mpv'])assert.deepEqual(validateRequest(mk(v),config).tasks,['js']);});
test('build request cannot enter diagnostics',()=>{const r=mk();r.build_requested=true;reject(()=>newCheckpoint(r,config),'BUILD_FORBIDDEN');});
test('no opportunity to swap main',()=>{const r=mk();r.branch='main';reject(()=>validateRequest(r,config),'PIN_MISMATCH');});
test('short SHA rejected',()=>{const r=mk();r.sha='abc';reject(()=>validateRequest(r,config),'PIN_MISMATCH');});
test('cross variant SHA rejected',()=>{const r=mk();r.sha=config.targets.mpv.pinned_sha;reject(()=>validateRequest(r,config),'PIN_MISMATCH');});
test('extra secret fields refused',()=>{const r=mk();r.token='123';reject(()=>validateRequest(r,config),'INVALID_REQUEST_FIELDS');});
test('invalid change traversal rejected',()=>{const r=mk();r.changed_files=[{filename:'../secrets',status:'modified'}];reject(()=>validateRequest(r,config),'INVALID_CHANGE');});
test('unsafe global dispatch flags refused',()=>{const c=structuredClone(config);c.execution_enabled=true;reject(()=>validateRequest(mk(),c),'UNSAFE_CONFIG');});
test('each transition strictly follows previous transition',()=>{
 let r=mk(),s=newCheckpoint(r,config);
 reject(()=>next(s,r,'SKIP_BUILD'),'OUT_OF_ORDER');
 for(const action of ['ANALYZE','INSPECT','PREPARE','VERIFY_TEST','SKIP_BUILD','REPORT'])s=next(s,r,action);
 assert.equal(s.stage,'REPORTED');
 assert.deepEqual(s.events,['ANALYZE','INSPECT','PREPARE','VERIFY_TEST','SKIP_BUILD','REPORT']);
 assert.equal(s.report.build_started,false);assert.equal(s.report.apk_created,false);
 assert.equal(s.report.full_android_ci_verified,false);
});
test('replayed action is unchanged, not re-executed',()=>{
 const r=mk(),s=next(newCheckpoint(r,config),r,'ANALYZE');
 assert.deepEqual(next(s,r,'ANALYZE'),s);
});
test('tampered state checksum refused',()=>{
 const r=mk(),s=next(newCheckpoint(r,config),r,'ANALYZE');
 s.events.push('FAKE');reject(()=>validateCheckpoint(s,r,config),'CHECKPOINT_HISTORY_TAMPERED');
});
test('tampered checksum refused with consistent action length',()=>{
 const r=mk(),s=newCheckpoint(r,config);
 s.checksum='0'.repeat(64);
 reject(()=>validateCheckpoint(s,r,config),'CHECKPOINT_DIGEST_MISMATCH');
});
test('stale request refuses a checkpoint of another commit',()=>{
 const r=mk(),s=newCheckpoint(r,config);const newer=structuredClone(r);
 newer.changed_files[0].filename='app/src/test/js/another.test.cjs';
 reject(()=>validateCheckpoint(s,newer,config),'CHECKPOINT_REQUEST_MISMATCH');
});
test('inspection evidence must be real PR candidates',()=>{
 const r=mk(),s=next(newCheckpoint(r,config),r,'ANALYZE');
 reject(()=>next(s,r,'INSPECT',{...inspection,checked_prs:[]}), 'INSPECTION_NOT_VERIFIED');
});
test('preparing cannot change production code',()=>{
 const r=mk(),s=next(next(newCheckpoint(r,config),r,'ANALYZE'),r,'INSPECT');
 reject(()=>next(s,r,'PREPARE',{...prep(r),implementation_applied:true}),'UNSAFE_IMPLEMENTATION');
});
test('fake test run not accepted',()=>{
 const r=mk();let s=newCheckpoint(r,config);
 for(const a of ['ANALYZE','INSPECT','PREPARE'])s=next(s,r,a);
 reject(()=>next(s,r,'VERIFY_TEST',{...safeEvidence(r),target_sha:'f'.repeat(40)}),'TEST_NOT_VERIFIED');
});
test('Kotlin changes are never verified by JavaScript-only evidence',()=>{
 const r=mk();r.changed_files=[{filename:'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt',status:'modified'}];
 let s=newCheckpoint(r,config);
 s=next(s,r,'ANALYZE',{tasks:['kotlin-unit','android-lint','android-build','apk-metadata']});
 s=next(s,r,'INSPECT');s=next(s,r,'PREPARE');
 reject(()=>next(s,r,'VERIFY_TEST',safeEvidence(r)),'REQUIRES_ADDITIONAL_TESTS');
});
test('trigger permits only one new file',()=>{
 const e={GITHUB_REPOSITORY:'laszko14/nira-browser',GITHUB_REF:'refs/heads/tools/zavro-development-system-stage1-20261027',GITHUB_ACTOR:'laszko14',BEFORE_SHA:'a'.repeat(40),GITHUB_SHA:'b'.repeat(40)};
 assert.equal(validateTrigger(e,'A\tdevtools/orchestrator-probes/clean-v1174-01.json'),'devtools/orchestrator-probes/clean-v1174-01.json');
 reject(()=>validateTrigger(e,'A\tdevtools/orchestrator-probes/a.json\nM\tapp/build.gradle'),'MULTIPLE_FILES');
 reject(()=>validateTrigger(e,'M\tdevtools/orchestrator-probes/a.json'),'UNSAFE_PROBE_PATH');
 reject(()=>validateTrigger({...e,GITHUB_REF:'refs/heads/main'},'A\tdevtools/orchestrator-probes/a.json'),'TRIGGER_DENIED');
});
test('read-only API rejects URL and only uses GET',async()=>{
 const calls=[];const a=new ReadOnlyAPI(null,async(_,opts)=>{calls.push(opts.method);return {status:200,json:async()=>({})};});
 await a.get('/actions/runs/40469224355');
 assert.deepEqual(calls,['GET']);
 await rejectAsync(()=>a.get('/actions/workflows/apk-release.yml/dispatches'),'NETWORK_PATH_DENIED');
});
function fakeFast(r,alter={}){
 const probe={schema_version:1,kind:'smart-ci-js',intent:'diagnostic-only',build_requested:false,repo:'laszko14/nira-browser',variant:r.variant,branch:r.branch,sha:r.sha};
 const run={id:r.test_run_id,name:'Zavro Smart CI Fast JavaScript',path:'.github/workflows/zavro-smart-ci-js.yml',workflow_id:397976899,event:'push',status:'completed',conclusion:'success',repository:{full_name:'laszko14/nira-browser'},head_branch:'tools/zavro-development-system-stage1-20261027',head_sha:'b'.repeat(40),actor:{login:'laszko14'}};
 const job={total_count:1,jobs:[{name:'fast-js',conclusion:'success',run_id:r.test_run_id}]};
 const file={encoding:'base64',content:Buffer.from(JSON.stringify(probe)).toString('base64')};
 return {get:async p=>p.includes('/jobs?')?{...job,...alter.jobs}:p.includes('/contents/')?{...file,...alter.file}:{...run,...alter.run}};
}
test('real-shape fast evidence allowed only when SHA and JS job match',async()=>{
 const r=mk();const e=await readVerifiedFastTest(r,fakeFast(r));
 assert.equal(e.verified,true);assert.equal(e.scope,'js');
});
test('failed historical JS workflow denied',async()=>{
 const r=mk();await rejectAsync(()=>readVerifiedFastTest(r,fakeFast(r,{run:{conclusion:'failure'}})),'FAST_RUN_NOT_SUCCESS');
});
test('fast probe wrong target SHA denied',async()=>{
 const r=mk();const f=fakeFast(r);
 const get=f.get;f.get=async p=>p.includes('/contents/')?{encoding:'base64',content:Buffer.from(JSON.stringify({...r,kind:'smart-ci-js',intent:'diagnostic-only',sha:'f'.repeat(40)})).toString('base64')}:get(p);
 await rejectAsync(()=>readVerifiedFastTest(r,f),'FAST_RUN_TARGET_MISMATCH');
});
test('incomplete jobs rejected',async()=>{
 const r=mk();await rejectAsync(()=>readVerifiedFastTest(r,fakeFast(r,{jobs:{total_count:2}})),'FAST_RUN_JOB_NOT_VERIFIED');
});
test('state persists and resumes after interruption',async()=>{
 const r=mk(),directory=await mkdtemp(join(tmpdir(),'zavro-stage7-'));try{
 const file=join(directory,'checkpoint.json');let s=newCheckpoint(r,config);
 await storeCheckpoint(file,s,r,config);
 s=next(s,r,'ANALYZE');await storeCheckpoint(file,s,r,config);
 s=next(s,r,'INSPECT');await storeCheckpoint(file,s,r,config);
 let inspectCalls=0;
 const progressOpts={load:async()=>JSON.parse(await readFile(file,'utf8')),save:async p=>storeCheckpoint(file,p,r,config),
  inspect:async()=>{inspectCalls++;return inspection;},audit:async()=>auditFor(r),verify:async()=>safeEvidence(r)};
 const final=await progress(r,config,progressOpts);
 assert.equal(final.stage,'REPORTED');assert.equal(final.events.length,6);assert.equal(inspectCalls,0);
 assert.equal((await progress(r,config,progressOpts)).checksum,final.checksum);
 assert.equal(inspectCalls,0);
 }finally{await rm(directory,{recursive:true,force:true});}
});
test('cannot downgrade checkpoint revision',async()=>{
 const r=mk(),directory=await mkdtemp(join(tmpdir(),'zavro-stage7-'));try{
 const file=join(directory,'state.json'),origin=newCheckpoint(r,config),s=next(origin,r,'ANALYZE');
 await storeCheckpoint(file,origin,r,config);await storeCheckpoint(file,s,r,config);
 await rejectAsync(()=>storeCheckpoint(file,origin,r,config),'CHECKPOINT_DOWNGRADE');
 }finally{await rm(directory,{recursive:true,force:true});}
});
test('duplicate persistence is a no-op, no duplicate work',async()=>{
 const r=mk(),directory=await mkdtemp(join(tmpdir(),'zavro-stage7-'));try{
 const file=join(directory,'state.json'),s=newCheckpoint(r,config);
 assert.equal(await storeCheckpoint(file,s,r,config),true);
 assert.equal(await storeCheckpoint(file,s,r,config),false);
 }finally{await rm(directory,{recursive:true,force:true});}
});
test('full diagnostic pipeline does not call build tool',async()=>{
 const r=mk('mpv');let state=null,calls=[];
 const final=await progress(r,config,{load:async()=>state,save:async x=>{state=x;calls.push(x.stage);},
  audit:async()=>{calls.push('AUDIT');return auditFor(r);},
  inspect:async()=>{calls.push('INSPECT_API');return inspection;},
  verify:async()=>{calls.push('FAST_API');return safeEvidence(r);}
 });
 assert.equal(final.stage,'REPORTED');
 assert.deepEqual(final.events,['ANALYZE','INSPECT','PREPARE','VERIFY_TEST','SKIP_BUILD','REPORT']);
 assert.equal(calls.filter(x=>x==='FAST_API').length,1);
 assert.equal(final.report.implementation_applied,false);
 assert.equal(final.report.apk_created,false);
});
