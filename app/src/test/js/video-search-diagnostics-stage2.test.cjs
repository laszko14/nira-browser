'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {TextEncoder} = require('node:util');
const root = '../../main/assets/extensions/zavro-video-search/';
const diagnostics = fs.readFileSync(path.join(__dirname, root, 'diagnostics.js'), 'utf8');
const content = fs.readFileSync(path.join(__dirname, root, 'content.js'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, root, 'manifest.json'), 'utf8'));
const uuid = '11111111-1111-4111-8111-111111111111';
const docId = 'a'.repeat(32);
const id = 'b'.repeat(32);

function harness(withContent = false, opts = {}) {
  let now = 10000, listener, disconnect;
  const sent = [], actions = [], timers = [];
  const url = new URL('https://films.example/search?q=demo');
  const marker = {
    closest(){return null;},
    getBoundingClientRect(){return {width:120,height:40};}
  };
  const samples = opts.samples || {};
  const page = {
    documentElement: {},
    querySelector(q) {
      if (opts.emptyMarker && q.includes('[data-search-empty=')) return marker;
      if (opts.busy && q.includes('[aria-busy=')) return marker;
      return samples[q] || null;
    },
    querySelectorAll(q){return opts.nodes?.[q] || [];},
    createTreeWalker: undefined,
  };
  const win = {top:null,addEventListener(evt,fn){actions.push({evt,fn});}};
  win.top = win;
  const port = {
    postMessage(m){sent.push(m);},
    onMessage:{addListener(fn){listener=fn;}},
    onDisconnect:{addListener(fn){disconnect=fn;}},
  };
  const context = {
    window:win,document:page,location:url,URL,Uint8Array,TextEncoder,
    crypto:{getRandomValues(x){x.fill(10);return x;}},
    browser:{runtime:{connectNative(){return port;}}},
    Date:{now(){return now;}},
    setTimeout(fn){timers.push(fn);return timers.length;},
    clearTimeout(){},
  };
  vm.runInNewContext(diagnostics,context);
  if(withContent) vm.runInNewContext(content,context);
  return {
    win,page,sent,url,actions,controller:win.__zavroVideoDiagnostics,
    dispatch:cmd=>listener(cmd), disconnect:()=>disconnect(),
    tick:ms=>{now+=ms;}, flush:()=>{for(const fn of timers.splice(0))fn();},
    observed:()=>{
      const x=win.__zavroVideoDiagnostics.read(0,
        sent.find(x=>x.event==='hello').documentId);
      return x?.events || [];
    },
  };
}

test('stage 2 adds no extension permissions and preserves content order',()=>{
  assert.deepEqual(manifest.permissions,[
    'nativeMessaging','nativeMessagingFromContent','geckoViewAddons'
  ]);
  assert.deepEqual(manifest.content_scripts[0].js,
    ['recognition.js','diagnostics.js','content.js']);
  assert.equal(manifest.content_scripts[0].all_frames,false);
});

test('inactive by default, rejected invalid sessions, and no automatic capture',()=>{
  const h=harness();
  assert.equal(h.controller.active(),false);
  assert.equal(h.controller.observe({stage:'FORM',decision:'SEEN',reason:'FORM_FOUND'}),false);
  assert.equal(h.controller.read(0,docId),null);
  assert.equal(h.controller.start('not-a-uuid',docId,0),false);
  assert.equal(h.controller.start(uuid,'wrong-document',0),false);
  assert.equal(h.controller.active(),false);
});

test('native binding gates diagnostics, old/wrong document and invalid key sets',()=>{
  const h=harness(true);
  const doc=h.sent[0].documentId;
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:doc,
    requestId:id,sessionId:uuid});
  assert.equal(h.controller.active(),false,'before bound must be ignored');
  h.dispatch({schemaVersion:1,event:'bound',documentId:doc});
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:'f'.repeat(32),
    requestId:id,sessionId:uuid});
  assert.equal(h.controller.active(),false);
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:doc,
    requestId:id,sessionId:uuid,debug:true});
  assert.equal(h.controller.active(),false);
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:doc,requestId:id,sessionId:uuid});
  assert.equal(h.sent.at(-1).status,'active');
  assert.equal(h.controller.active(),true);
  h.dispatch({schemaVersion:1,event:'diag_read',documentId:doc,
    requestId:id,cursor:-1});
  assert.equal(h.sent.at(-1).event,'diag_ack','invalid cursor yields no response');
  h.dispatch({schemaVersion:1,event:'diag_read',documentId:doc,
    requestId:id,cursor:0});
  assert.equal(h.sent.at(-1).event,'diag_events');
  h.disconnect();
  assert.equal(h.controller.active(),false);
  assert.equal(h.controller.read(0,doc),null);
});

test('read-only form, empty marker and pagination events never change primary result',()=>{
  const h=harness(true,{emptyMarker:true});
  const doc=h.sent[0].documentId;
  h.dispatch({schemaVersion:1,event:'bound',documentId:doc});
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:doc,requestId:id,sessionId:uuid});
  h.dispatch({schemaVersion:1,event:'inspect',documentId:doc,requestId:id});
  assert.equal(h.sent.at(-1).event,'inspection');
  h.dispatch({schemaVersion:1,event:'extract_batch',documentId:doc,requestId:id,
    resultsCardSelector:'.cards',startIndex:0,linkSelector:null,titleSelector:null,
    thumbnailSelector:null,durationSelector:null});
  assert.equal(h.sent.at(-1).event,'results');
  assert.equal(h.sent.at(-1).status,'no_results',
    'only an existing explicit empty marker proves no results');
  h.dispatch({schemaVersion:1,event:'paginate',documentId:doc,
    requestId:id,pagination:'INFINITE_SCROLL',nextPageSelector:null,
    resultsCardSelector:'.cards'});
  assert.equal(h.sent.at(-1).event,'page_action');
  assert.equal(h.sent.at(-1).status,'pending');
  h.dispatch({schemaVersion:1,event:'diag_read',documentId:doc,requestId:id,cursor:0});
  const events=h.sent.at(-1).batch.events;
  assert.equal(events.some(e=>e.stage==='FORM'),true);
  assert.equal(events.some(e=>e.reason==='EXPLICIT_VISIBLE_EMPTY_MARKER'),true);
  assert.equal(events.some(e=>e.reason==='PAGINATION_PENDING') ||
    h.controller.read(0,doc).events.length<4,true);
  assert.equal(events.every(e=>e.claim==='OBSERVATION'),true);
  assert.equal(h.sent.some(e=>e.event==='diag_events'),true);
});

test('empty cards with loading is UNCERTAIN/WAITING, never a real empty result',()=>{
  const h=harness(true,{busy:true});
  const doc=h.sent[0].documentId;
  h.dispatch({schemaVersion:1,event:'bound',documentId:doc});
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:doc,requestId:id,sessionId:uuid});
  h.dispatch({schemaVersion:1,event:'extract',documentId:doc,requestId:id,
    resultsCardSelector:'.cards'});
  assert.notEqual(h.sent.at(-1).status,'no_results');
  const batch=h.controller.read(0,doc);
  assert.equal(batch.events.some(e=>e.reason==='DYNAMIC_LOADING'),true);
  assert.equal(batch.events.some(e=>e.reason==='EXPLICIT_VISIBLE_EMPTY_MARKER'),false);
});

test('sensitive URLs, free text and bounded structural-only DOM evidence',()=>{
  const h=harness();
  assert.equal(h.controller.start(uuid,docId,0),true);
  assert.equal(h.controller.observe({stage:'CARDS',decision:'SEEN',reason:'test',
    fields:{
      PAGE_URL:'https://user:password@films.example/search?q=aurora&token=secret123&state=hidden',
      FILM_URL:'https://films.example/watch?film=4&lang=pl&access_token=mytoken',
      DETAIL:'Cookie: session=abc password=val Authorization: Bearer xyz987',
    }}),true);
  const bad={
    tagName:'INPUT',children:[],
    getAttribute(k){return k==='value'?'supersecret':k==='type'?'password':null;},
    closest(){return null;},
  };
  const nested={
    tagName:'DIV',children:[bad],
    closest(){return null;},
    getAttribute(k){return k==='class'?'results':null;},
    textContent:'TOPSECRET from a site'
  };
  const source={querySelector(selector) {
    if(selector.includes('[data-search-results]')) return nested;
    return null;
  }};
  h.controller.snapshot(docId,id,source);
  const result=h.controller.read(0,docId);
  const payload=JSON.stringify(result);
  for(const secret of ['secret123','mytoken','password@films','supersecret',
    'TOPSECRET','xyz987','session=abc']) {
    assert.equal(payload.includes(secret),false,'leaked '+secret);
  }
  assert.match(payload,/film=4/);
  assert.match(payload,/lang=pl/);
  assert.match(payload,/DOM_FRAGMENT/);
  assert.equal(payload.includes('<input'),false);
});

test('bounded serial, explicit drops, small bridge packets, and readable after stop',()=>{
  const h=harness();
  assert.equal(h.controller.start(uuid,docId,0),true);
  for(let i=0;i<310;i++) h.controller.observe({
    stage:'CARDS',decision:'SEEN',reason:'SEEN',
    fields:{DETAIL:'x'.repeat(6000),TITLE:'Episode '+i},
  });
  const first=h.controller.read(0,docId);
  assert.equal(first.attempted,310);
  assert.ok(first.dropped>0);
  assert.ok(first.captured<=256);
  assert.equal(first.limited,true);
  assert.ok(JSON.stringify(first).length<8000);
  assert.ok(first.events.length>0 && first.events.length<=4);
  let cursor=0, sequences=[];
  for(let i=0;i<130;i++){
    const batch=h.controller.read(cursor,docId);
    if(!batch.events.length)break;
    sequences.push(...batch.events.map(e=>e.sequence));
    cursor=batch.events.at(-1).sequence;
  }
  assert.equal(new Set(sequences).size,sequences.length);
  assert.equal(sequences.every((v,i)=>i===0||v>sequences[i-1]),true);
  assert.equal(h.controller.stop(uuid),true);
  assert.equal(h.controller.active(),false);
  assert.equal(h.controller.read(cursor,docId).stopped,true);
  h.controller.close();
  assert.equal(h.controller.read(0,docId),null);
});

test('SPA changes document correlation without presenting old document as current',()=>{
  const h=harness();
  h.controller.start(uuid,docId,0);
  h.controller.observe({stage:'FORM',decision:'SEEN',reason:'FIRST'});
  h.controller.route('c'.repeat(32),1,'https://films.example/results?q=aurora','SAME_ORIGIN_ROUTE_CHANGE');
  assert.equal(h.controller.read(0,docId),null);
  const next=h.controller.read(0,'c'.repeat(32));
  assert.equal(next.events.length,2);
  assert.equal(next.events[0].documentId,docId);
  assert.equal(next.events[1].documentId,docId);
  h.controller.observe({stage:'CARDS',decision:'UNCERTAIN',reason:'NEW_ROUTE'});
  assert.equal(h.controller.read(2,'c'.repeat(32)).events[0].documentId,'c'.repeat(32));
});

test('private DOM/bridge RAM is retired on disconnect and no sensitive storage APIs used',()=>{
  const h=harness(true);
  const doc=h.sent[0].documentId;
  h.dispatch({schemaVersion:1,event:'bound',documentId:doc});
  h.dispatch({schemaVersion:1,event:'diag_start',documentId:doc,requestId:id,sessionId:uuid});
  h.dispatch({schemaVersion:1,event:'inspect',documentId:doc,requestId:id});
  assert.ok(h.controller.read(0,doc).events.length);
  h.disconnect();
  assert.equal(h.controller.read(0,doc),null);
  assert.doesNotMatch(diagnostics,/\b(?:localStorage|sessionStorage|indexedDB|fetch|XMLHttpRequest)\s*[.(]/);
});
