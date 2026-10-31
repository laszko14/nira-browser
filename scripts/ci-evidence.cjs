'use strict';
const fs=require('node:fs');
function buildReport(env){
  for(const key of ['DOCS_RESULT','SHARED_RESULT','GITHUB_SHA','GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT','GITHUB_EVENT_NAME','GITHUB_REF'])
    if(!env[key])throw Error('Missing result context: '+key);
  const ok=env.DOCS_RESULT==='success'&&env.SHARED_RESULT==='success';
  const lines=['## CI evidence — Zavro','',
    'Run: '+env.GITHUB_RUN_ID+' / attempt '+env.GITHUB_RUN_ATTEMPT,
    'Ref/event: '+env.GITHUB_REF+' / '+env.GITHUB_EVENT_NAME,
    'Tested SHA: '+env.GITHUB_SHA,'',
    '| Required gate | Result |','|---|---|',
    '| process-docs | '+env.DOCS_RESULT+' |',
    '| shared-tests (4 child jobs) | '+env.SHARED_RESULT+' |',
    '','Overall: **'+(ok?'PASS':'FAIL / INCOMPLETE')+'**.',
    'Review the four child jobs separately; skipped/cancelled/missing are not PASS.',
    'Device: NOT_TESTED; APK: NOT_BUILT.',''];
  return {ok,text:lines.join('\n')};
}
if(require.main===module){
  const result=buildReport(process.env);
  process.stdout.write(result.text+'\n');
  if(process.env.GITHUB_STEP_SUMMARY)fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,result.text+'\n');
  if(!result.ok){process.stderr.write('::error::Required CI jobs did not all succeed\n');process.exitCode=1;}
}
module.exports={buildReport};
