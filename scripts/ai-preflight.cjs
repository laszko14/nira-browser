'use strict';
const fs=require('node:fs');
const cp=require('node:child_process');
const path=require('node:path');
const crypto=require('node:crypto');
const ROOT=path.join(__dirname,'..');
const SHA=/^[0-9a-f]{40}$/;
const BRANCH=/^(?:feat|fix|chore)\/zavro-clean-[a-z0-9-]+$/;
const CORE=['AGENTS.md','docs/AI_HANDOFF.md','docs/ai/STARTUP_PREFLIGHT.md'];
const DEFAULT=['docs/ai/TASK_TEMPLATE.md','docs/ai/DEVELOPMENT_WORKFLOW.md',
  'docs/ai/TEST_POLICY.md','docs/ai/QUALITY_GATES.md'];
const SCOPE={
 DEFAULT:[],
 VIDEO_SEARCH:['docs/AGENT_REFERENCE.md','docs/VIDEO_SEARCH_AUDIT.md'],
 UI_GESTURES:['docs/AGENT_REFERENCE.md','docs/ai/TEST_PATTERNS.md'],
 DATA_PRIVACY:['docs/ai/TEST_PATTERNS.md','docs/ai/ERROR_LEARNING.md'],
 CI_TOOLING:['docs/ai/ERROR_LEARNING.md','docs/ai/ERROR_CASES.json',
   'docs/ai/CI_EVENTS_AND_RECEIPTS.md'],
 APK:['docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md','docs/ai/APK_RELEASE.md',
   'docs/ANDROID_SIGNING_SETUP.md']
};
function invalid(message){throw Error('PREFLIGHT_BLOCKED: '+message);}
function selectedPaths(scope){
  if(!Object.hasOwn(SCOPE,scope))invalid('unsupported scope');
  return [...new Set([...CORE,...DEFAULT,...SCOPE[scope]])];
}
function inspect(input,read){
  const {branch,sha,checkoutBranch,checkoutSha,scope}=input;
  if(!BRANCH.test(branch)||!SHA.test(sha))invalid('invalid branch or SHA');
  if(checkoutBranch!==branch||checkoutSha!==sha)invalid('checkout branch/SHA mismatch');
  const hashes={};
  for(const filename of selectedPaths(scope)){
    const data=read(filename);
    if(typeof data!=='string'||!data.trim())invalid('missing source '+filename);
    hashes[filename]=crypto.createHash('sha256').update(data,'utf8').digest('hex');
    if(filename==='AGENTS.md'&&
      (!data.includes('docs/AI_HANDOFF.md')||!data.includes('APK_CHAT_GITHUB_REVIEW_TRIGGER.md')))
      invalid('AGENTS.md is not an active policy');
    if(filename==='docs/AI_HANDOFF.md'&&
      (!data.includes('repo: laszko14/nira-browser')||
      !data.includes('product_line: Zavro Clean')||
      !data.split(/\r?\n/).some(l=>l==='branch: '+branch)||
      !/^observed_head_sha: [0-9a-f]{40}$/m.test(data)))
      invalid('handoff does not match current branch');
    if(filename==='docs/ai/STARTUP_PREFLIGHT.md'&&!data.includes('SOURCE_SNAPSHOT_VERIFIED'))
      invalid('preflight policy missing');
  }
  return {status:'SOURCE_SNAPSHOT_VERIFIED',branch,sha,scope,paths:Object.keys(hashes),sha256:hashes,
    limitation:'Checkout/document bytes only; not proof of model comprehension or remote live HEAD'};
}
function main(argv=process.argv.slice(2),env=process.env){
  const ci=argv[0]==='--ci',args=ci?argv.slice(1):argv;
  if(args.length!==3)invalid('usage: [--ci] BRANCH FULL_SHA SCOPE');
  const [branch,sha,scope]=args;
  let checkoutBranch,checkoutSha;
  try {
    checkoutSha=cp.execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim();
    checkoutBranch=ci?env.GITHUB_REF_NAME:
      cp.execFileSync('git',['symbolic-ref','--short','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim();
  }catch(e){invalid('git checkout unavailable');}
  if(ci&&(env.GITHUB_SHA!==sha||!['push','workflow_dispatch'].includes(env.GITHUB_EVENT_NAME)))
    invalid('unexpected CI event/ref/SHA');
  const result=inspect({branch,sha,checkoutBranch,checkoutSha,scope},
    p=>{try{return fs.readFileSync(path.join(ROOT,p),'utf8')}catch{return null}});
  process.stdout.write(JSON.stringify(result)+'\n');
}
if(require.main===module){try{main()}catch(e){console.error(e.message);process.exitCode=1}}
module.exports={inspect,selectedPaths,main,SCOPE};
