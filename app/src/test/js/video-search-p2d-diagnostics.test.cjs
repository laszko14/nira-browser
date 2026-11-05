'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const { TextEncoder }=require('node:util');
const content=fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'),'utf8');

async function check(url, matches={}) {
  const sent=[];
  let dispatch;
  const timers=[];
  const location=new URL(url);
  const document={
    querySelector(){return null;},
    querySelectorAll(s){return matches[s] || [];}
  };
  const win={top:null,addEventListener(){},innerHeight:700};
  win.top=win;
  const port={
    postMessage(m){sent.push(m);},
    onMessage:{addListener(fn){dispatch=fn;}},
    onDisconnect:{addListener(){}}
  };
  vm.runInNewContext(content,{
    window:win,document,location,URL,Uint8Array,TextEncoder,
    browser:{runtime:{connectNative(){return port;}}},
    crypto:{getRandomValues(bytes){bytes.fill(11);return bytes;}},
    setTimeout(fn){timers.push(fn);}
  });
  const doc=sent[0].documentId;
  dispatch({schemaVersion:1,event:'bound',documentId:doc});
  dispatch({schemaVersion:1,event:'validate',documentId:doc,
    requestId:'c'.repeat(32),inputSelector:'#q',resultsCardSelector:'.cards',
    submission:'FORM_SUBMIT',submitSelector:null,pagination:'NONE',
    nextPageSelector:null});
  for(let i=0;i<12;i++) {
    for(const fn of timers.splice(0))fn();
    await Promise.resolve();
  }
  return sent.filter(x=>x.event==='validation');
}

test('P2-D results-only document does not declare homepage search selector broken',async()=>{
  const result=await check('https://films.example/results?q=test');
  assert.equal(result.length,1);
  assert.equal(result[0].status,'unverified');
  assert.equal(result[0].reason,'results_only');
  assert.equal(result[0].attempts,3);
});

test('P2-D genuinely missing input on homepage stays diagnosable but not auto-repaired',async()=>{
  const result=await check('https://films.example/');
  assert.equal(result.length,1);
  assert.equal(result[0].status,'stale');
  assert.equal(result[0].reason,'input_missing');
  assert.equal(result[0].matchedCards,0);
});

test('P2-D diagnostics preserve strict native request scope',async()=>{
  const result=await check('https://films.example/search?query=example');
  assert.equal(result.length,1);
  const packet=result[0];
  assert.equal(packet.requestId,'c'.repeat(32));
  assert.equal(packet.documentId.length,32);
  assert.equal(Object.hasOwn(packet,'query'),false);
  assert.equal(Object.hasOwn(packet,'profileId'),false);
  assert.equal(Object.hasOwn(packet,'pageUrl'),false);
});

test('P2-D human login gate is blocked, never diagnosed as CSS failure',async()=>{
  const result=await check('https://films.example/login');
  assert.equal(result.length,1);
  assert.equal(result[0].status,'unsafe');
  assert.equal(result[0].reason,'human_gate');
  assert.equal(result[0].attempts,1);
});
