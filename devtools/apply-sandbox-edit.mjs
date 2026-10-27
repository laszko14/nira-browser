#!/usr/bin/env node
// Controlled GitHub code editing: one immutable request, one isolated branch, one file, no merge/build.
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {validateEdit,makeEdit,applyEditResult,EditError} from './code-editor.mjs';

const REPO='laszko14/nira-browser';
const TOOLS='refs/heads/tools/zavro-development-system-stage1-20261027';
const HASH=/^[0-9a-f]{40}$/;
export class ApplyError extends Error {constructor(code){super(code);this.code=code;}}
const assert=(x,c)=>{if(!x)throw new ApplyError(c);};

export function validateEvent(env,raw){
 assert(env.GITHUB_REPOSITORY===REPO&&env.GITHUB_REF===TOOLS&&env.GITHUB_ACTOR==='laszko14','BAD_EVENT');
 assert(HASH.test(env.BEFORE_SHA||'')&&HASH.test(env.GITHUB_SHA||''),'BAD_EVENT_SHA');
 const rows=String(raw).trim().split('\n').filter(Boolean);
 assert(rows.length===1,'MULTI_FILE_TRIGGER');
 const match=/^A\t(devtools\/edit-probes\/[a-z0-9-]+\.json)$/.exec(rows[0]);
 assert(match,'BAD_TRIGGER_PATH');
 return match[1];
}
export class GitHubClient{
 constructor(token,transport=fetch){assert(typeof token==='string'&&token.length>20,'MISSING_TOKEN');this.token=token;this.transport=transport;}
 async call(method,path,body){
  assert(['GET','POST','PATCH'].includes(method),'INVALID_METHOD');
  assert(/^\/(?:git\/ref\/heads\/tools\/zavro-edit-(?:clean|mpv)-20261027|git\/commits\/[a-f0-9]{40}|git\/trees|git\/commits|git\/refs\/heads\/tools\/zavro-edit-(?:clean|mpv)-20261027|contents\/(?:app\/src\/(?:main\/java\/com\/prirai\/android\/nira\/media\/[A-Za-z]+\.kt|test\/java\/com\/prirai\/android\/nira\/devtools\/[A-Za-z]+Test\.kt|test\/java\/com\/prirai\/android\/nira\/media\/MediaRecentDeduplicatorTest\.kt)|docs\/dev-system\/sandbox\/[a-z0-9-]+\.md)\?ref=[a-f0-9]{40})$/.test(path),'API_ENDPOINT_DENIED');
  const url='https://api.github.com/repos/'+REPO+path;
  const headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28',Authorization:'Bearer '+this.token,'Content-Type':'application/json'};
  let response;
  try{response=await this.transport(url,{method,headers,redirect:'error',signal:AbortSignal.timeout(20000),...(body===undefined?{}:{body:JSON.stringify(body)})});}
  catch{throw new ApplyError('NETWORK_FAILED');}
  if(response.status===404&&method==='GET')return null;
  assert(response.status>=200&&response.status<300,'HTTP_'+response.status);
  try{return await response.json();}catch{throw new ApplyError('BAD_RESPONSE');}
 }
}
export async function apply(request,api){
 const approved=validateEdit(request);
 const headPath='/git/ref/heads/'+request.workspace_branch;
 const current=await api.call('GET',headPath);
 assert(current?.object?.sha===request.workspace_head,'WORKSPACE_MOVED');
 // A verified source pin is a separate safety boundary from the editable branch.
 const source=await api.call('GET','/git/commits/'+request.source_sha);
 assert(source?.sha===request.source_sha&&HASH.test(source?.tree?.sha||''),'UNVERIFIED_SOURCE');
 const where='/contents/'+request.path+'?ref='+request.workspace_head;
 const currentFile=await api.call('GET',where);
 let found=null;
 if(currentFile){
  assert(currentFile.type==='file'&&currentFile.encoding==='base64'&&typeof currentFile.content==='string'&&HASH.test(currentFile.sha),'UNSUPPORTED_CONTENT');
  assert(currentFile.content.length<=240000,'FILE_TOO_LARGE');
  found={sha:currentFile.sha,type:'file',content:Buffer.from(currentFile.content.replace(/\s/g,''),'base64').toString('utf8')};
 }
 const plan=makeEdit(request,found,request.workspace_head);
 const parent=await api.call('GET','/git/commits/'+request.workspace_head);
 assert(parent?.sha===request.workspace_head&&HASH.test(parent?.tree?.sha||''),'PARENT_MISMATCH');
 // Do not mix a base-branch file with the snapshot; all edits derive from the checked workspace tree.
 const tree=await api.call('POST','/git/trees',{base_tree:parent.tree.sha,tree:[{path:plan.path,mode:'100644',type:'blob',content:plan.content}]});
 assert(HASH.test(tree?.sha||''),'TREE_CREATION_FAILED');
 const commit=await api.call('POST','/git/commits',{message:'test(sandbox): apply exact ChatGPT-reviewed '+request.variant+' file edit',tree:tree.sha,parents:[request.workspace_head]});
 assert(HASH.test(commit?.sha||'')&&commit.sha!==request.workspace_head,'COMMIT_CREATION_FAILED');
 // Another actor changing workspace since the original HEAD causes a non-fast-forward rejection.
 const recheck=await api.call('GET',headPath);
 assert(recheck?.object?.sha===request.workspace_head,'WORKSPACE_RACE_REJECTED');
 const updated=await api.call('PATCH','/git/refs/heads/'+request.workspace_branch,{sha:commit.sha,force:false});
 assert(updated?.object?.sha===commit.sha,'REF_UPDATE_FAILED');
 const observed=await api.call('GET',headPath);
 assert(observed?.object?.sha===commit.sha,'POST_WRITE_VERIFICATION_FAILED');
 return {...applyEditResult(plan,request.workspace_head,commit.sha),
  original_sha:request.source_sha,edited_file:request.path,changed_bytes:plan.bytes,
  new_sha256:plan.new_sha256,production_affected:plan.production_affected,required_checks:plan.required_checks,
  source_pinned:approved.source.sha,not_merged:true,apk_created:false};
}
async function main(){
 const env=process.env;
 const changed=execFileSync('git',['diff','--name-status','--no-renames',env.BEFORE_SHA,env.GITHUB_SHA],{encoding:'utf8'});
 const file=validateEvent(env,changed);
 const request=JSON.parse(readFileSync(file,'utf8'));
 validateEdit(request);
 const result=await apply(request,new GitHubClient(env.GH_TOKEN));
 console.log(JSON.stringify(result,null,2));
 if(env.GITHUB_OUTPUT){
  const {appendFileSync}=await import('node:fs');
  appendFileSync(env.GITHUB_OUTPUT,'sha='+result.new_head+'\nvariant='+request.variant+'\npath='+result.edited_file+'\n');
 }
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))
 main().catch(e=>{console.error('ZAVRO_EDIT_DENIED '+(e instanceof ApplyError||e instanceof EditError?e.code:'FAILED'));process.exitCode=1;});
