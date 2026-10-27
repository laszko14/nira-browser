#!/usr/bin/env node
// Stage 5: planning + auditing only. No POST, no Gradle, no secret reading.
import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

export class SmartCIError extends Error {constructor(code){super(code);this.code=code;}}
const fail=code=>{throw new SmartCIError(code);};
const ensure=(v,code)=>{if(!v)fail(code);};
const SHA=/^[0-9a-f]{40}$/;
const RUN_TASKS=['js','lua','kotlin-unit','android-lint','android-build','android-instrumentation','apk-metadata'];
const FAST=new Set(['js','lua','apk-metadata']);
const repo='laszko14/nira-browser';
const cert='df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00';
const forbidden=/^(\.git\/|\.github\/secrets\/)|(^|\/)\.\.(\/|$)|[\u0000-\u001f\\]/;
function cleanPath(p){
 ensure(typeof p==='string'&&p.length>0&&p.length<=512&&!forbidden.test(p)&&!p.startsWith('/')&&!p.includes('//'),'UNSAFE_PATH');
 return p;
}
export function chooseTests(files,variant,config){
 ensure(variant==='clean'||variant==='mpv','INVALID_VARIANT');
 const target=config?.targets?.[variant];
 ensure(config?.repo===repo&&config?.mode==='preflight-only'&&config.execution_enabled===false&&target&&SHA.test(target.pinned_sha),'UNSAFE_CONFIG');
 ensure(Array.isArray(files)&&files.length>0&&files.length<=200,'CHANGE_LIST_REQUIRED');
 const seen=new Set(),groups=new Set(),reasons=[],flags=new Set();
 for(const item of files){
  const p=cleanPath(typeof item==='string'?item:item?.filename);
  const status=typeof item==='string'?'modified':item.status;
  ensure(['added','modified','removed','renamed'].includes(status),'UNEXPECTED_DIFF_STATUS');
  ensure(!seen.has(p),'DUPLICATE_PATH');seen.add(p);
  const prev=typeof item==='object'&&item.previous_filename?cleanPath(item.previous_filename):null;
  const names=prev?[p,prev]:[p];
  if(status==='renamed'&&!prev)fail('RENAME_SOURCE_REQUIRED');
  for(const name of names){
   if(/^docs\/|\.md$/.test(name)||/^README|^LICENSE$/.test(name)){groups.add('documentation');continue;}
   if(/^app\/src\/test\/js\/.*\.(cjs|mjs|js)$/.test(name)){groups.add('js-tests');flags.add('js');continue;}
   if(/^app\/src\/(main|test|androidTest)\/.*\.(js|cjs|mjs)$/.test(name)||/^mozilla\/.*\.(js|cjs|mjs)$/.test(name)){groups.add('js-runtime');flags.add('js');flags.add('kotlin-unit');flags.add('android-build');continue;}
   if(/\.lua$/.test(name)){groups.add('lua');flags.add('lua');if(!/^app\/src\/test\//.test(name))flags.add('android-build');continue;}
   if(/^app\/src\/androidTest\/.*\.(kt|java)$/.test(name)){groups.add('instrumentation');flags.add('android-instrumentation');flags.add('android-build');continue;}
   if(/^app\/src\/test\/.*\.(kt|java)$/.test(name)){groups.add('kotlin-tests');flags.add('kotlin-unit');continue;}
   if(/^app\/src\/main\/.*\.(kt|java)$/.test(name)){groups.add('android-source');flags.add('kotlin-unit');flags.add('android-lint');flags.add('android-build');continue;}
   if(/^app\/src\/main\/.*\.(xml|json|html|css|png|svg|webp|ttf|otf)$/.test(name)){groups.add('resources');flags.add('android-lint');flags.add('android-build');continue;}
   if(/^(\.github\/workflows\/|gradle\/|scripts\/|app\/build\.gradle|build\.gradle|settings\.gradle|gradle\.properties|gradlew|app\/proguard-rules\.pro|app\/src\/main\/AndroidManifest\.xml)/.test(name)){
     groups.add('build-config');for(const t of RUN_TASKS)flags.add(t);continue;
   }
   if(/^devtools\/|^docs\/dev-system\//.test(name)){groups.add('devtools');flags.add('js');continue;}
   groups.add('unknown');for(const t of RUN_TASKS)flags.add(t);
  }
 }
 // Unknown or build tool changes always require all test families to preserve safety.
 if(groups.has('unknown'))reasons.push('UNKNOWN_CHANGE_FULL_MATRIX');
 if(groups.has('build-config'))reasons.push('BUILD_CONFIG_FULL_MATRIX');
 if(groups.has('android-source'))reasons.push('PRODUCTION_KOTLIN_REQUIRES_ANDROID_VALIDATION');
 if(groups.has('js-runtime'))reasons.push('RUNTIME_JS_REQUIRES_INTEGRATION_VALIDATION');
 if(groups.has('documentation')&&flags.size===0)reasons.push('DOCUMENTATION_ONLY');
 if(variant==='mpv'&&flags.has('android-build'))flags.add('lua'); // packaging/mpv handoff protection
 if(flags.has('android-build'))flags.add('apk-metadata');
 const tasks=RUN_TASKS.filter(t=>flags.has(t));
 return {schema_version:1,variant,commit_sha:target.pinned_sha,groups:[...groups].sort(),tasks,
   fast_checks:tasks.filter(t=>FAST.has(t)),
   requires_explicit_android_approval:tasks.some(t=>!FAST.has(t)),
   android_tasks_blocked:true,approved_android_runs:0,
   skip_full_android_only_if_no_android_tasks:!tasks.some(t=>!FAST.has(t)),
   changed_file_count:files.length,reasons,selection_kind:groups.has('unknown')?'FULL_FAIL_CLOSED':'SELECTIVE_PLAN'};
}
export function validateProject({variant,config,appGradle,workflow,gradleProperties,head}){
 ensure(config?.repo===repo&&config.mode==='preflight-only'&&config.execution_enabled===false,'UNSAFE_CONFIG');
 const target=config.targets?.[variant];
 ensure(target&&SHA.test(target.pinned_sha)&&head===target.pinned_sha,'HEAD_MOVED');
 ensure(typeof appGradle==='string'&&typeof workflow==='string'&&typeof gradleProperties==='string','MISSING_SOURCE');
 const expectPackage=variant==='clean'?'com.prirai.android.nira':'com.prirai.android.nira.mpv';
 const baseCode=target.version_code-1000;
 ensure(new RegExp('applicationId\\s*=\\s*["\\x27]'+expectPackage.replace(/\./g,'\\.')+'["\\x27]').test(appGradle),'APPLICATION_ID');
 ensure(new RegExp('\\bversionCode\\s*=\\s*'+baseCode+'\\b').test(appGradle),'VERSION_CODE');
 ensure(appGradle.includes('versionName = "'+target.version_name+'"'),'VERSION_NAME');
 if(variant==='clean')ensure(/applicationIdSuffix\s*=\s*['"]\.debug['"]/.test(appGradle),'DEBUG_PACKAGE');
 else ensure(!/applicationIdSuffix\s*=\s*['"]\.debug['"]/.test(appGradle),'MPV_PACKAGE');
 ensure(/include\(testAbi \?: 'arm64-v8a'\)/.test(appGradle)||/include\(discoveryAbi \?: 'arm64-v8a'\)/.test(appGradle),'ARM64_SPLIT');
 ensure(workflow.includes('EXPECTED_VERSION_CODE: "'+target.version_code+'"')&&workflow.includes('EXPECTED_VERSION_NAME: '+target.version_name),'CI_VERSION');
 ensure(workflow.includes('EXPECTED_PACKAGE: '+target.package)&&workflow.includes('EXPECTED_CERT_SHA256: '+cert),'CI_IDENTITY');
 ensure(workflow.includes('verify-update-apk.sh')&&workflow.includes('assembleDebug'),'CI_APK_VERIFY');
 ensure(workflow.includes('gradle/actions/setup-gradle@v5'),'CI_GRADLE_SETUP');
 const flags={cache:/^org\.gradle\.caching=true$/m.test(gradleProperties),
 parallel:/^org\.gradle\.parallel=true$/m.test(gradleProperties),
 configuration_cache:/^org\.gradle\.configuration-cache=true$/m.test(gradleProperties)};
 ensure(Object.values(flags).every(Boolean),'GRADLE_CACHE_CONFIG_CHANGED');
 return {variant,sha:head,version_code:target.version_code,version_name:target.version_name,
  package:target.package,abi:'arm64-v8a',certificate_sha256:cert,
  gradle_features:flags,ci_jobs_in_parallel:['build','lint','test'],
  android_compilation_executed:false,cache_speedup_measured:false,static_audit:'PASS'};
}
export function historySummary(runs){
 ensure(Array.isArray(runs)&&runs.length>=2&&runs.length<=30,'HISTORY_REQUIRED');
 const selected=[];
 for(const run of runs){
  ensure(Number.isSafeInteger(run.run_id)&&run.run_id>0&&run.result==='success','INVALID_HISTORY_RUN');
  ensure(SHA.test(run.sha)&&Number.isFinite(run.elapsed_seconds)&&run.elapsed_seconds>0,'INVALID_HISTORY_DATA');
  ensure(run.jobs&&typeof run.jobs==='object','MISSING_JOB_TIMINGS');
  const jobs={};
  for(const job of ['build','test','lint']){
   const v=run.jobs[job];
   ensure(Number.isFinite(v)&&v>0,'INVALID_JOB_TIMING');jobs[job]=v;
  }
  selected.push({...run,jobs});
 }
 const sorted=key=>selected.map(x=>key==='overall'?x.elapsed_seconds:x.jobs[key]).sort((a,b)=>a-b);
 const median=arr=>arr.length%2?arr[(arr.length-1)/2]:(arr[arr.length/2-1]+arr[arr.length/2])/2;
 const output={samples:selected.length,run_ids:selected.map(x=>x.run_id),median_seconds:{overall:median(sorted('overall')),build:median(sorted('build')),test:median(sorted('test')),lint:median(sorted('lint'))},
  slowest_job_in_each_run:selected.map(x=>Object.entries(x.jobs).sort((a,b)=>b[1]-a[1])[0][0]),
  observed_gradle_cache_speedup:null,
  predicted_speedup_from_new_changes:null,
  optimization_measured:false};
 return output;
}
export class GitHubReader{
 constructor(token=null,transport=fetch){this.token=token;this.transport=transport;}
 async get(path){
  ensure(/^\/(git\/ref\/heads\/feat\/zavro[a-z0-9/-]+|contents\/(app\/build\.gradle|gradle\.properties|\.github\/workflows\/apk-release\.yml)\?ref=[a-f0-9]{40})$/.test(path),'API_PATH_DENIED');
  let res;
  const headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'};
  if(this.token)headers.Authorization='Bearer '+this.token;
  try{res=await this.transport('https://api.github.com/repos/'+repo+path,{method:'GET',headers,redirect:'error',signal:AbortSignal.timeout(12000)});}catch{fail('API_NETWORK_ERROR');}
  ensure(res.status===200,'API_HTTP_'+res.status);
  try{return await res.json();}catch{fail('API_BAD_JSON');}
 }
 async getSource(p,sha){
  const x=await this.get('/contents/'+p+'?ref='+sha);
  ensure(x.encoding==='base64'&&typeof x.content==='string'&&x.content.length<350000,'API_CONTENT_FORMAT');
  return Buffer.from(x.content.replace(/\s/g,''),'base64').toString('utf8');
 }
}
export async function liveAudit(config,variant,api){
 const t=config?.targets?.[variant];ensure(t&&SHA.test(t.pinned_sha),'INVALID_VARIANT');
 const current=await api.get('/git/ref/heads/'+t.branch);
 ensure(current?.object?.sha===t.pinned_sha,'HEAD_MOVED');
 const [appGradle,workflow,gradleProperties]=await Promise.all(['app/build.gradle','.github/workflows/apk-release.yml','gradle.properties'].map(p=>api.getSource(p,t.pinned_sha)));
 return validateProject({variant,config,appGradle,workflow,gradleProperties,head:current.object.sha});
}
async function main(){
 const args=process.argv.slice(2);
 ensure(args.length===2&&args[0]==='audit'&&['clean','mpv'].includes(args[1]),'USAGE_AUDIT_VARIANT');
 const cfg=JSON.parse(await fs.readFile(new URL('./build-control.json',import.meta.url),'utf8'));
 const result=await liveAudit(cfg,args[1],new GitHubReader(process.env.GH_TOKEN||null));
 console.log(JSON.stringify(result,null,2));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 main().catch(e=>{console.error('SMART_CI '+(e instanceof SmartCIError?e.code:'FAILED'));process.exitCode=1;});
