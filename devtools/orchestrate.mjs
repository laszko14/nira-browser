#!/usr/bin/env node
// Zavro Development System Stage 7: read-only orchestration; NO APK/Gradle, refs, or code mutation.
import fs from 'node:fs/promises';
import {openSync,closeSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {basename,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {chooseTests,liveAudit,GitHubReader} from './smart-ci.mjs';
import {inspectLivePRs,GithubReader as InspectorReader} from './code-inspector.mjs';

const REPO='laszko14/nira-browser';
const TOOL_BRANCH='tools/zavro-development-system-stage1-20261027';
const SHA=/^[0-9a-f]{40}$/;
const REQUEST_KEYS=['branch','build_requested','changed_files','fast_probe_path','kind','mode','repo','schema_version','sha','test_run_id','variant'];
const STAGES=['CREATED','ANALYZED','INSPECTED','ISOLATION_PLANNED','TEST_VERIFIED','BUILD_SKIPPED','REPORTED'];
const ALLOWED_ACTIONS=['ANALYZE','INSPECT','PREPARE','VERIFY_TEST','SKIP_BUILD','REPORT'];
const CHECKPOINT_KEYS=['branch','checksum','events','mode','plan_id','report','revision','schema_version','sha','stage','variant'];
export class OrchestrationError extends Error{constructor(code){super(code);this.code=code;}}
function guard(ok,code){if(!ok)throw new OrchestrationError(code);}
function digest(x){return createHash('sha256').update(JSON.stringify(x),'utf8').digest('hex');}
function objectKeys(x){return Object.keys(x||{}).sort().join(',');}
function safePath(p){return typeof p==='string'&&p.length<=220&&!p.includes('..')&&!p.startsWith('/')&&!p.includes('\\')&&/^[A-Za-z0-9_.\/+-]+$/.test(p);}
export function validateRequest(request,config){
 guard(request&&typeof request==='object'&&!Array.isArray(request)&&objectKeys(request)===REQUEST_KEYS.join(','),'INVALID_REQUEST_FIELDS');
 guard(request.schema_version===1&&request.kind==='orchestrator-diagnostic'&&request.mode==='DIAGNOSTIC_ONLY'&&request.repo===REPO&&request.build_requested===false,'BUILD_FORBIDDEN');
 guard(request.variant==='clean'||request.variant==='mpv','INVALID_VARIANT');
 guard(config?.repo===REPO&&config.mode==='preflight-only'&&config.execution_enabled===false&&config.create_refs_enabled===false&&config.allow_releases===false,'UNSAFE_CONFIG');
 const t=config.targets?.[request.variant];
 guard(t&&SHA.test(request.sha)&&request.sha===t.pinned_sha&&request.branch===t.branch,'PIN_MISMATCH');
 guard(Array.isArray(request.changed_files)&&request.changed_files.length>0&&request.changed_files.length<=30,'CHANGES_REQUIRED');
 for(const f of request.changed_files){
  guard(f&&objectKeys(f)==='filename,status'&&safePath(f.filename)&&['added','modified','removed'].includes(f.status),'INVALID_CHANGE');
 }
 guard(Number.isSafeInteger(request.test_run_id)&&request.test_run_id>0,'INVALID_TEST_RUN');
 guard(typeof request.fast_probe_path==='string'&&/^devtools\/smart-ci-probes\/[a-z0-9-]+\.json$/.test(request.fast_probe_path),'INVALID_FAST_PROBE');
 const testPlan=chooseTests(request.changed_files,request.variant,config);
 return {target:t,tasks:testPlan.tasks};
}
function sign(state){const {checksum,...rest}=state;return {...rest,checksum:digest(rest)};}
export function newCheckpoint(request,config){
 validateRequest(request,config);
 const id=digest(request);
 return sign({schema_version:1,plan_id:id,mode:'DIAGNOSTIC_ONLY',variant:request.variant,branch:request.branch,sha:request.sha,
  revision:0,stage:'CREATED',events:[],report:null});
}
export function validateCheckpoint(state,request,config){
 validateRequest(request,config);
 guard(state&&objectKeys(state)===CHECKPOINT_KEYS.join(','),'BAD_CHECKPOINT_SCHEMA');
 guard(state.schema_version===1&&state.plan_id===digest(request)&&state.variant===request.variant&&state.branch===request.branch&&state.sha===request.sha&&state.mode==='DIAGNOSTIC_ONLY','CHECKPOINT_REQUEST_MISMATCH');
 guard(STAGES.includes(state.stage)&&Number.isInteger(state.revision)&&state.revision===STAGES.indexOf(state.stage),'CHECKPOINT_STEP_MISMATCH');
 guard(Array.isArray(state.events)&&state.events.length===state.revision&&state.events.every((e,i)=>e===ALLOWED_ACTIONS[i]),'CHECKPOINT_HISTORY_TAMPERED');
 guard(state.checksum===digest(Object.fromEntries(Object.entries(state).filter(([k])=>k!=='checksum'))),'CHECKPOINT_DIGEST_MISMATCH');
 guard(state.stage==='REPORTED'?state.report?.status==='DIAGNOSTIC_ONLY_TEST_VERIFIED':state.report===null,'CHECKPOINT_REPORT_INVALID');
 return state;
}
export function advance(state,request,config,action,evidence={}){
 validateCheckpoint(state,request,config);
 const idx=ALLOWED_ACTIONS.indexOf(action);
 guard(idx>=0,'UNKNOWN_ACTION');
 if(idx<state.revision){
  // Replays of already recorded transitions never rewrite or re-dispatch.
  guard(state.events[idx]===action,'REPLAY_MISMATCH');return state;
 }
 guard(idx===state.revision,'OUT_OF_ORDER');
 const select=validateRequest(request,config).tasks;
 if(action==='ANALYZE'){
  guard(evidence?.tasks?.join(',')===select.join(','),'SELECTION_MISMATCH');
 }else if(action==='INSPECT'){
  guard(evidence?.repo===REPO&&evidence.checked_prs?.length===4&&evidence.mutation_allowed===false&&evidence.merge_conflicts_claimed===false,'INSPECTION_NOT_VERIFIED');
  guard([84,85,88,89].every(n=>evidence.checked_prs.some(x=>x.number===n&&SHA.test(x.sha))),'PR_EVIDENCE_MISSING');
 }else if(action==='PREPARE'){
  guard(evidence?.implementation_applied===false&&evidence?.isolation_only===true&&evidence?.branch_plan==='tools/zavro-isolated-'+request.variant+'-'+request.sha.slice(0,12),'UNSAFE_IMPLEMENTATION');
 }else if(action==='VERIFY_TEST'){
  guard(select.length===1&&select[0]==='js','REQUIRES_ADDITIONAL_TESTS');
  guard(evidence?.run_id===request.test_run_id&&evidence?.target_sha===request.sha&&evidence?.variant===request.variant&&evidence?.scope==='js'&&evidence?.verified===true,'TEST_NOT_VERIFIED');
 }else if(action==='SKIP_BUILD'){
  guard(request.build_requested===false&&state.mode==='DIAGNOSTIC_ONLY'&&evidence?.android_build_started===false&&evidence?.reason==='NO_BUILD_REQUESTED','BUILD_NOT_SKIPPED');
 }else if(action==='REPORT'){
  guard(evidence?.status==='DIAGNOSTIC_ONLY_TEST_VERIFIED'&&evidence?.android_build_started===false&&evidence?.apk_created===false&&evidence?.implementation_applied===false,'INVALID_FINAL_REPORT');
 }
 const out={...state,revision:state.revision+1,stage:STAGES[state.revision+1],events:[...state.events,action],
  report:action==='REPORT'?{
   status:'DIAGNOSTIC_ONLY_TEST_VERIFIED',variant:request.variant,sha:request.sha,
   observed_test_run_id:request.test_run_id,test_scope:'js',
   build_started:false,apk_created:false,implementation_applied:false,
   full_android_ci_verified:false,all_required_tests_verified:select.length===1&&select[0]==='js',
   inspector_overlap_is_merge_conflict:false,checkpoint_resumable:true
  }:null};
 return sign(out);
}
export function validateTrigger(env,diff){
 guard(env.GITHUB_REPOSITORY===REPO&&env.GITHUB_REF==='refs/heads/'+TOOL_BRANCH&&env.GITHUB_ACTOR==='laszko14','TRIGGER_DENIED');
 guard(SHA.test(env.BEFORE_SHA||'')&&SHA.test(env.GITHUB_SHA||''),'TRIGGER_SHA');
 guard(typeof diff==='string'&&diff.length<1200,'TRIGGER_DIFF');
 const rows=diff.trim().split('\n').filter(Boolean);
 guard(rows.length===1,'MULTIPLE_FILES');
 const [status,filename,...extra]=rows[0].split('\t');
 guard(status==='A'&&extra.length===0&&/^devtools\/orchestrator-probes\/[a-z0-9-]+\.json$/.test(filename),'UNSAFE_PROBE_PATH');
 return filename;
}
export async function readVerifiedFastTest(request,fetcher){
 const run=await fetcher.get('/actions/runs/'+request.test_run_id);
 guard(run?.id===request.test_run_id&&run.name==='Zavro Smart CI Fast JavaScript'&&run.path==='.github/workflows/zavro-smart-ci-js.yml'&&run.workflow_id===397976899&&run.event==='push'&&run.status==='completed'&&run.conclusion==='success','FAST_RUN_NOT_SUCCESS');
 guard(run.repository?.full_name===REPO&&run.head_branch===TOOL_BRANCH&&SHA.test(run.head_sha)&&run.actor?.login==='laszko14','FAST_RUN_PROVENANCE');
 const {jobs,total_count}=await fetcher.get('/actions/runs/'+request.test_run_id+'/jobs?per_page=100');
 guard(total_count===1&&jobs?.length===1&&jobs[0].name==='fast-js'&&jobs[0].conclusion==='success'&&jobs[0].run_id===run.id,'FAST_RUN_JOB_NOT_VERIFIED');
 const f=await fetcher.get('/contents/'+request.fast_probe_path+'?ref='+run.head_sha);
 guard(f?.encoding==='base64'&&typeof f.content==='string'&&f.content.length<16000,'FAST_PROBE_NOT_FOUND');
 let p;try{p=JSON.parse(Buffer.from(f.content.replace(/\s/g,''),'base64').toString('utf8'));}catch{throw new OrchestrationError('FAST_PROBE_BAD_JSON');}
 guard(p?.schema_version===1&&p.kind==='smart-ci-js'&&p.intent==='diagnostic-only'&&p.build_requested===false&&p.repo===REPO&&p.variant===request.variant&&p.branch===request.branch&&p.sha===request.sha,'FAST_RUN_TARGET_MISMATCH');
 return {run_id:run.id,target_sha:p.sha,variant:p.variant,scope:'js',verified:true};
}
export class ReadOnlyAPI{
 constructor(token=null,transport=fetch){this.token=token;this.transport=transport;}
 async get(path){
  guard(/^\/actions\/runs\/[1-9][0-9]*(?:\/jobs\?per_page=100)?$/.test(path)||
    /^\/contents\/devtools\/smart-ci-probes\/[a-z0-9-]+\.json\?ref=[a-f0-9]{40}$/.test(path),'NETWORK_PATH_DENIED');
  const headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'};
  if(this.token)headers.Authorization='Bearer '+this.token;
  let response;
  try{response=await this.transport('https://api.github.com/repos/'+REPO+path,{method:'GET',headers,redirect:'error',signal:AbortSignal.timeout(14000)});}catch{throw new OrchestrationError('NETWORK_FAILED');}
  guard(response.status===200,'NETWORK_HTTP_'+response.status);
  try{return await response.json();}catch{throw new OrchestrationError('NETWORK_BAD_JSON');}
 }
}
export async function storeCheckpoint(file,state,request,config){
 validateCheckpoint(state,request,config);
 guard(typeof file==='string'&&file.length>0&&file.length<1024&&file.endsWith('.json'),'UNSAFE_CHECKPOINT_PATH');
 const lock=file+'.lock';let handle;
 try{handle=await fs.open(lock,'wx',0o600);}catch{throw new OrchestrationError('CHECKPOINT_LOCKED');}
 try{
  let existing=null;
  try{existing=JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code!=='ENOENT')throw new OrchestrationError('CHECKPOINT_READ_FAILED');}
  if(existing){
   validateCheckpoint(existing,request,config);
   guard(existing.revision<=state.revision,'CHECKPOINT_DOWNGRADE');
   if(existing.revision===state.revision){guard(existing.checksum===state.checksum,'CHECKPOINT_FORK');return false;}
   guard(existing.revision===state.revision-1,'CHECKPOINT_GAP');
   guard(existing.events.join(',')===state.events.slice(0,-1).join(','),'CHECKPOINT_HISTORY_DIVERGED');
  }else guard(state.revision===0,'CHECKPOINT_INITIAL_MISSING');
  const temp=file+'.part-'+process.pid;
  try{
   const out=await fs.open(temp,'wx',0o600);
   try{await out.writeFile(JSON.stringify(state,null,2)+'\n');await out.sync();}finally{await out.close();}
   await fs.rename(temp,file);
  }finally{await fs.unlink(temp).catch(()=>{});}
  return true;
 }finally{await handle.close();await fs.unlink(lock).catch(()=>{});}
}
export async function progress(request,config,{load=async()=>null,save=async()=>{},audit,inspect,verify}={}){
 validateRequest(request,config);
 let state=await load();
 if(state)validateCheckpoint(state,request,config);
 else{state=newCheckpoint(request,config);await save(state);}
 while(state.stage!=='REPORTED'){
  const action=ALLOWED_ACTIONS[state.revision];
  let evidence;
  if(action==='ANALYZE')evidence={tasks:chooseTests(request.changed_files,request.variant,config).tasks};
  if(action==='INSPECT')evidence=await inspect();
  if(action==='PREPARE')evidence={implementation_applied:false,isolation_only:true,branch_plan:'tools/zavro-isolated-'+request.variant+'-'+request.sha.slice(0,12)};
  if(action==='VERIFY_TEST'){
   // Check current HEAD and signed CI configuration before accepting old test evidence.
   const a=await audit();
   guard(a.sha===request.sha&&a.static_audit==='PASS'&&a.android_compilation_executed===false,'TARGET_AUDIT_FAILED');
   evidence=await verify();
  }
  if(action==='SKIP_BUILD')evidence={android_build_started:false,reason:'NO_BUILD_REQUESTED'};
  if(action==='REPORT')evidence={status:'DIAGNOSTIC_ONLY_TEST_VERIFIED',android_build_started:false,apk_created:false,implementation_applied:false};
  const next=advance(state,request,config,action,evidence);
  await save(next);state=next;
 }
 return state;
}
async function main(){
 const args=process.argv.slice(2);
 guard(args.length===4&&args[0]==='run'&&args[2]==='--checkpoint','USAGE');
 const path=args[1],checkpointPath=args[3];
 guard(/^devtools\/orchestrator-probes\/[a-z0-9-]+\.json$/.test(path),'REQUEST_PATH_DENIED');
 guard(process.env.GITHUB_REPOSITORY===REPO&&process.env.GITHUB_REF==='refs/heads/'+TOOL_BRANCH&&process.env.GITHUB_ACTOR==='laszko14','CLI_ONLY_ON_AUTHORIZED_RUNNER');
 const before=process.env.BEFORE_SHA??'',after=process.env.GITHUB_SHA??'';
 const diff=execFileSync('git',['diff','--name-status','--no-renames',before,after],{encoding:'utf8'});
 guard(validateTrigger(process.env,diff)===path,'TRIGGER_FILE_MISMATCH');
 const request=JSON.parse(await fs.readFile(path,'utf8'));
 const config=JSON.parse(await fs.readFile(new URL('./build-control.json',import.meta.url),'utf8'));
 const token=process.env.GH_TOKEN||null;
 const load=async()=>{try{return JSON.parse(await fs.readFile(checkpointPath,'utf8'));}catch(e){if(e.code==='ENOENT')return null;throw new OrchestrationError('CHECKPOINT_READ_FAILED');}};
 const save=async(s)=>storeCheckpoint(checkpointPath,s,request,config);
 const result=await progress(request,config,{
  load,save,
  audit:()=>liveAudit(config,request.variant,new GitHubReader(token)),
  inspect:()=>inspectLivePRs(new InspectorReader(token)),
  verify:()=>readVerifiedFastTest(request,new ReadOnlyAPI(token))
 });
 // Only bounded, sanitized metadata from the state reaches the report, not code, logs, or URLs.
 const report={...result.report,plan_id:result.plan_id,stage:result.stage,revision:result.revision,checkpoint_checksum:result.checksum};
 const output=checkpointPath.replace(/\.json$/,'.report.json');
 const content=JSON.stringify(report,null,2)+'\n';
 try{await fs.writeFile(output,content,{flag:'wx',mode:0o600});}
 catch(e){
  if(e.code!=='EEXIST')throw new OrchestrationError('REPORT_WRITE_FAILED');
  guard(await fs.readFile(output,'utf8')===content,'REPORT_REPLAY_CONFLICT');
 }
 console.log('ZAVRO_STAGE7_DIAGNOSTIC_ONLY_PASS '+result.variant+' '+result.sha+' JS run '+request.test_run_id+'; no APK and no code changes');
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 main().catch(e=>{console.error('ZAVRO_STAGE7 '+(e instanceof OrchestrationError?e.code:'FAILED'));process.exitCode=1;});
}
