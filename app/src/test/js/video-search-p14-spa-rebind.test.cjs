'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {TextEncoder} = require('node:util');

// The exact bundled extension, with a simulated isolated Gecko port.
const content = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');

function harness(initial = 'https://films.example/home') {
  let onPortMessage, rngCalls=0, scrolls=0;
  const messages=[], callbacks=new Map(), timers=[];
  const address=new URL(initial);
  const page={
    documentElement:{},
    querySelector() {return null;},
    querySelectorAll() {return [];}
  };
  const win={
    top:null, innerHeight:800,
    addEventListener(type, fn) {callbacks.set(type, fn);},
    scrollBy() {scrolls++;}
  };
  win.top=win;
  const port={
    onMessage:{addListener(fn){onPortMessage=fn;}},
    onDisconnect:{addListener(){}},
    postMessage(msg){messages.push(msg);}
  };
  vm.runInNewContext(content,{
    window:win,document:page,location:address,
    browser:{runtime:{connectNative(){return port;}}},
    crypto:{getRandomValues(array){array.fill(++rngCalls);return array;}},
    URL,Uint8Array,TextEncoder,
    setTimeout(fn){timers.push(fn);return timers.length;},
    clearTimeout(){}
  });
  assert.equal(messages[0].event,'hello');
  let seq=0;
  const commands=(event,extra={})=>({
    schemaVersion:1,event,documentId:messages.at(-1).documentId,
    requestId:(++seq).toString(16).padStart(32,'0'),...extra
  });
  const send=(msg)=>onPortMessage(msg);
  function last(event) {return messages.filter(x=>x.event===event).at(-1);}
  function bind() {
    const h=last('hello');
    send({schemaVersion:1,event:'bound',documentId:h.documentId});
    return h;
  }
  return {
    messages,address,callbacks,send,commands,bind,last,
    navigate(url,event) {
      address.href=url;
      if(event) callbacks.get(event)?.();
    },
    sync() {send({schemaVersion:1,event:'url_sync'});},
    inspect(token=last('hello').documentId) {
      let n=messages.length;
      const p=commands('inspect',{documentId:token});
      send(p);
      return messages.slice(n);
    },
    scroll() {
      let n=messages.length;
      send(commands('paginate',{pagination:'INFINITE_SCROLL',nextPageSelector:null}));
      return messages.slice(n);
    },
    flush() {for(const fn of timers.splice(0))fn();},
    get scrolls() {return scrolls;}
  };
}

test('initial v0 hello and SPA pushState url_sync produce fresh nonce and v1',()=>{
  const h=harness(),first=h.bind();
  assert.equal(first.navigationRevision,0);
  h.navigate('https://films.example/results?q=one&lang=pl');
  h.sync();
  const next=h.last('hello');
  assert.notEqual(next.documentId,first.documentId);
  assert.equal(next.navigationRevision,1);
  assert.equal(next.pageUrl,'https://films.example/results?q=one&lang=pl');
  assert.equal(h.inspect(first.documentId).length,0);
  h.bind();
  assert.equal(h.inspect().at(-1).event,'inspection');
});

test('the same-origin native sync can retry an unanswered SPA hello',()=>{
  const h=harness();h.bind();
  h.navigate('https://films.example/results#part');
  h.sync();
  assert.equal(h.messages.filter(m=>m.event==='hello').length,2,
    'one new hello per changed route, not two');
  const first=h.last('hello');
  h.sync();
  const retry=h.last('hello');
  assert.equal(retry.documentId,first.documentId);
  assert.equal(retry.navigationRevision,first.navigationRevision);
  assert.equal(h.messages.filter(m=>m.event==='hello').length,3);
  h.bind();
  assert.equal(h.inspect().at(-1).event,'inspection');
});

test('popstate and hashchange revoke old token and support navigating back',()=>{
  const h=harness(),initial=h.bind();
  h.navigate('https://films.example/results?q=A','popstate');
  const newer=h.last('hello');
  assert.equal(newer.navigationRevision,1);
  h.bind();
  h.navigate('https://films.example/home','popstate');
  const back=h.last('hello');
  assert.equal(back.navigationRevision,2);
  assert.notEqual(back.documentId,newer.documentId);
  h.bind();
  assert.equal(h.inspect(newer.documentId).length,0);
  assert.equal(h.inspect(initial.documentId).length,0);
  assert.equal(h.inspect().at(-1).event,'inspection');
  h.navigate('https://films.example/home#x','hashchange');
  assert.equal(h.last('hello').navigationRevision,3);
});

test('a cross-origin navigation permanently revokes this document port',()=>{
  const h=harness(),first=h.bind();
  h.navigate('https://other.example/watch','popstate');
  const n=h.messages.length;
  h.sync();
  assert.equal(h.messages.length,n);
  assert.equal(h.inspect(first.documentId).length,0);
  h.navigate('https://films.example/home','popstate');
  h.sync();
  assert.equal(h.messages.length,n);
});

test('out-of-order command from previous address triggers rebind but is rejected',()=>{
  const h=harness(),old=h.bind();
  h.navigate('https://films.example/results?q=next'); // no popstate event!
  const changed=h.inspect(old.documentId);
  assert.equal(changed.length,1);
  assert.equal(changed[0].event,'hello');
  assert.equal(changed[0].navigationRevision,1);
  h.bind();
  assert.equal(h.inspect().at(-1).event,'inspection');
});

test('delayed old infinite-scroll action cannot execute on new SPA route',()=>{
  const h=harness();h.bind();
  assert.equal(h.scroll().at(-1).status,'dispatched');
  h.navigate('https://films.example/results?q=other','popstate');
  h.bind();
  h.flush();
  assert.equal(h.scrolls,0);
  assert.equal(h.scroll().at(-1).status,'dispatched');
  h.flush();
  assert.equal(h.scrolls,1);
});

test('malformed native sync is ignored, no new trust or data exposure',()=>{
  const h=harness();h.bind();
  const before=h.messages.length;
  h.send({schemaVersion:1,event:'url_sync',pageUrl:'https://other.example/'});
  h.send({schemaVersion:1,event:'url_sync',profileId:'default'});
  assert.equal(h.messages.length,before);
});

test('a stale bound packet cannot restore a revoked route',()=>{
  const h=harness(),old=h.bind();
  h.navigate('https://films.example/results','popstate');
  h.send({schemaVersion:1,event:'bound',documentId:old.documentId});
  assert.equal(h.inspect(old.documentId).length,0);
  h.bind();
  assert.equal(h.inspect().at(-1).event,'inspection');
});
