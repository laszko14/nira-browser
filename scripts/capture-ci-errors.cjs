'use strict';
const fs=require('node:fs');
const REPO='laszko14/nira-browser',API='https://api.github.com/repos/'+REPO;
const SHA=/^[0-9a-f]{40}$/;
const BAD=new Set(['failure','timed_out','cancelled','skipped','action_required']);
const safe=x=>String(x||'').replace(/[\r\n\t]/g,' ').slice(0,160);
function guard(ok,message){if(!ok)throw Error('CI capture: '+message)}
function classify(run,jobs,sha,id){
  guard(run&&run.id===id&&run.repository?.full_name===REPO,'wrong run/repo');
  guard(SHA.test(sha)&&run.head_sha===sha,'stale run SHA');
  guard(Number.isSafeInteger(run.run_attempt)&&run.run_attempt>0,'attempt');
  guard(Array.isArray(jobs)&&jobs.length>0,'missing jobs');
  const seen=new Set(),failures=[];
  for(const job of jobs){
    guard(Number.isSafeInteger(job.id)&&!seen.has(job.id),'job id/duplicates');
    seen.add(job.id);
    if(/^(?:Verify required jobs and summarize evidence|Error learning evidence)\b/i.test(job.name||''))continue;
    if((job.name==='build'||job.name==='release')&&job.conclusion==='skipped')continue;
    guard(job.status==='completed','job not completed: '+safe(job.name));
    guard(job.conclusion==='success'||BAD.has(job.conclusion),'unexpected conclusion');
    if(job.conclusion==='success')continue;
    const step=(job.steps||[]).find(s=>s.conclusion==='failure'||s.conclusion==='timed_out');
    failures.push({event_id:'CI-'+id+'-'+run.run_attempt+'-'+job.id,kind:'CI_FAILURE',
      source_sha:sha,run_id:id,job_id:job.id,attempt:run.run_attempt,
      job:safe(job.name),conclusion:job.conclusion,
      first_failing_step:step?safe(step.name):null,
      evidence_url:'https://github.com/'+REPO+'/actions/runs/'+id,
      cause:'undetermined',state:'UNTRIAGED'});
  }
  return {schema:'zavro.ci-error-capture/1',repository:REPO,run_id:id,
    source_sha:sha,attempt:run.run_attempt,event:run.event,
    capture_status:failures.length?'FAILURES_OBSERVED':'NO_FAILURES_OBSERVED',
    failures,note:'Read-only job metadata only; no logs or secrets. Reconcile into docs/ai/ERROR_CASES.json during task.'};
}
async function request(url,token,fetcher){
  const r=await fetcher(url,{headers:{'Accept':'application/vnd.github+json',
    ...(token?{'Authorization':'Bearer '+token}:{})}});
  guard(r.ok,'GitHub API '+r.status);
  return r.json();
}
async function readOnce(id,sha,token,fetcher=globalThis.fetch){
  guard(Number.isSafeInteger(id)&&id>0&&SHA.test(sha),'invalid args');
  const run=await request(API+'/actions/runs/'+id,token,fetcher);
  let jobs=[],page=1;
  while(page<=20){
    const data=await request(API+'/actions/runs/'+id+'/jobs?filter=latest&per_page=100&page='+page,token,fetcher);
    guard(Array.isArray(data.jobs)&&Number.isSafeInteger(data.total_count),'bad job response');
    jobs.push(...data.jobs);
    guard(jobs.length<=data.total_count,'pagination overflow');
    if(jobs.length===data.total_count)return classify(run,jobs,sha,id);
    guard(data.jobs.length===100,'incomplete job page');
    page++;
  }
  guard(false,'pagination limit');
}
async function main(){
  const argv=process.argv.slice(2);
  guard(argv.length===3,'usage: node scripts/capture-ci-errors.cjs RUN_ID RUN_HEAD_SHA OUTPUT_FILE');
  const r=await readOnce(Number(argv[0]),argv[1],process.env.GH_TOKEN||'');
  fs.writeFileSync(argv[2],JSON.stringify(r,null,2)+'\n',{mode:0o600});
  console.log('CI learning: '+r.capture_status+' run='+r.run_id+' sha='+r.source_sha+
    ' attempt='+r.attempt+' observed='+r.failures.length);
}
if(require.main===module)main().catch(e=>{console.error(e.message);process.exitCode=1});
module.exports={classify,readOnce};
