'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');
const dir='docs/ai/project-sources';
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const names=['ZAVRO_START.md','ZAVRO_FUNKCJE.md','ZAVRO_SYSTEM_V3.md'];
function validateSources(load=read){
  const body=names.map(name=>load(dir+'/'+name));
  for(const [i,b] of body.entries()){
    assert.ok(typeof b==='string'&&b.length>=300&&b.length<5000,names[i]+' size');
    assert.match(b,/AGENTS\.md/);
    assert.match(b,/AI_HANDOFF\.md/);
    assert.match(b,/GitHub/);
    assert.doesNotMatch(b,/\b(?:CI_PENDING\s*=\s*PASS|PREFLIGHT_BLOCKED\s*=\s*PASS)\b/);
    assert.doesNotMatch(b,/[0-9a-f]{40}/,'Project Sources must not freeze a live HEAD SHA');
  }
  assert.match(body[0],/feat\/zavro-clean-v3-20261101/);
  assert.match(body[0],/STARTUP_PREFLIGHT\.md/);
  assert.match(body[1],/GeckoView/);
  assert.match(body[1],/filmów/);
  assert.match(body[2],/ERROR_CASES\.json/);
  assert.match(body[2],/pull_request/);
  assert.ok(!body.some(b=>b.includes('workflow_mode=autonomous_task')),'no copied agent policy');
  return {files:names.length,status:'SOURCE_INDEX_CHECKED_NOT_INSTALLED'};
}
test('all three Project Sources stay short and avoid stale policy/SHA',()=>{
 assert.deepEqual(validateSources(),{files:3,status:'SOURCE_INDEX_CHECKED_NOT_INSTALLED'});
});
test('missing Project Source fails closed',()=>{
 assert.throws(()=>validateSources(p=>p.endsWith('ZAVRO_START.md')?null:read(p)));
});
test('frozen commit and fake PASS in sources fail',()=>{
 assert.throws(()=>validateSources(p=>p.endsWith('ZAVRO_START.md')?
   read(p)+'\n'+'f'.repeat(40):read(p)),/freeze/);
 assert.throws(()=>validateSources(p=>p.endsWith('ZAVRO_FUNKCJE.md')?
   read(p)+'\nCI_PENDING = PASS':read(p)));
});
module.exports={validateSources};
