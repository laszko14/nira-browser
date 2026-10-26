'use strict';
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
// Reuse the unchanged v1163 DOM adapter without executing its main(). The only
// test-helper extension exposes sanitized title/poster fields to the new oracle.
const filename=path.resolve(__dirname,'../fixtures.cjs');
let code=fs.readFileSync(filename,'utf8').replace(/main\(\)\.catch\([\s\S]*$/,'module.exports={domCase};');
code=code.replace('const result={id:c.id,films:c.films.length','const result={metadata:messages.map(m=>({title:m.title,poster:m.posterUrl})),id:c.id,films:c.films.length');
const helper=new Module(filename,module);helper.filename=filename;helper.paths=module.paths;helper._compile(code,filename);
const cases=require('./frozen-cases.json')[process.env.DISCOVERY_SET||'DOM_B'];
(async()=>{const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox']});const results=[];
try{for(const c of cases){const result=await helper.exports.domCase(browser,{...c,films:c.expected.map(x=>[x])});
 const posterOK=!c.poster||result.metadata.some(m=>m.poster===c.poster);
 results.push({...result,posterOK,pass:result.post===c.expected.length&&result.falsePositives===0&&posterOK});}}
finally{await browser.close();}
const report={layer:'Chromium DOM, controlled extension adapters; not Gecko',set:process.env.DISCOVERY_SET||'DOM_B',films:results.reduce((n,x)=>n+x.films,0),detected:results.reduce((n,x)=>n+x.post,0),passed:results.filter(x=>x.pass).length,failed:results.filter(x=>!x.pass).length,cases:results};
if(process.env.DISCOVERY_REPORT)fs.writeFileSync(process.env.DISCOVERY_REPORT,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({set:report.set,films:report.films,detected:report.detected,passed:report.passed,failed:report.failed,cases:results.map(({id,post,posterOK,pass})=>({id,post,posterOK,pass}))},null,2));
if(!process.env.DISCOVERY_BASELINE)assert.equal(report.failed,0);
})().catch(e=>{console.error(e.message);process.exitCode=1;});
