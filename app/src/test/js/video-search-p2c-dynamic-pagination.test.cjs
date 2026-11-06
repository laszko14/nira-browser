'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { TextEncoder } = require('node:util');

const content = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');
function fixture() {
  const sent = [], timers = [];
  let handle, cards = [], blocked = false, changed = false;
  let windowScrolls = 0, innerScrolls = 0;
  const location = new URL('https://films.example/results?q=abc');
  const document = {
    querySelector(selector) {
      if (selector === 'input[type="password"]') return blocked ? {} : null;
      return null;
    },
    querySelectorAll(selector) { return selector === '.cards' ? cards : []; }
  };
  const win = {
    innerHeight: 640, addEventListener() {},
    scrollBy() {windowScrolls++;},
    getComputedStyle() {return {overflowY:'auto'};}
  };
  win.top = win;
  const port = {
    onMessage: {addListener(fn) {handle=fn;}},
    onDisconnect: {addListener() {}},
    postMessage(msg) {sent.push(msg);}
  };
  vm.runInNewContext(content, {
    window:win, document, location, URL, Uint8Array, TextEncoder,
    browser:{runtime:{connectNative(){return port;}}},
    crypto:{getRandomValues(bytes) {bytes.fill(7);return bytes;}},
    setTimeout(fn) {timers.push(fn);}
  });
  const doc = sent[0].documentId;
  handle({schemaVersion:1,event:'bound',documentId:doc});
  let n = 0;
  function paginate(selector='.cards') {
    const before = sent.length;
    handle({schemaVersion:1,event:'paginate',documentId:doc,
      requestId:(++n).toString(16).padStart(32,'0'),
      pagination:'INFINITE_SCROLL',nextPageSelector:null,
      resultsCardSelector:selector});
    assert.equal(sent.length, before+1);
    return sent.at(-1).status;
  }
  function container() {
    return {
      clientHeight:400, scrollHeight:1300, parentElement:null,
      scrollBy(){innerScrolls++;}
    };
  }
  function card(parent, ownerDocument=document) {
    return {parentElement:parent,ownerDocument,isConnected:true};
  }
  return {paginate,container,card,setCards(v){cards=v;},
    flush(){for(const t of timers.splice(0))t();},
    stats(){return {windowScrolls,innerScrolls};},
    setBlocked(v){blocked=v;}, change(){changed=true;location.pathname='/other';}};
}

test('infinite scroll targets the results scroll container, not window',()=>{
  const h=fixture(), parent=h.container();
  h.setCards([h.card(parent),h.card(parent)]);
  assert.equal(h.paginate(),'dispatched');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:1});
});

test('SPA hydration without result cards does not scroll anything',()=>{
  const h=fixture();
  assert.equal(h.paginate(),'pending');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});

test('virtual-list replacement revokes a queued scroll action',()=>{
  const h=fixture(), parent=h.container();
  h.setCards([h.card(parent)]);
  assert.equal(h.paginate(),'dispatched');
  h.setCards([h.card(parent)]);
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});

test('two independent scrolling result containers are ambiguous and fail closed',()=>{
  const h=fixture();
  h.setCards([h.card(h.container()),h.card(h.container())]);
  assert.equal(h.paginate(),'pending');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});

test('document scroll is fallback when no dedicated owner is present',()=>{
  const h=fixture();
  h.setCards([h.card(null)]);
  assert.equal(h.paginate(),'dispatched');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:1,innerScrolls:0});
});

test('navigation and protected login pages do not trigger delayed scrolling',()=>{
  const h=fixture();
  h.setCards([h.card(null)]);
  assert.equal(h.paginate(),'dispatched');
  h.change(); h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
  const k=fixture();
  k.setCards([k.card(null)]);k.setBlocked(true);
  assert.equal(k.paginate(),'unsafe');
});

test('P3-C a login gate appearing after scroll ACK cancels deferred scrolling',()=>{
  const h=fixture(), parent=h.container();
  h.setCards([h.card(parent),h.card(parent)]);
  assert.equal(h.paginate(),'dispatched');
  h.setBlocked(true);
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});
