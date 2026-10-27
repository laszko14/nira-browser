#!/usr/bin/env node
// Read-only recovery test across two separate GitHub Actions runs.
import fs from 'node:fs/promises';
import {progress,validateCheckpoint} from './orchestrate.mjs';

const EXPECTED={repo:'laszko14/nira-browser',source_run_id:40472241780,source_artifact_id:12719096410,
  kind:'resume-previous-action',schema_version:1,build_requested:false,
  request_path:'devtools/orchestrator-probes/stage7-mpv-20261027-01.json'};
export class ResumeError extends Error{constructor(code){super(code);this.code=code;}}
const check=(x,c)=>{if(!x)throw new ResumeError(c);};
export function validateResumeRequest(obj){
 check(obj&&typeof obj==='object'&&!Array.isArray(obj)&&
 Object.keys(obj).sort().join(',')===Object.keys(EXPECTED).sort().join(','),'RESUME_FIELDS');
 for(const [k,v] of Object.entries(EXPECTED))check(obj[k]===v,'RESUME_REQUEST_MISMATCH');
 return obj;
}
export async function resumedState(req,config,state,report){
 validateCheckpoint(state,req,config);
 check(state.stage==='REPORTED'&&state.revision===6&&state.report?.status==='DIAGNOSTIC_ONLY_TEST_VERIFIED','NOT_COMPLETED');
 check(report&&report.plan_id===state.plan_id&&report.checkpoint_checksum===state.checksum&&report.status==='DIAGNOSTIC_ONLY_TEST_VERIFIED'&&report.sha===req.sha&&report.build_started===false&&report.apk_created===false,'REPORT_MISMATCH');
 let calls=0;
 const unexpected=()=>{calls++;throw new ResumeError('PREVIOUS_WORK_REEXECUTED');};
 const recovered=await progress(req,config,{load:async()=>state,save:unexpected,audit:unexpected,inspect:unexpected,verify:unexpected});
 check(calls===0&&recovered.checksum===state.checksum,'RESUME_REPLAY_CHANGED');
 return {status:'CROSS_RUN_CHECKPOINT_RESTORED',plan_id:recovered.plan_id,sha:recovered.sha,
   source_run_id:EXPECTED.source_run_id,source_artifact_id:EXPECTED.source_artifact_id,
   verified_zero_repeat_operations:true,build_started:false,artifact_apk_created:false};
}
async function main(){
 const [probe,stateFile,reportFile,requestFile]=process.argv.slice(2);
 check([probe,stateFile,reportFile,requestFile].every(x=>typeof x==='string'&&x.length<1024),'USAGE');
 const [p,state,report,req,conf]=await Promise.all([
  fs.readFile(probe,'utf8').then(JSON.parse),
  fs.readFile(stateFile,'utf8').then(JSON.parse),
  fs.readFile(reportFile,'utf8').then(JSON.parse),
  fs.readFile(requestFile,'utf8').then(JSON.parse),
  fs.readFile(new URL('./build-control.json',import.meta.url),'utf8').then(JSON.parse)
 ]);
 validateResumeRequest(p);
 console.log(JSON.stringify(await resumedState(req,conf,state,report)));
}
if(process.argv[1]?.endsWith('resume-check.mjs'))main().catch(e=>{console.error('ZAVRO_RESUME '+(e instanceof ResumeError?e.code:'INVALID'));process.exitCode=1;});
