#!/usr/bin/env node
// Fast CI only: diagnostic JavaScript, NEVER Android builds or signing.
import {execFileSync} from 'node:child_process';
import {readFileSync,appendFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
export class FastRequestError extends Error{}
const ok=(x,c)=>{if(!x)throw new FastRequestError(c);};
const KEYS=['branch','build_requested','intent','kind','repo','schema_version','sha','variant'];
export function checkFastRequest(r,c,file,env){
 ok(env.GITHUB_REPOSITORY==='laszko14/nira-browser'&&env.GITHUB_REF==='refs/heads/tools/zavro-development-system-stage1-20261027'&&env.GITHUB_ACTOR==='laszko14','UNAUTHORIZED_EVENT');
 ok(r&&typeof r==='object'&&!Array.isArray(r)&&Object.keys(r).sort().join(',')===KEYS.join(','),'BAD_FIELDS');
 ok(r.schema_version===1&&r.kind==='smart-ci-js'&&r.intent==='diagnostic-only'&&r.repo==='laszko14/nira-browser'&&r.build_requested===false,'NOT_DIAGNOSTIC');
 ok(['clean','mpv'].includes(r.variant),'BAD_VARIANT');
 const target=c?.targets?.[r.variant];
 ok(c.repo==='laszko14/nira-browser'&&c.mode==='preflight-only'&&c.execution_enabled===false&&target,'UNSAFE_CONFIG');
 ok(r.branch===target.branch&&r.sha===target.pinned_sha&&/^[a-f0-9]{40}$/.test(r.sha),'SHA_OR_BRANCH_MISMATCH');
 ok(/^devtools\/smart-ci-probes\/[a-z0-9-]+\.json$/.test(file),'BAD_FILE');
 return {variant:r.variant,branch:r.branch,sha:r.sha};
}
export function singleAddedFile(diff){
 const rows=diff.trim().split('\n').filter(Boolean);
 ok(rows.length===1,'MULTIPLE_CHANGED_FILES');
 const parts=rows[0].split('\t');
 ok(parts.length===2&&parts[0]==='A'&&/^devtools\/smart-ci-probes\/[a-z0-9-]+\.json$/.test(parts[1]),'NOT_NEW_FAST_PROBE');
 return parts[1];
}
function main(){
 const before=process.env.BEFORE_SHA??'',after=process.env.GITHUB_SHA??'';
 ok(/^[a-f0-9]{40}$/.test(before)&&/^[a-f0-9]{40}$/.test(after),'BAD_COMMIT_SHA');
 const diff=execFileSync('git',['diff','--name-status','--no-renames',before,after],{encoding:'utf8'});
 const file=singleAddedFile(diff);
 const config=JSON.parse(readFileSync(new URL('./build-control.json',import.meta.url),'utf8'));
 const result=checkFastRequest(JSON.parse(readFileSync(file,'utf8')),config,file,process.env);
 appendFileSync(process.env.GITHUB_OUTPUT,'variant='+result.variant+'\nbranch='+result.branch+'\nsha='+result.sha+'\n');
 console.log('SMART_CI_FAST_REQUEST_VALID: '+result.variant+' SHA='+result.sha+'; Android disabled');
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{main();}catch(e){console.error('SMART_CI_REQUEST_REJECTED: '+(e instanceof FastRequestError?e.message:'INVALID'));process.exitCode=1;}
}
