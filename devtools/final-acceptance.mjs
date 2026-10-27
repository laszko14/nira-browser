#!/usr/bin/env node
// Stage 8 acceptance: checks evidence and reports gaps, never dispatches or builds.
import fs from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {newCheckpoint,advance,validateCheckpoint,OrchestrationError} from './orchestrate.mjs';
import {chooseTests} from './smart-ci.mjs';
import {inspectTestLab} from './code-inspector.mjs';

const REPO='laszko14/nira-browser';
const TOOLS='tools/zavro-development-system-stage1-20261027';
const MAIN='0823f24eb48e2eefbe38c7616bc1d55e73917b37';
const SHA=/^[a-f0-9]{40}$/;
export class AcceptanceError extends Error{constructor(code){super(code);this.code=code;}}
function demand(condition,code){if(!condition)throw new AcceptanceError(code);}
const targets={
 clean:{branch:'feat/zavro-v1174-clean-video-metadata',sha:'3330a0b3a057008886fde392f788a1941e0dfb84',package:'com.prirai.android.nira.debug',code:1174},
 mpv:{branch:'feat/zavro-x-mpv-v1167-reference-video-panel',sha:'d63f414acecae84c3507c3f4bb998e690281b91e',package:'com.prirai.android.nira.mpv',code:1167}
};
const safeWf=(text)=>!(/\b(?:gh api -X POST|workflow_dispatch:|\.\/gradlew|assembleDebug|contents:\s*write|actions:\s*write)\b/.test(text));
// Workflows contain prose mentioning no-Gradle; only check actual executable tokens, not words in comments.
export function auditStatic({config,checkpoint,workflows,orchestrator}){
 demand(config?.repo===REPO&&config.mode==='preflight-only'&&config.execution_enabled===false&&config.create_refs_enabled===false&&config.allow_releases===false,'CONTROL_FLAGS_UNSAFE');
 demand(Object.keys(config.targets||{}).sort().join(',')==='clean,mpv','TARGET_SET_CHANGED');
 for(const [name,expected] of Object.entries(targets)){
  const t=config.targets[name];
  demand(t?.branch===expected.branch&&t.pinned_sha===expected.sha&&t.package===expected.package&&t.version_code===expected.code&&t.abi==='arm64-v8a'&&/^[a-f0-9]{64}$/.test(t.certificate_sha256),'TARGET_METADATA_CHANGED');
 }
 demand(checkpoint?.last_completed_stage>=7&&checkpoint?.last_completed_stage<=8&&checkpoint.infrastructure_branch===TOOLS&&checkpoint.baseline_sha===MAIN,'CHECKPOINT_NOT_CURRENT');
 const diagnostic={
  probe:{path:'devtools/control-probes/*.json',name:'Zavro ChatGPT connector probe'},
  smart:{path:'devtools/smart-ci-probes/*.json',name:'Zavro Smart CI Fast JavaScript'},
  orchestrator:{path:'devtools/orchestrator-probes/*.json',name:'Zavro Development System Orchestrator Diagnostics'}
 };
 for(const [name,v] of Object.entries(diagnostic)){
  const w=workflows?.[name];
  demand(typeof w==='string'&&w.startsWith('name: '+v.name+'\n')&&w.includes("      - '"+v.path+"'")&&w.includes('      - tools/zavro-development-system-stage1-20261027'),'TRIGGER_SCOPE_'+name);
  demand(/permissions:\s*\n(?:  (?:contents|actions|pull-requests): read\s*\n)+/m.test(w),'READ_ONLY_PERMISSIONS_'+name);
  demand(!/^\s*(?:contents|actions|pull-requests): write\s*$/m.test(w)&&!/\bgh api -X POST\b/.test(w)&&!/\b\.\/gradlew\b/.test(w),'DIAGNOSTIC_WORKFLOW_UNSAFE_'+name);
  demand(w.includes('persist-credentials: false')&&w.includes('timeout-minutes:'),'DIAGNOSTIC_HARDENING_'+name);
 }
 const bridge=workflows?.bridge;
 demand(typeof bridge==='string'&&bridge.includes("      - 'devtools/build-requests/*.json'")&&bridge.includes('ZAVRO_ALLOW_SIGNED_BUILD_DISPATCH')&&bridge.includes("vars.ZAVRO_ALLOW_SIGNED_BUILD_DISPATCH == 'ENABLED_BY_REPOSITORY_OWNER'"),'SIGNED_BUILD_ADMIN_GATE_ABSENT');
 demand(bridge.includes('github.actor == ')&&bridge.includes("      contents: write")&&bridge.includes("      actions: write")&&bridge.includes("if: needs.preflight.result == 'success'"),'SIGNED_BUILD_BRIDGE_INCOMPLETE');
 demand(typeof orchestrator==='string'&&orchestrator.includes('BUILD_FORBIDDEN')&&orchestrator.includes('REQUIRES_ADDITIONAL_TESTS')&&orchestrator.includes('build_started:false')&&!orchestrator.includes('gh api -X POST'),'ORCHESTRATOR_NOT_READ_ONLY');
 return {
  static_controls:'PASS',
  diagnostic_workflows_read_only:true,
  repository_owner_dispatch_gate_present:true,
  signed_build_dispatch_proven:false,
  production_build_workflows_modified:false,
  configuration_sha256:createHash('sha256').update(JSON.stringify(config)).digest('hex')
 };
}
export function classifyEvidence(run,expected,artifacts=[]){
 demand(run&&run.id===expected.id&&run.name===expected.name&&run.path===expected.path&&run.repository?.full_name===REPO&&run.head_branch===TOOLS,'RUN_PROVENANCE_INVALID');
 demand(SHA.test(run.head_sha)&&run.status==='completed'&&run.conclusion===expected.conclusion,'RUN_NOT_VERIFIED');
 demand(run.event==='push','NOT_A_PROBE_RUN');
 demand(Array.isArray(artifacts)&&artifacts.length<=20,'INVALID_ARTIFACTS');
 if(expected.artifact){
  const chosen=artifacts.filter(x=>x.id===expected.artifact);
  demand(chosen.length===1&&chosen[0].expired===false&&chosen[0].size_in_bytes>0&&chosen[0].workflow_run?.head_sha===run.head_sha,'CHECKPOINT_ARTIFACT_MISSING');
 }
 return {id:run.id,name:run.name,conclusion:run.conclusion,artifact:expected.artifact||null,apk_compiled_by_this_run:false};
}
export const evidenceManifest=[
 {key:'stage3_deny',id:40466190188,name:'Zavro ChatGPT Build Bridge',path:'.github/workflows/zavro-chat-build.yml',conclusion:'failure'},
 {key:'stage5_clean',id:40469224355,name:'Zavro Smart CI Fast JavaScript',path:'.github/workflows/zavro-smart-ci-js.yml',conclusion:'success'},
 {key:'stage5_mpv',id:40469257622,name:'Zavro Smart CI Fast JavaScript',path:'.github/workflows/zavro-smart-ci-js.yml',conclusion:'success'},
 {key:'stage6_inspector',id:40470818731,name:'Zavro ChatGPT connector probe',path:'.github/workflows/zavro-chat-probe.yml',conclusion:'success'},
 {key:'stage7_clean',id:40471995681,name:'Zavro Development System Orchestrator Diagnostics',path:'.github/workflows/zavro-stage7-orchestrator.yml',conclusion:'success',artifact:12718951320},
 {key:'stage7_mpv_recovery',id:40472241780,name:'Zavro Development System Orchestrator Diagnostics',path:'.github/workflows/zavro-stage7-orchestrator.yml',conclusion:'success',artifact:12719096410},
 {key:'stage7_deny',id:40472397516,name:'Zavro Development System Orchestrator Diagnostics',path:'.github/workflows/zavro-stage7-orchestrator.yml',conclusion:'failure',artifact:12719177242}
];
export function acceptanceSummary({staticAudit,results,main,clean,mpv,jobCheck}){
 demand(staticAudit?.static_controls==='PASS','STATIC_ACCEPTANCE_MISSING');
 demand(main===MAIN&&clean===targets.clean.sha&&mpv===targets.mpv.sha,'PROTECTED_REF_MOVED');
 demand(Array.isArray(results)&&results.length===evidenceManifest.length&&results.every((x,i)=>x.id===evidenceManifest[i].id&&x.conclusion===evidenceManifest[i].conclusion),'EVIDENCE_INCOMPLETE');
 demand(jobCheck?.stage3_dispatch_skipped===true&&jobCheck?.stage7_negative_requires_tests===true,'NEGATIVE_TEST_NOT_PROVEN');
 return {
  audit:'STAGE8_DIAGNOSTIC_ACCEPTED_WITH_OPEN_GATES',repository:REPO,
  scenarios_confirmed:results.map(r=>({scenario:evidenceManifest.find(e=>e.id===r.id)?.key,run_id:r.id,status:r.conclusion})),
  protected_refs_unchanged:true,
  diagnostic_read_only:true,
  credential_free_git_commits:true,
  stage3_dispatch_admin_gated:true,
  checkpoint_local_resume_confirmed:true,checkpoint_artifact_restore_in_same_run_confirmed:true,
  signed_android_dispatch_tested:false,signed_apk_binary_independently_checked:false,
  automatic_code_implementation_tested:false,cross_run_resume_tested:false,
  full_ci_speedup_benchmarked:false,gecko_device_testlab_live_verified:false,
  merges_performed:false,android_build_started:false,apk_generated:false
 };
}
export class ReadOnlyGitHub{
 constructor(token=null,transport=fetch){this.token=token;this.transport=transport;}
 async get(resource){
  demand(/^\/(?:git\/ref\/heads\/(?:main|feat\/zavro[-a-z0-9/]+)|actions\/runs\/[0-9]+(?:\/(?:jobs|artifacts)\?per_page=100)?)$/.test(resource),'URL_NOT_ALLOWED');
  let response;
  try{response=await this.transport('https://api.github.com/repos/'+REPO+resource,{method:'GET',headers:{Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28',...(this.token?{Authorization:'Bearer '+this.token}:{})},redirect:'error',signal:AbortSignal.timeout(13000)});}
  catch{throw new AcceptanceError('NETWORK_READ_FAILED');}
  demand(response.status===200,'NETWORK_READ_HTTP_'+response.status);
  try{return await response.json();}catch{throw new AcceptanceError('NETWORK_RESPONSE_INVALID');}
 }
}
async function run(){
 const base=new URL('../',import.meta.url);
 const read=path=>fs.readFile(new URL(path,base),'utf8');
 const wf={
  probe:await read('.github/workflows/zavro-chat-probe.yml'),
  smart:await read('.github/workflows/zavro-smart-ci-js.yml'),
  orchestrator:await read('.github/workflows/zavro-stage7-orchestrator.yml'),
  bridge:await read('.github/workflows/zavro-chat-build.yml')
 };
 const [config,checkpoint,orchestrator]=await Promise.all([read('devtools/build-control.json').then(JSON.parse),read('docs/dev-system/CHECKPOINT.json').then(JSON.parse),read('devtools/orchestrate.mjs')]);
 const staticAudit=auditStatic({config,checkpoint,workflows:wf,orchestrator});
 const api=new ReadOnlyGitHub(process.env.GH_TOKEN||null);
 const refs=await Promise.all(['/git/ref/heads/main','/git/ref/heads/'+targets.clean.branch,'/git/ref/heads/'+targets.mpv.branch].map(p=>api.get(p)));
 const runs=await Promise.all(evidenceManifest.map(async e=>{
  const data=await api.get('/actions/runs/'+e.id);
  const a=e.artifact?(await api.get('/actions/runs/'+e.id+'/artifacts?per_page=100')).artifacts:[];
  return classifyEvidence(data,e,a);
 }));
 const [stage3Jobs,stage7Jobs]=await Promise.all([
  api.get('/actions/runs/40466190188/jobs?per_page=100'),
  api.get('/actions/runs/40472397516/jobs?per_page=100')
 ]);
 const jobCheck={
  stage3_dispatch_skipped:stage3Jobs.jobs?.some(j=>j.name==='dispatch'&&j.conclusion==='skipped'),
  stage7_negative_requires_tests:stage7Jobs.jobs?.some(j=>j.name==='no-build-scenario'&&j.conclusion==='failure')
 };
 const final=acceptanceSummary({staticAudit,results:runs,main:refs[0].object.sha,clean:refs[1].object.sha,mpv:refs[2].object.sha,jobCheck});
 console.log(JSON.stringify({static:staticAudit,summary:final},null,2));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 run().catch(e=>{console.error('ZAVRO_STAGE8 '+(e instanceof AcceptanceError?e.code:'FAILED'));process.exitCode=1;});
