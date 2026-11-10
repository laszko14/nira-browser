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
  const activeLine=load('docs/ai/ACTIVE_CLEAN_LINE.md');
  assert.equal(typeof activeLine,'string','active branch document required');
  const match=activeLine.match(/^- \*\*Jedyna gałąź do kolejnych etapów:\*\* `((?:feat|fix|chore)\/zavro-clean-[a-z0-9-]+)`\./m);
  assert.ok(match,'canonical active Clean branch declaration required');
  const branch=match[1];
  assert.ok(body[0].includes('`'+branch+'`'),'Project Source active branch mismatch');
  assert.ok(load('AGENTS.md').includes('`'+branch+'`'),'AGENTS.md active branch mismatch');
  assert.ok(load('docs/ai/STARTUP_PREFLIGHT.md').includes('`'+branch+'`'),'preflight active branch mismatch');
  assert.ok(load('docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md').includes('`'+branch+'`'),'project bootstrap active branch mismatch');
  assert.doesNotMatch(body[0],/feat\/zavro-clean-v3-20261101/,'retired V3 branch must not be presented as current');
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
test('a stale Project Source branch fails closed against canonical branch',()=>{
 const canonical=read('docs/ai/ACTIVE_CLEAN_LINE.md').match(/^- \*\*Jedyna gałąź do kolejnych etapów:\*\* `([^\`]+)`\./m)[1];
 assert.throws(()=>validateSources(p=>p.endsWith('ZAVRO_START.md')?
   read(p).replace(canonical,'feat/zavro-clean-v3-20261101'):read(p)),/active branch mismatch/);
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
