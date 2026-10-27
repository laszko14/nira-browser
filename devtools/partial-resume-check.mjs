#!/usr/bin/env node
// Resume intentionally incomplete work without skipping required tests, repeating, or building.
import {readFileSync} from 'node:fs';
import {validateCheckpoint,progress,OrchestrationError} from './orchestrate.mjs';
import {chooseTests} from './smart-ci.mjs';

const REPO='laszko14/nira-browser';
const RUN=40472397516,ARTIFACT=12719177242;
export class PartialResumeError extends Error{constructor(code){super(code);this.code=code;}}
const guard=(ok,code)=>{if(!ok)throw new PartialResumeError(code);};
export function checkProbe(probe){
 const expected={schema_version:1,kind:'verify-fail-closed-partial',repo:REPO,source_run_id:RUN,source_artifact_id:ARTIFACT,
   source_request:'devtools/orchestrator-probes/stage7-negative-kotlin-20261027-01.json',build_requested:false};
 guard(probe&&Object.keys(probe).sort().join(',')===Object.keys(expected).sort().join(',')&&
   Object.keys(expected).every(k=>probe[k]===expected[k]),'PROBE_NOT_APPROVED');
 return expected;
}
export async function verifyPartial({state,request,config}){
 validateCheckpoint(state,request,config);
 guard(state.stage==='ISOLATION_PLANNED'&&state.revision===3&&state.report===null,'NOT_EXPECTED_PARTIAL_STATE');
 const tasks=chooseTests(request.changed_files,request.variant,config).tasks;
 guard(tasks.includes('android-build')&&tasks.includes('kotlin-unit'),'NOT_KOTLIN_SCENARIO');
 const invoked={audit:0,inspect:0,verify:0,save:0};
 const prohibit=kind=>async()=>{invoked[kind]++;throw new PartialResumeError('UNEXPECTED_REEXECUTION_'+kind);};
 let rejected=false;
 try{
  await progress(request,config,{load:async()=>state,save:prohibit('save'),
   audit:prohibit('audit'),inspect:prohibit('inspect'),verify:prohibit('verify')});
 }catch(e){if(e instanceof OrchestrationError&&e.code==='REQUIRES_ADDITIONAL_TESTS')rejected=true;else throw e;}
 guard(rejected&&Object.values(invoked).every(x=>x===0),'FAIL_CLOSED_GATE_NOT_OBSERVED');
 return {status:'PARTIAL_CHECKPOINT_RESUMED_SAFELY',source_run_id:RUN,artifact_id:ARTIFACT,
   stage_still:state.stage,checkpoint_checksum:state.checksum,required_tests_missing:tasks,
   prior_steps_replayed:0,remote_operations_started:0,build_started:false,apk_created:false};
}
async function main(){
 const args=process.argv.slice(2);
 guard(args.length===3,'USAGE');
 const [state,request,probe]=args.map(p=>JSON.parse(readFileSync(p,'utf8')));
 checkProbe(probe);
 const config=JSON.parse(readFileSync(new URL('./build-control.json',import.meta.url),'utf8'));
 console.log(JSON.stringify(await verifyPartial({state,request,config})));
}
if(process.argv[1]?.endsWith('partial-resume-check.mjs'))
 main().catch(e=>{console.error('PARTIAL_RESUME '+(e.code||'FAILED'));process.exitCode=1;});
