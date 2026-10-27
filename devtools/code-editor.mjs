#!/usr/bin/env node
// Safe Code Editor: exact patch planning for an isolated GitHub branch; no hidden mutation or build.
import {createHash} from 'node:crypto';
const REPO='laszko14/nira-browser';
const PINS={clean:{source_branch:'feat/zavro-v1174-clean-video-metadata',sha:'3330a0b3a057008886fde392f788a1941e0dfb84'},
  mpv:{source_branch:'feat/zavro-x-mpv-v1167-reference-video-panel',sha:'d63f414acecae84c3507c3f4bb998e690281b91e'}};
const SHA=/^[a-f0-9]{40}$/;
const KEYS=['content','expected_blob_sha','kind','operation','path','repo','schema_version','source_branch','source_sha','variant','workspace_branch','workspace_head'];
export class EditError extends Error{constructor(code){super(code);this.code=code;}}
const check=(v,code)=>{if(!v)throw new EditError(code);};
function sha256(s){return createHash('sha256').update(s,'utf8').digest('hex');}
export function allowedFile(path){
 check(typeof path==='string'&&path.length<256&&!path.includes('..')&&!path.includes('//')&&!path.includes('\\')&&!path.startsWith('/'),'UNSAFE_PATH');
 check(/^app\/src\/test\/java\/com\/prirai\/android\/nira\/devtools\/[A-Za-z][A-Za-z0-9]*Test\.kt$/.test(path)||/^docs\/dev-system\/sandbox\/[a-z0-9-]+\.md$/.test(path),'NOT_ALLOWLISTED');
 return path;
}
export function validateEdit(request){
 check(request&&typeof request==='object'&&!Array.isArray(request)&&Object.keys(request).sort().join(',')===KEYS.join(','),'FIELDS');
 check(request.schema_version===1&&request.kind==='sandbox-text-edit'&&request.repo===REPO,'REQUEST');
 check(['clean','mpv'].includes(request.variant),'VARIANT');
 const pin=PINS[request.variant];
 check(request.source_branch===pin.source_branch&&request.source_sha===pin.sha,'SOURCE_PIN');
 check(request.workspace_branch==='tools/zavro-edit-'+request.variant+'-20261027'&&SHA.test(request.workspace_head),'WORKSPACE_ISOLATION');
 check(request.operation==='create'||request.operation==='replace','OPERATION');
 allowedFile(request.path);
 check(typeof request.content==='string'&&request.content.length>=1&&request.content.length<=16000&&!request.content.includes('\0'),'CONTENT');
 check((request.expected_blob_sha===null&&request.operation==='create')||(request.operation==='replace'&&SHA.test(request.expected_blob_sha)),'BLOB_PRECONDITION');
 check(!/ANDROID_KEYSTORE_BASE64|KEYSTORE_PASSWORD|Bearer\s+[a-z0-9_-]{10,}|Authorization:/i.test(request.content),'SENSITIVE_CONTENT');
 return {source:pin,variant:request.variant};
}
export function makeEdit(request,found,liveWorkspaceHead){
 validateEdit(request);
 check(liveWorkspaceHead===request.workspace_head,'WORKSPACE_MOVED');
 const isCreate=request.operation==='create';
 check(isCreate?found===null:found&&typeof found==='object'&&found.sha===request.expected_blob_sha,'FILE_CAS_MISMATCH');
 check(isCreate||found.type==='file','UNSAFE_EXISTING_OBJECT');
 if(!isCreate){
  check(typeof found.content==='string'&&found.content.length<150000,'BAD_PREVIOUS_CONTENT');
  check(found.content!==request.content,'NO_CHANGE');
 }
 const oldContent=isCreate?'':found.content;
 check(request.content!==oldContent,'NO_CHANGE');
 return {operation:request.operation,branch:request.workspace_branch,parent:request.workspace_head,
   path:request.path,mode:'100644',type:'blob',content:request.content,
   old_sha256:isCreate?null:sha256(oldContent),new_sha256:sha256(request.content),
   bytes:Buffer.byteLength(request.content,'utf8'),production_affected:false,
   source_branch_untouched:true,requires_git_ref_cas:true,requires_independent_tests:true};
}
export function applyEditResult(plan,liveParent,headAfter){
 check(plan&&plan.requires_git_ref_cas===true,'INVALID_PLAN');
 check(liveParent===plan.parent,'RACE_PRECONDITION');
 check(SHA.test(headAfter)&&headAfter!==liveParent,'NO_COMMIT');
 return {result:'SANDBOX_COMMIT_CREATED',branch:plan.branch,new_head:headAfter,
   source_branch_untouched:true,android_build_started:false,
   compiled_verified:false,tests_required:true};
}
