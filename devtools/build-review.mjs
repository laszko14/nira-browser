#!/usr/bin/env node
// Stage 9A, signed-build readiness: READ ONLY. Deliberately no tag, POST, Gradle or APK.
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {GitHubReadAPI,planBuild} from './build-control.mjs';

const REPO='laszko14/nira-browser';
const TOOL_BRANCH='tools/zavro-development-system-stage1-20261027';
const SHA=/^[0-9a-f]{40}$/;
const KEYS=['branch','build_requested','kind','repo','schema_version','sha','variant'];
export class ReviewError extends Error {constructor(code){super(code);this.code=code;}}
function ensure(x,code){if(!x)throw new ReviewError(code);}
export function validateReview(request,config,file){
 ensure(request&&!Array.isArray(request)&&typeof request==='object'&&Object.keys(request).sort().join(',')===KEYS.join(','),'FIELDS');
 ensure(request.schema_version===1&&request.kind==='build-readiness-review'&&request.repo===REPO&&request.build_requested===false,'NOT_READ_ONLY');
 ensure(['clean','mpv'].includes(request.variant),'VARIANT');
 const t=config?.targets?.[request.variant];
 ensure(config.repo===REPO&&config.mode==='preflight-only'&&config.execution_enabled===false&&config.create_refs_enabled===false&&config.allow_releases===false,'CONFIG');
 ensure(t&&request.branch===t.branch&&request.sha===t.pinned_sha&&SHA.test(request.sha),'PIN');
 ensure(/^devtools\/build-review-probes\/[a-z0-9-]+\.json$/.test(file),'PATH');
 return {variant:request.variant,branch:request.branch,sha:request.sha};
}
export function validateTrigger(env,diff){
 ensure(env.GITHUB_REPOSITORY===REPO&&env.GITHUB_REF==='refs/heads/'+TOOL_BRANCH&&env.GITHUB_ACTOR==='laszko14','EVENT');
 ensure(SHA.test(env.BEFORE_SHA??'')&&SHA.test(env.GITHUB_SHA??''),'SHA');
 const lines=String(diff).trim().split('\n').filter(Boolean);
 ensure(lines.length===1,'SINGLE_ADDITION');
 const parts=lines[0].split('\t');
 ensure(parts.length===2&&parts[0]==='A'&&/^devtools\/build-review-probes\/[a-z0-9-]+\.json$/.test(parts[1]),'UNSAFE_CHANGE');
 return parts[1];
}
export async function review(request,config,api){
 const plan=await planBuild(config,request,api);
 ensure(plan.build_started===false&&plan.dispatch_enabled===false,'UNSAFE_PLAN');
 return {...plan,stage:'READ_ONLY_BUILD_REVIEW',owner_approved:false,actual_build_requested:false,
   tag_created:false,release_created:false,apk_created:false,
   ready_for_build:plan.decision==='READY_FOR_REVIEW'?'NEEDS_EXPLICIT_APPROVAL':'REVIEW_EXISTING_RUN',
   actual_dispatch_verified:false};
}
async function main(){
 const env=process.env;
 const diff=execFileSync('git',['diff','--name-status','--no-renames',env.BEFORE_SHA,env.GITHUB_SHA],{encoding:'utf8'});
 const file=validateTrigger(env,diff);
 const request=JSON.parse(fs.readFileSync(file,'utf8'));
 const config=JSON.parse(fs.readFileSync(new URL('./build-control.json',import.meta.url),'utf8'));
 validateReview(request,config,file);
 const result=await review(request,config,new GitHubReadAPI({token:env.GH_TOKEN||null}));
 console.log(JSON.stringify(result,null,2));
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 main().catch(e=>{console.error('BUILD_REVIEW_'+(e.code||'FAILED'));process.exitCode=1;});
}
