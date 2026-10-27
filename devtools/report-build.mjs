#!/usr/bin/env node
/** Stage 4: non-mutating, fail-closed GitHub Actions report and artifact provenance. */
import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';

const REPO = 'laszko14/nira-browser';
const BASE = 'https://api.github.com/repos/' + REPO;
const SHA = /^[a-f0-9]{40}$/;
const CERT = /^[a-f0-9]{64}$/;
export class ReportError extends Error {constructor(code){super(code);this.code=code;}}
function fail(code){throw new ReportError(code);}
function assert(v,code){if(!v)fail(code);}
function safeInt(v){return Number.isSafeInteger(v)&&v>0;}
function normalize(s){return String(s??'').toLowerCase();}
const knownSteps=new Set(['Run Android lint','Run unit tests','Test media detector WebExtension protocol','Build single arm64 debug APK (update-safe certificate)','Build signed standalone arm64 Zavro x MPV APK','Validate Gradle wrapper','Upload only arm64 debug APK']);
const maskedStep=s=>knownSteps.has(s)?s:'unrecognized step';

export function classifyFailure(job, log=''){
 const body=String(log??'').slice(-300000);
 const step=(job.steps??[]).find(s=>s.conclusion==='failure');
 const name=normalize(step?.name??job.name);
 if(/signing certificate sha-256 does not match|stable signing secret|keystore.*not configured|apksigner.*failed/i.test(body))return 'SIGNING';
 if(/apk metadata mismatch|apk must contain only arm64|expected exactly one arm64|versioncode.*mismatch/i.test(body))return 'APK_METADATA';
 if(/could not resolve|could not find .*:.*:|could not download|could not GET|read timed out|repository .*not found/i.test(body))return 'DEPENDENCY';
 if(/compiler error|compilation error|:compiledebugkotlin.*failed|unresolved reference|cannot find symbol/i.test(body))return 'COMPILER';
 if(/lint/.test(name))return 'ANDROID_LINT';
 if(/webextension|javascript|\bnode\b|media detector/i.test(name)||/# fail [1-9]|not ok \d+|ERR_ASSERTION/i.test(body))return 'JAVASCRIPT_TEST';
 if(/unit tests|testdebugunittest|\bjunit\b/i.test(name)||/there were failing tests/i.test(body))return 'KOTLIN_TEST';
 if(/checkout|setup|upload|cache|runner|environment/i.test(name))return 'INFRASTRUCTURE';
 return 'UNKNOWN';
}

export function verifiedLogLine(log,t){
 if(typeof log!=='string'||!t||!CERT.test(t.certificate_sha256))return false;
 const match=log.match(/^.*?Verified package=([^\s]+) versionCode=(\d+) versionName=([^\s]+) ABI=([^\s]+) certificateSHA256=([a-f0-9]{64})[^\r\n]*$/m);
 return !!match&&match[1]===t.package&&Number(match[2])===t.version_code&&match[3]===t.version_name&&match[4]==='arm64-v8a'&&match[5]===t.certificate_sha256;
}

export function inspectArtifact(a,run,t,expectedName){
 if(!a||!safeInt(a.id)||a.name!==expectedName||a.expired!==false||!safeInt(a.size_in_bytes))return null;
 const link=a.workflow_run;
 if(!link||link.id!==run.id||link.repository_id!==1428971613||link.head_sha!==run.head_sha||(link.head_branch&&link.head_branch!==run.head_branch))return null;
 const digest=typeof a.digest==='string'&&/^sha256:[a-f0-9]{64}$/.test(a.digest)?a.digest:null;
 return {artifact_id:a.id,name:a.name,zip_bytes:a.size_in_bytes,expires_at:typeof a.expires_at==='string'?a.expires_at:null,digest,artifact_url:'https://github.com/'+REPO+'/actions/runs/'+run.id+'/artifacts/'+a.id};
}

export function buildReport({run,jobs,artifacts,config,variant,logs={}}){
 assert(config?.repo===REPO&&config.mode==='preflight-only'&&config.execution_enabled===false,'UNSAFE_CONFIG');
 assert(variant==='clean'||variant==='mpv','INVALID_VARIANT');
 const t=config.targets?.[variant];
 assert(t&&SHA.test(t.pinned_sha)&&CERT.test(t.certificate_sha256)&&safeInt(t.version_code),'INVALID_TARGET');
 assert(run&&safeInt(run.id)&&run.head_sha===t.pinned_sha&&run.head_branch===t.branch&&run.name==='Android CI','RUN_PROVENANCE_MISMATCH');
 assert(run.workflow_id===392221495&&run.event!=='push','WORKFLOW_UNRECOGNIZED');
 assert(run.head_repository?.id===undefined||run.head_repository?.id===1428971613,'REPO_MISMATCH');
 assert(run.status==='completed'||['queued','in_progress','requested','waiting','pending'].includes(run.status),'RUN_STATUS_UNKNOWN');
 assert(Array.isArray(jobs)&&Array.isArray(artifacts),'INVALID_COLLECTION');
 assert(jobs.length<=100&&artifacts.length<=100,'PAGINATION_REQUIRED');
 const mapping={};const failures=[];
 for(const j of jobs){
  if(!j||!safeInt(j.id)||!['build','test','lint','release'].includes(j.name)||mapping[j.name])fail('UNEXPECTED_OR_DUPLICATE_JOB');
  if(j.run_id!=null&&j.run_id!==run.id)fail('JOB_PROVENANCE_MISMATCH');
  mapping[j.name]={status:j.status,conclusion:j.conclusion};
  if(['failure','timed_out','cancelled'].includes(j.conclusion))failures.push({job:j.name,category:classifyFailure(j,logs[j.id]),failed_step:maskedStep(j.steps?.find(s=>s.conclusion==='failure')?.name||'unknown')});
 }
 const done=run.status==='completed';
 const jobsOK=['build','test','lint'].every(k=>mapping[k]?.conclusion==='success')&&mapping.release?.conclusion==='skipped';
 const runOK=done&&run.conclusion==='success'&&jobsOK;
 const artifactName=variant==='clean'?'zavro-arm64-update-apk-v'+t.version_code:'zavro-x-mpv-arm64-v'+t.version_code;
 const candidates=artifacts.filter(a=>a?.name===artifactName);
 const candidate=candidates.length===1?inspectArtifact(candidates[0],run,t,artifactName):null;
 const buildJob=jobs.find(j=>j.name==='build');
 const ciVerified=!!buildJob&&buildJob.conclusion==='success'&&verifiedLogLine(logs[buildJob.id],t);
 const artifact=candidate&&runOK&&ciVerified?candidate:null;
 const issues=[];
 if(!runOK)issues.push(done?'CI_NOT_SUCCESSFUL':'RUN_NOT_COMPLETED');
 if(candidates.length!==1)issues.push('APK_ARTIFACT_COUNT_INVALID');
 else if(!candidate)issues.push('APK_ARTIFACT_PROVENANCE_INVALID');
 if(!ciVerified)issues.push('SIGNED_APK_VERIFICATION_LOG_MISSING');
 const state=artifact?'CI_VERIFIED_APK_ARTIFACT':runOK?'VERIFICATION_REQUIRED':done?'FAILED_OR_INCOMPLETE':'RUNNING';
 return {schema_version:1,variant,run_id:run.id,commit_sha:run.head_sha,branch:run.head_branch,
  run_url:'https://github.com/'+REPO+'/actions/runs/'+run.id,status:run.status,conclusion:run.conclusion??null,
  run_attempt:run.run_attempt??null,version_code:t.version_code,version_name:t.version_name,package:t.package,abi:'arm64-v8a',
  expected_certificate_sha256:t.certificate_sha256,jobs:mapping,failures,state,issues,
  ci_verified_log_observed:ciVerified,independently_inspected_apk_bytes:false,artifact,build_started_by_reporter:false};
}

export async function readRunReport(api,config,variant,runId,logs={},logProvider=null){
 if(!safeInt(runId))fail('INVALID_RUN_ID');
 const base='/actions/runs/'+runId;
 const [run,jobs,artifacts]=await Promise.all([api.get(base),api.get(base+'/jobs?per_page=100'),api.get(base+'/artifacts?per_page=100')]);
 assert(jobs?.total_count===jobs?.jobs?.length&&artifacts?.total_count===artifacts?.artifacts?.length,'PAGINATION_REQUIRED');
 const supplied=logProvider?await logProvider(run,jobs.jobs):logs;
 return buildReport({run,jobs:jobs.jobs,artifacts:artifacts.artifacts,config,variant,logs:supplied});
}

export class ReadOnlyGitHub{
 constructor(token=null,transport=fetch){this.token=token;this.transport=transport;}
 async get(path){
  assert(/^\/actions\/runs\/[1-9][0-9]*(?:\/(?:jobs|artifacts)\?per_page=100)?$/.test(path),'URL_DENIED');
  const headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'Zavro-Report/0.4'};
  if(this.token)headers.Authorization='Bearer '+this.token;
  let response;
  try{response=await this.transport(BASE+path,{method:'GET',headers,redirect:'error',signal:AbortSignal.timeout(12000)});}catch{fail('NETWORK_FAILURE');}
  if(response.status!==200)fail('API_HTTP_'+response.status);
  try{return await response.json();}catch{fail('BAD_GITHUB_JSON');}
 }
}

async function main(){
 const argv=process.argv.slice(2);
 const withLogs=argv.length===5&&argv[4]==='--with-ci-logs';
 if((argv.length!==4&&!withLogs)||argv[0]!=='--run'||!/^[1-9][0-9]*$/.test(argv[1])||argv[2]!=='--variant')fail('USAGE: --run ID --variant clean|mpv [--with-ci-logs]');
 const conf=JSON.parse(await fs.readFile(new URL('./build-control.json',import.meta.url),'utf8'));
 if(withLogs&&!process.env.GH_TOKEN)fail('GH_TOKEN_REQUIRED_FOR_LOGS');
 const logProvider=withLogs?async(_run,jobs)=>{
  const build=jobs.find(j=>j.name==='build'&&Number.isSafeInteger(j.id)&&j.id>0);
  if(!build)return {};
  try{
   const data=execFileSync('gh',['api','repos/'+REPO+'/actions/jobs/'+build.id+'/logs'],{encoding:'utf8',timeout:12000,maxBuffer:16*1024*1024,stdio:['ignore','pipe','pipe']});
   return {[build.id]:data};
  }catch{fail('CI_LOG_FETCH_FAILED');}
 }:null;
 const report=await readRunReport(new ReadOnlyGitHub(process.env.GH_TOKEN||null),conf,argv[3],Number(argv[1]),{},logProvider);
 console.log(JSON.stringify(report,null,2));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 main().catch(e=>{console.error('ZAVRO_REPORT '+(e instanceof ReportError?e.code:'FAILED'));process.exitCode=1;});
}
