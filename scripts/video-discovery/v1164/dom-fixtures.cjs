'use strict';
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
// Reuse the unchanged v1163 DOM adapter without executing its main(). The only
// test-helper extension exposes sanitized title/poster fields to the new oracle.
const filename=path.resolve(__dirname,'../fixtures.cjs');
let code=fs.readFileSync(filename,'utf8').replace(/main\(\)\.catch\([\s\S]*$/,'module.exports={domCase};');
code=code.replace('const frameIds=new Map(),messages=[];let nativeMessages=0;',
 'const frameIds=new Map(),messages=[];let nativeMessages=0,detectorStartedAt=null,firstResultMs=null;');
code=code.replace("if(message.event==='media_candidate')messages.push(message);",
 "if(message.event==='media_candidate'){messages.push(message);if(firstResultMs===null&&detectorStartedAt!==null)firstResultMs=performance.now()-detectorStartedAt;}");
code=code.replace('await install(page.mainFrame());','detectorStartedAt=performance.now();await install(page.mainFrame());');
code=code.replace('const result={id:c.id,films:c.films.length','const result={firstResultMs,metadata:messages.map(m=>({title:m.title,poster:m.posterUrl})),id:c.id,films:c.films.length');
const helper=new Module(filename,module);helper.filename=filename;helper.paths=module.paths;helper._compile(code,filename);
const cases=require('./frozen-cases.json')[process.env.DISCOVERY_SET||'DOM_B'];
(async()=>{const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox']});const results=[];
try{for(const c of cases){const result=await helper.exports.domCase(browser,{...c,films:c.expected.map(x=>[x])});
 const posterOK=!c.poster||result.metadata.some(m=>m.poster===c.poster);
 results.push({...result,posterOK,pass:result.post===c.expected.length&&result.falsePositives===0&&posterOK});}}
finally{await browser.close();}
const sum=k=>results.reduce((n,x)=>n+(x[k]||0),0);
const times=results.map(x=>x.firstResultMs).filter(x=>x!==null).sort((a,b)=>a-b);
const costs=results.flatMap(x=>x.cost);
const measured={preEligible:sum('preEligible'),pre:sum('pre'),post:sum('post'),falsePositives:sum('falsePositives'),duplicateMessages:sum('duplicateMessages'),urlAliasMergeGroups:sum('wrongMerges'),duplicateMaterials:sum('duplicateMaterials'),nativeMessages:sum('nativeMessages'),candidateMessages:sum('messages'),extraGet:sum('extraRequests'),firstResultMs:times,firstResultMedianMs:times.length?times[Math.floor(times.length/2)]:null,maxCallbackMs:Math.max(0,...costs.map(x=>x.maxCallbackMs)),initialMs:costs.map(x=>x.initialMs),mutations:costs.reduce((n,x)=>n+x.mutations,0),queries:costs.reduce((n,x)=>n+x.queries,0)};
const report={oracle:'URL sources; variants are not independent films. urlAliasMergeGroups is not a wrong-film merge metric.',measured,layer:'Chromium DOM, controlled extension adapters; not Gecko',set:process.env.DISCOVERY_SET||'DOM_B',expectedSources:results.reduce((n,x)=>n+x.films,0),detected:results.reduce((n,x)=>n+x.post,0),passed:results.filter(x=>x.pass).length,failed:results.filter(x=>!x.pass).length,cases:results};
if(process.env.DISCOVERY_REPORT)fs.writeFileSync(process.env.DISCOVERY_REPORT,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({set:report.set,expectedSources:report.expectedSources,detected:report.detected,passed:report.passed,failed:report.failed,cases:results.map(({id,post,posterOK,pass})=>({id,post,posterOK,pass}))},null,2));
if(!process.env.DISCOVERY_BASELINE)assert.equal(report.failed,0);
})().catch(e=>{console.error(e.message);process.exitCode=1;});
