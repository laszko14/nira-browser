import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const CFG_REPO = 'laszko14/nira-browser';
class BuildControlError extends Error {constructor(code,why){super(code+": "+why);this.code=code;}}

function deny(code,why){throw new BuildControlError(code,why);}

function checkConfig(c){
 if(!c||c.schema_version!==1||c.repo!=="laszko14/nira-browser"||c.workflow!=="apk-release.yml"||c.mode!=="preflight-only")deny("CONFIG","Unexpected repository, workflow or mode");
 if(c.execution_enabled!==false||c.create_refs_enabled!==false||c.allow_releases!==false)deny("CONFIG","Stage 2 prohibits execution, tag creation and releases");
 if(!c.targets||Object.keys(c.targets).sort().join(",")!=="clean,mpv")deny("CONFIG","Exactly two variants required");
 for(const variant of ["clean","mpv"]){
  const t=c.targets[variant];
  if(!t||typeof t.branch!=="string"||!/^feat\/zavro[-a-z0-9/]+$/.test(t.branch))deny("CONFIG","Unsafe branch");
  if(!/^[a-f0-9]{40}$/.test(t.pinned_sha||""))deny("CONFIG","Invalid target pin");
  if(t.package!==({clean:"com.prirai.android.nira.debug",mpv:"com.prirai.android.nira.mpv"})[variant]||t.abi!=="arm64-v8a")deny("CONFIG","Invalid variant identity");
  if(!Number.isInteger(t.version_code)||t.version_code<1000||!/^0\.5\.22-alpha\d+-(clean|mpv\d+)$/.test(t.version_name||""))deny("CONFIG","Invalid version");
  if(!/^[a-f0-9]{64}$/.test(t.certificate_sha256||""))deny("CONFIG","Invalid certificate fingerprint");
 }
 return c;
}

function checkRequest(c,r){
 checkConfig(c);
 if(!r||!Object.hasOwn(c.targets,r.variant))deny("REQUEST","Choose clean or mpv");
 const t=c.targets[r.variant];
 if(r.branch!==t.branch)deny("BRANCH","Wrong variant branch");
 if(typeof r.sha!=="string"||!/^[a-f0-9]{40}$/.test(r.sha))deny("SHA","Commit must be a full SHA");
 if(r.sha!==t.pinned_sha)deny("STALE_PIN","SHA requires reviewed config refresh");
 if(r.tag!==undefined&&(r.tag!=="zavro-build-"+r.sha))deny("TAG","Dedicated immutable build tag required");
 return t;
}

function workflowGuard(text,t){
 if(typeof text!=="string"||text.length>120000||!/^name:\s*Android CI/m.test(text)||!/^  workflow_dispatch:\s*$/m.test(text))deny("WORKFLOW","Manual workflow trigger not verified");
 const build=text.match(/^  build:\s*\n([\s\S]*?)(?=^  (?:lint|test|release):|$(?![\s\S]))/m)?.[1];
 if(!build||!build.includes("assembleDebug")||!build.includes("scripts/verify-update-apk.sh"))deny("WORKFLOW","Signed ARM64 build verifier missing");
 for(const [key,value] of [["EXPECTED_PACKAGE",t.package],["EXPECTED_VERSION_CODE",String(t.version_code)],["EXPECTED_VERSION_NAME",t.version_name],["EXPECTED_CERT_SHA256",t.certificate_sha256]]){
  const candidates=[...build.matchAll(new RegExp("^\\s{6}"+key+":\\s*[\"']?([^\\s\"'#]+)","gm"))].map(m=>m[1]);
  if(candidates.length!==1||candidates[0]!==value)deny("WORKFLOW","Version/package/certificate expectation mismatch: "+key);
 }
 if(!/^[ \t]*if:\s*startsWith\(github\.ref, 'refs\/tags\/v'\)/m.test(text))deny("WORKFLOW","Release guard not verified");
 return true;
}

function classifyDuplicate(runs,sha,branch,id){
 if(!Array.isArray(runs))deny("RUNS","Invalid workflow-runs response");
 const selected=runs.filter(r=>r&&r.head_sha===sha&&r.head_branch===branch&&r.workflow_id===id);
 const running=selected.find(r=>["queued","in_progress","waiting","pending","requested"].includes(r.status));
 if(running)return {decision:"REUSE_RUNNING",run_id:running.id};
 const success=selected.find(r=>r.status==="completed"&&r.conclusion==="success");
 if(success)return {decision:"REUSE_SUCCESS",run_id:success.id};
 if(selected.length)return {decision:"REVIEW_PREVIOUS_FAILURE",run_id:selected[0].id};
 return {decision:"READY_FOR_REVIEW",run_id:null};
}

class GitHubReadAPI{
 constructor({token=null,transport=null}={}){this.token=token;this.transport=transport;}
 async get(apiPath){
  if(!apiPath.startsWith("/repos/laszko14/nira-browser/")||apiPath.includes("..")||/[\r\n]/.test(apiPath))deny("PATH","Outside allowed repository");
  const transport=this.transport||fetch;
  const headers={Accept:"application/vnd.github+json","X-GitHub-Api-Version":"2026-03-10","User-Agent":"Zavro-Build-Control/0.2"};
  if(this.token)headers.Authorization="Bearer "+this.token;
  let res;
  try{res=await transport("https://api.github.com"+apiPath,{method:"GET",headers,redirect:"error",signal:AbortSignal.timeout(12000)});}
  catch(e){deny("NETWORK","Read-only GitHub request failed");}
  if(res.status!==200)deny("GITHUB_HTTP","Read-only GitHub API status "+res.status);
  try{return await res.json();}catch(e){deny("GITHUB_DATA","Invalid JSON");}
 }
 async head(branch){const x=await this.get("/repos/laszko14/nira-browser/git/ref/heads/"+branch.split("/").map(encodeURIComponent).join("/"));return x?.object?.sha;}
 async workflowMetadata(){return this.get("/repos/laszko14/nira-browser/actions/workflows/apk-release.yml");}
 async workflowText(sha){
  const x=await this.get("/repos/laszko14/nira-browser/contents/.github/workflows/apk-release.yml?ref="+sha);
  if(x.encoding!=="base64"||typeof x.content!=="string")deny("WORKFLOW","Not a base64 workflow");
  return Buffer.from(x.content.replace(/\s/g,""),"base64").toString("utf8");
 }
 async runs(sha){const x=await this.get("/repos/laszko14/nira-browser/actions/workflows/apk-release.yml/runs?head_sha="+sha+"&per_page=100");return x.workflow_runs;}
}

async function planBuild(c,r,api){
 const t=checkRequest(c,r);
 if(await api.head(t.branch)!==r.sha)deny("HEAD_MOVED","Branch no longer points to pinned SHA");
 const meta=await api.workflowMetadata();
 if(meta?.state!=="active"||meta.path!==".github/workflows/apk-release.yml"||!Number.isInteger(meta.id))deny("WORKFLOW","Workflow not active on GitHub");
 workflowGuard(await api.workflowText(r.sha),t);
 const d=classifyDuplicate(await api.runs(r.sha),r.sha,r.branch,meta.id);
 return {variant:r.variant,branch:r.branch,sha:r.sha,application_id:t.package,version_code:t.version_code,version_name:t.version_name,abi:t.abi,workflow:"apk-release.yml",request_key:c.repo+":"+r.variant+":"+r.sha+":apk-release.yml",...d,build_started:false,dispatch_enabled:false};
}

async function dispatchBuild(c,r,api){checkRequest(c,r);deny("DISPATCH_DISABLED","Stage 2 has no POST/dispatch transport");}
export {BuildControlError,checkConfig,checkRequest,workflowGuard,classifyDuplicate,GitHubReadAPI,planBuild,dispatchBuild};
if (process.argv[1] && import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href) {
 const args=process.argv.slice(2);
 try {
  if (args.length!==7||args[0]!=='plan'||args[1]!=='--variant'||args[3]!=='--branch'||args[5]!=='--sha') deny('USAGE','plan --variant clean|mpv --branch BRANCH --sha FULL_SHA');
  const config=JSON.parse(await fs.readFile(new URL('./build-control.json',import.meta.url),'utf8'));
  const result=await planBuild(config,{variant:args[2],branch:args[4],sha:args[6]},new GitHubReadAPI({token:process.env.GH_TOKEN||null}));
  console.log(JSON.stringify(result,null,2));
 } catch(e) {console.error('BUILD_CONTROL '+(e.code||'ERROR')+': '+(e.message||'unknown error').replace(/Bearer\s+\S+/g,'Bearer [REDACTED]'));process.exitCode=1;}
}
