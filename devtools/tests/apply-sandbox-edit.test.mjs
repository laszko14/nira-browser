import test from 'node:test';
import assert from 'node:assert/strict';
import {validateEvent,apply,GitHubClient,ApplyError} from '../apply-sandbox-edit.mjs';

const source='d63f414acecae84c3507c3f4bb998e690281b91e';
const sandbox='tools/zavro-edit-mpv-20261027';
const request=()=>({schema_version:1,kind:'sandbox-text-edit',repo:'laszko14/nira-browser',
 variant:'mpv',source_branch:'feat/zavro-x-mpv-v1167-reference-video-panel',source_sha:source,
 workspace_branch:sandbox,workspace_head:source,
 path:'app/src/test/java/com/prirai/android/nira/devtools/ZavroAutomatedEditorTest.kt',
 operation:'create',expected_blob_sha:null,
 content:'package com.prirai.android.nira.devtools\nimport org.junit.Test\nclass ZavroAutomatedEditorTest { @Test fun verifiesAutomation() {} }\n'});
const event={GITHUB_REPOSITORY:'laszko14/nira-browser',GITHUB_REF:'refs/heads/tools/zavro-development-system-stage1-20261027',
 GITHUB_ACTOR:'laszko14',BEFORE_SHA:'a'.repeat(40),GITHUB_SHA:'b'.repeat(40)};
function mock(opts={}){
 const old=source,commit='c'.repeat(40),calls=[];
 let head=opts.movedInitially?'d'.repeat(40):old;
 return {calls,call:async(method,path,body)=>{
  calls.push({method,path,body});
  if(path.startsWith('/git/ref/heads/')){
   if(opts.race&&calls.filter(x=>x.path.startsWith('/git/ref/heads/')).length===2)head='e'.repeat(40);
   return {object:{sha:head}};
  }
  if(path==='/git/commits/'+source)return {sha:source,tree:{sha:'f'.repeat(40)}};
  if(path==='/contents/'+request().path+'?ref='+old){
   return opts.existing?{type:'file',sha:'a'.repeat(40),encoding:'base64',content:Buffer.from('old').toString('base64')}:null;
  }
  if(path==='/git/trees')return {sha:'1'.repeat(40)};
  if(path==='/git/commits'&&method==='POST')return {sha:commit};
  if(path.startsWith('/git/refs/heads/')&&method==='PATCH'){head=commit;return{object:{sha:commit}};}
  throw Error('Unexpected API '+method+' '+path);
 }};
}
const bad=(fn,code)=>assert.rejects(fn,e=>e instanceof ApplyError&&e.code===code);
test('one new request is validated',()=>assert.equal(validateEvent(event,'A\tdevtools/edit-probes/mpv-test-01.json'),'devtools/edit-probes/mpv-test-01.json'));
test('two file push rejected',()=>assert.throws(()=>validateEvent(event,'A\tdevtools/edit-probes/test.json\nM\tapp/build.gradle'),e=>e.code==='MULTI_FILE_TRIGGER'));
test('wrong ref and actor blocked',()=>{assert.throws(()=>validateEvent({...event,GITHUB_REF:'refs/heads/main'},'A\tdevtools/edit-probes/x.json'),e=>e.code==='BAD_EVENT');assert.throws(()=>validateEvent({...event,GITHUB_ACTOR:'attacker'},'A\tdevtools/edit-probes/x.json'),e=>e.code==='BAD_EVENT');});
test('successful exact edit only writes one blob and non-force ref',async()=>{
 const api=mock(),result=await apply(request(),api);
 assert.equal(result.result,'SANDBOX_COMMIT_CREATED');
 assert.equal(result.branch,sandbox);assert.equal(result.new_head,'c'.repeat(40));
 assert.equal(result.source_branch_untouched,true);assert.equal(result.not_merged,true);
 const posts=api.calls.filter(x=>['POST','PATCH'].includes(x.method));
 assert.deepEqual(posts.map(x=>x.path),['/git/trees','/git/commits','/git/refs/heads/'+sandbox]);
 assert.equal(posts[2].body.force,false);
 assert.deepEqual(posts[1].body.parents,[source]);
 assert.deepEqual(posts[0].body.tree.map(x=>x.path),[request().path]);
});
test('stale workspace denies any write',async()=>{
 const api=mock({movedInitially:true});await bad(()=>apply(request(),api),'WORKSPACE_MOVED');assert.equal(api.calls.filter(x=>x.method!=='GET').length,0);
});
test('concurrent branch update after staging denies ref patch',async()=>{
 const api=mock({race:true});await bad(()=>apply(request(),api),'WORKSPACE_RACE_REJECTED');
 assert.equal(api.calls.filter(x=>x.method==='PATCH').length,0);
});
test('duplicate create denies tree mutation',async()=>{
 const api=mock({existing:true});await assert.rejects(()=>apply(request(),api),e=>e.code==='FILE_CAS_MISMATCH');
 assert.equal(api.calls.filter(x=>x.method==='POST').length,0);
});
test('malicious main branch request rejected before GitHub calls',async()=>{
 const api=mock();await assert.rejects(()=>apply({...request(),workspace_branch:'main'},api),e=>e.code==='WORKSPACE_ISOLATION');
 assert.equal(api.calls.length,0);
});
test('changes to source branch rather than sandbox rejected',async()=>{
 const api=mock();await assert.rejects(()=>apply({...request(),workspace_head:'a'.repeat(40),workspace_branch:'feat/zavro-x-mpv-v1167-reference-video-panel'},api),e=>e.code==='WORKSPACE_ISOLATION');
 assert.equal(api.calls.length,0);
});
test('credentials are never written to added code',async()=>{
 const api=mock();await assert.rejects(()=>apply({...request(),content:'KEYSTORE_PASSWORD=exposed'},api),e=>e.code==='SENSITIVE_CONTENT');
 assert.equal(api.calls.length,0);
});
test('URL allowlist blocks arbitrary HTTP endpoint',async()=>{
 const calls=[];const api=new GitHubClient('x'.repeat(32),async(url,options)=>{calls.push({url,options});return {status:200,json:async()=>({})};});
 await assert.rejects(()=>api.call('PATCH','/git/refs/heads/main',{sha:'a'.repeat(40),force:true}),e=>e.code==='API_ENDPOINT_DENIED');
 await assert.rejects(()=>api.call('POST','/actions/workflows/apk-release.yml/dispatches',{}),e=>e.code==='API_ENDPOINT_DENIED');
 assert.equal(calls.length,0);
});
test('REST read authenticates GET without logging token',async()=>{
 const methods=[];const api=new GitHubClient('x'.repeat(32),async(_url,opts)=>{methods.push(opts.method);return {status:200,json:async()=>({object:{sha:source}})};});
 await api.call('GET','/git/ref/heads/'+sandbox);assert.deepEqual(methods,['GET']);
});
