import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {validateResumeRequest,resumedState,ResumeError} from '../resume-check.mjs';
import {newCheckpoint,advance} from '../orchestrate.mjs';
const config=JSON.parse(readFileSync(new URL('../build-control.json',import.meta.url),'utf8'));
const req=JSON.parse(readFileSync(new URL('../orchestrator-probes/stage7-mpv-20261027-01.json',import.meta.url),'utf8'));
const probe={repo:'laszko14/nira-browser',source_run_id:40472241780,source_artifact_id:12719096410,kind:'resume-previous-action',schema_version:1,build_requested:false,request_path:'devtools/orchestrator-probes/stage7-mpv-20261027-01.json'};
const good=()=>{
 let state=newCheckpoint(req,config);
 const events=[
 ['ANALYZE',{tasks:['js']}],
 ['INSPECT',{repo:'laszko14/nira-browser',checked_prs:[84,85,88,89].map(number=>({number,sha:'a'.repeat(40)})),mutation_allowed:false,merge_conflicts_claimed:false}],
 ['PREPARE',{implementation_applied:false,isolation_only:true,branch_plan:'tools/zavro-isolated-mpv-'+req.sha.slice(0,12)}],
 ['VERIFY_TEST',{run_id:req.test_run_id,target_sha:req.sha,variant:'mpv',scope:'js',verified:true}],
 ['SKIP_BUILD',{android_build_started:false,reason:'NO_BUILD_REQUESTED'}],
 ['REPORT',{status:'DIAGNOSTIC_ONLY_TEST_VERIFIED',android_build_started:false,apk_created:false,implementation_applied:false}]
 ];
 for(const [a,e] of events)state=advance(state,req,config,a,e);
 return {state,report:{...state.report,plan_id:state.plan_id,checkpoint_checksum:state.checksum}};
};
const deny=(fn,code)=>assert.rejects(fn,e=>e instanceof ResumeError&&e.code===code);
test('dedicated request with exact original run and artifact accepted',()=>assert.deepEqual(validateResumeRequest(probe),probe));
test('unauthorized run is rejected',()=>assert.throws(()=>validateResumeRequest({...probe,source_run_id:99}),e=>e.code==='RESUME_REQUEST_MISMATCH'));
test('unauthorized build request is rejected',()=>assert.throws(()=>validateResumeRequest({...probe,build_requested:true}),e=>e.code==='RESUME_REQUEST_MISMATCH'));
test('unknown fields are forbidden',()=>assert.throws(()=>validateResumeRequest({...probe,token:'a'}),e=>e.code==='RESUME_FIELDS'));
test('complete state replays with zero callbacks',async()=>{const a=good();const x=await resumedState(req,config,a.state,a.report);assert.equal(x.status,'CROSS_RUN_CHECKPOINT_RESTORED');assert.equal(x.verified_zero_repeat_operations,true);});
test('report with wrong checkpoint digest is denied',async()=>{const a=good();await deny(()=>resumedState(req,config,a.state,{...a.report,checkpoint_checksum:'f'.repeat(64)}),'REPORT_MISMATCH');});
test('partial checkpoint cannot be declared complete',async()=>{const a=good();let s=newCheckpoint(req,config);await deny(()=>resumedState(req,config,s,a.report),'NOT_COMPLETED');});
test('incomplete or faked report cannot declare a build',async()=>{const a=good();await deny(()=>resumedState(req,config,a.state,{...a.report,build_started:true}),'REPORT_MISMATCH');});
