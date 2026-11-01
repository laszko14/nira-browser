'use strict';
const {test}=require('node:test'), assert=require('node:assert/strict');
const {REPO,BRANCH,WORKFLOW,REQUIRED,evaluate,readOnce}=require('./ci-run-receipt.cjs');
const SHA='a'.repeat(40), OLD='b'.repeat(40);
const run={id:123456,run_attempt:2,repository:{full_name:REPO},path:WORKFLOW,
  head_branch:BRANCH,head_sha:SHA,event:'push',status:'completed',conclusion:'success'};
const jobs=REQUIRED.map((name,i)=>({id:100+i,name,run_attempt:2,status:'completed',
  conclusion:'success',html_url:'https://github.com/'+REPO+'/actions/runs/123456/job/'+i}));
const branch={commit:{sha:SHA}};
function receipt(r=run,j=jobs,b=branch,sha=SHA){return evaluate(r,j,b,sha)}
test('one-shot evidence accepts exactly all six latest successful jobs, matching SHA',()=>{
  const x=receipt();
  assert.equal(x.status,'CODE_VERIFIED');
  assert.equal(x.run_id,123456);
  assert.equal(x.attempt,2);
  assert.equal(x.jobs.length,6);
  assert.equal(x.device,'NOT_TESTED');
  assert.equal(x.apk,'NOT_BUILT');
  assert.equal(x.auto_resumes_chat,false);
});
test('missing, duplicated, skipped, cancelled, failed and prior-attempt jobs fail closed',()=>{
  for(const corrupted of [
    jobs.slice(1),
    [...jobs,jobs[0]],
    jobs.map((j,i)=>i===3?{...j,status:'completed',conclusion:'skipped'}:j),
    jobs.map((j,i)=>i===1?{...j,status:'completed',conclusion:'cancelled'}:j),
    jobs.map((j,i)=>i===4?{...j,status:'completed',conclusion:'failure'}:j),
    jobs.map((j,i)=>i===5?{...j,run_attempt:1}:j)
  ])assert.equal(receipt(run,corrupted).status,'BLOCKED');
});
test('unfinished and stale or unrelated CI never become a verified receipt',()=>{
  assert.equal(receipt({...run,status:'in_progress',conclusion:null},[]).status,'CI_PENDING');
  assert.equal(receipt(run,jobs,{commit:{sha:OLD}}).status,'STALE_HEAD');
  assert.equal(receipt({...run,head_sha:OLD}).status,'STALE_HEAD');
  assert.equal(receipt({...run,path:'.github/workflows/apk-release.yml'}).status,'BLOCKED');
  assert.equal(receipt({...run,event:'pull_request'}).status,'BLOCKED');
  assert.equal(receipt({...run,conclusion:'failure'}).status,'BLOCKED');
});
test('readOnce uses read-only GET, latest-only pagination, and checks live HEAD',async()=>{
  let calls=[], tokenSeen=0;
  const filler=Array.from({length:94},(_,i)=>({id:200+i,name:'other-'+i,status:'completed',conclusion:'success'}));
  const pages=[
    {total_count:101,jobs:[...jobs,...filler]},
    {total_count:101,jobs:[{name:'unrelated',status:'completed',conclusion:'success'}]}
  ];
  const fetcher=async (url,options)=>{
    calls.push(url);
    if(options?.method && options.method!=='GET')throw Error('Unexpected write');
    if(options?.headers.Authorization==='Bearer redacted-test-token')tokenSeen++;
    let payload;
    if(url.endsWith('/actions/runs/123456'))payload=run;
    else if(url.endsWith('&page=1'))payload=pages[0];
    else if(url.endsWith('&page=2'))payload=pages[1];
    else if(url.includes('/branches/'))payload=branch;
    else throw Error('Unexpected request URL');
    return {ok:true,status:200,json:async()=>payload};
  };
  const outcome=await readOnce({runId:123456,expectedSha:SHA,fetcher,token:'redacted-test-token'});
  assert.equal(outcome.status,'CODE_VERIFIED');
  assert.equal(calls.length,4);
  assert.ok(calls[1].includes('filter=latest&per_page=100&page=1'));
  assert.ok(calls[2].includes('filter=latest&per_page=100&page=2'));
  assert.equal(tokenSeen,4);
  assert.ok(!JSON.stringify(outcome).includes('redacted-test-token'));
});
test('pagination gaps and untrusted run identity cannot yield PASS',async()=>{
  const api='https://api.github.com/repos/'+REPO;
  let calls=[];
  const bad=async url=>{
    calls.push(url);
    if(url.endsWith('/actions/runs/123456'))return {ok:true,json:async()=>run};
    return {ok:true,json:async()=>({total_count:200,jobs:jobs})};
  };
  await assert.rejects(readOnce({runId:123456,expectedSha:SHA,fetcher:bad}),/Incomplete GitHub job page/);
  calls=[];
  const wrong=async url=>{
    calls.push(url);
    return {ok:true,json:async()=>({...run,repository:{full_name:'another/repo'}})};
  };
  const receipt=await readOnce({runId:123456,expectedSha:SHA,fetcher:wrong});
  assert.equal(receipt.status,'BLOCKED');
  assert.equal(calls.length,1);
  assert.throws(()=>evaluate(run,jobs,branch,'not-a-sha'),/Expected exact/);
});
