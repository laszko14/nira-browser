'use strict';
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const baseline=require('./lint-stage4-baseline.cjs');
const levels=['Fatal','Error','Warning','Hint'];
function loadBaseline(){
  if(baseline.schemaVersion!==1||baseline.sourceRunId!==41253701343)throw Error('Invalid baseline provenance');
  const data=JSON.parse(zlib.inflateSync(Buffer.from(baseline.compressed,'base64')).toString('utf8'));
  const filenames=Object.keys(data);
  const totals=Object.fromEntries(levels.map(s=>[s,0]));
  let groups=0;
  for(const rules of Object.values(data))for(const [name,count] of Object.entries(rules)){
    const severity=name.split(':')[0];groups++;
    if(!levels.includes(severity)||!Number.isSafeInteger(count)||count<1)throw Error('Invalid baseline group');
    totals[severity]+=count;
  }
  if(filenames.length!==baseline.fileCount||groups!==baseline.groupCount||
    levels.some(s=>totals[s]!==baseline.observed[s]))throw Error('Corrupt baseline counts');
  return data;
}
function attributes(source){
  const map=Object.create(null);
  for(const m of source.matchAll(/\b([A-Za-z][A-Za-z0-9]*)="([^"]*)"/g))map[m[1]]=m[2];
  return map;
}
function normalizeFile(file,workspace=process.env.GITHUB_WORKSPACE){
  if(typeof file!=='string'||!file)throw Error('Lint issue without file');
  const normalized=file.replace(/\\/g,'/');
  const isAbsolute=normalized.startsWith('/')||/^[A-Za-z]:\//.test(normalized);
  let p=normalized;
  if(isAbsolute){
    if(workspace){
      // Bind absolute lint locations to the real checkout, not a similarly named directory.
      const root=workspace.replace(/\\/g,'/').replace(/\/+$/,'');
      if(!root||!normalized.startsWith(root+'/'))throw Error('Unexpected lint file: '+file);
      p=normalized.slice(root.length+1);
    }else{
      // Offline reports: the final repository segment must directly own the lint path.
      const marker='/nira-browser/',index=normalized.lastIndexOf(marker);
      if(index<0)throw Error('Unexpected lint file: '+file);
      p=normalized.slice(index+marker.length);
    }
  }
  const allowed=/^(?:app|gradle)(?:\/|$)|^(?:build\.gradle|settings\.gradle|gradle\.properties)$/;
  if(!allowed.test(p)||p.split('/').some(part=>!part||part==='.'||part==='..'))
    throw Error('Unexpected lint file: '+file);
  return p;
}
function parseLint(xml){
  if(typeof xml!=='string'||!/<issues\b/.test(xml)||!/<\/issues>/.test(xml))throw Error('Invalid lint report XML');
  const counts=Object.create(null),totals=Object.fromEntries(levels.map(s=>[s,0]));
  const matches=[...xml.matchAll(/<issue\b([^>]+)>([\s\S]*?)<\/issue>/g)];
  if(matches.length!==(xml.match(/<issue\b/g)||[]).length)throw Error('Unparsed lint issue');
  for(const m of matches){
    const issue=attributes(m[1]),loc=m[2].match(/<location\b([^>]*?)\/>/);
    if(!issue.id||!levels.includes(issue.severity)||!loc)throw Error('Incomplete lint issue');
    const f=normalizeFile(attributes(loc[1]).file),key=issue.severity+':'+issue.id;
    if(!counts[f])counts[f]=Object.create(null);
    counts[f][key]=(counts[f][key]||0)+1;
    totals[issue.severity]++;
  }
  return {counts,totals};
}
function compareLint(actual,expected=loadBaseline()){
  const added=[];
  for(const [file,rules] of Object.entries(actual.counts))for(const [rule,count] of Object.entries(rules)){
    const old=expected[file]?.[rule]||0;
    if(count>old)added.push({file,rule,actual:count,baseline:old,delta:count-old});
  }
  return added.sort((a,b)=>b.delta-a.delta||a.file.localeCompare(b.file));
}
function summary(actual,added){
  const out=['## Zavro Android Lint — baseline regression gate','',
    'Reference: GitHub Actions #'+baseline.sourceRunId+', SHA '+baseline.sourceSha+'.','',
    '| Severity | This run | Frozen baseline |','|---|---:|---:|'];
  for(const severity of levels)out.push('| '+severity+' | '+actual.totals[severity]+' | '+baseline.observed[severity]+' |');
  out.push('',added.length?'**FAIL** — increased findings in '+added.length+' rule/file groups.':'**PASS** — no increased rule/file counts.',
    'Existing findings have not been fixed or hidden. This gate compares path + severity + rule + count, not exact message identity.','');
  for(const group of added.slice(0,20))out.push('- '+group.file+' '+group.rule+' +'+group.delta+' (now '+group.actual+', old '+group.baseline+')');
  if(added.length>20)out.push('- Additional groups: '+(added.length-20));
  return out.join('\n')+'\n';
}
function main(filename){
  const actual=parseLint(fs.readFileSync(filename,'utf8'));
  const added=compareLint(actual),report=summary(actual,added);
  process.stdout.write(report);
  if(process.env.GITHUB_STEP_SUMMARY)fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,report);
  if(added.length){process.stderr.write('::error::New Android lint findings in '+added.length+' groups\n');process.exitCode=1;}
}
if(require.main===module)main(process.argv[2]||path.join(__dirname,'../app/build/reports/lint-results-debug.xml'));
module.exports={loadBaseline,normalizeFile,parseLint,compareLint,summary};
