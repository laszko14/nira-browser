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
  let windowScrolls = 0, innerScrolls = 0, stuck = false,
    terminal = false, clock = 1000;
  const location = new URL('https://films.example/results?q=abc');
  const document = {
    querySelector(selector) {
      if (selector === 'input[type="password"]') return blocked ? {} : null;
      if (selector.includes('[data-pagination-end="true"]')) return terminal ?
        {closest(){return null;}} : null;
      return null;
    },
    querySelectorAll(selector) { return selector === '.cards' ? cards : []; }
  };
  const win = {
    innerHeight: 640, scrollY:0, addEventListener() {},
    scrollBy() {windowScrolls++; if(!stuck)this.scrollY+=360;},
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
    setTimeout(fn) {timers.push(fn);},
    Date:{now(){return clock;}}
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
      clientHeight:400, scrollHeight:1300, scrollTop:0,parentElement:null,
      scrollBy(){innerScrolls++;if(!stuck)this.scrollTop+=160;}
    };
  }
  function card(parent, ownerDocument=document, href=null) {
    return {parentElement:parent,ownerDocument,isConnected:true,href,
      querySelector(q){
        if(q!=='a[href]'||!this.href)return null;
        const value=this.href;
        return {getAttribute(k){return k==='href'?value:null;}};
      }};
  }
  return {paginate,container,card,setCards(v){cards=v;},
    flush(){for(const t of timers.splice(0))t();},
    stats(){return {windowScrolls,innerScrolls};},
    setBlocked(v){blocked=v;},setStuck(v){stuck=v;},
    setTerminal(v){terminal=v;},advanceTime(ms){clock+=ms;},
    change(){changed=true;location.pathname='/other';}};
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


test('P4-C2 overlapping scroll ACKs never queue two actions against one window',()=>{
  const h=fixture(), owner=h.container();
  h.setCards([h.card(owner)]);
  assert.equal(h.paginate(),'dispatched');
  assert.equal(h.paginate(),'pending');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:1});
});

test('P4-C2 virtualized tail replacement revokes deferred action despite stable first card',()=>{
  const h=fixture(), owner=h.container();
  const first=h.card(owner),last=h.card(owner);
  h.setCards([first,last]);
  assert.equal(h.paginate(),'dispatched');
  h.setCards([first,h.card(owner)]); // first survived, trailing item recycled
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});

test('P4-C2 mutated film identity in an existing virtual row revokes pending action',()=>{
  const h=fixture(), owner=h.container();
  const first=h.card(owner,undefined,'/movie/1'),
    last=h.card(owner,undefined,'/movie/2');
  h.setCards([first,last]);
  assert.equal(h.paginate(),'dispatched');
  last.href='/movie/99'; // exact same DOM object, new content
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});

test('P4-C2 mismatched trailing result scroll container cannot scroll unrelated sidebar',()=>{
  const h=fixture(), parent=h.container();
  h.setCards([h.card(parent),h.card(parent),h.card(h.container())]);
  assert.equal(h.paginate(),'pending');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:0});
});

test('P4-C2 two no-progress scrolls cause pending, never a synthetic end',()=>{
  const h=fixture(), owner=h.container();
  h.setCards([h.card(owner),h.card(owner)]);
  h.setStuck(true);
  assert.equal(h.paginate(),'dispatched');
  h.flush();
  assert.equal(h.paginate(),'dispatched');
  h.flush();
  assert.equal(h.paginate(),'pending');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:2});
  // The list can hydrate later. Pending must recover without reset/click.
  h.setCards([h.card(owner),h.card(owner),h.card(owner)]);
  assert.equal(h.paginate(),'dispatched');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:3});
});

test('P4-C2 actual scrollTop movement counts as progress in a stable card window',()=>{
  const h=fixture(), owner=h.container();
  h.setCards([h.card(owner),h.card(owner)]);
  for(let i=0;i<4;i++){
    assert.equal(h.paginate(),'dispatched');
    h.flush();
  }
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:4});
});

test('P4-C2 recycler updates film identity without replacing nodes and can resume after stall',()=>{
  const h=fixture(), owner=h.container();
  const cards=[h.card(owner,undefined,'/watch/1'),h.card(owner,undefined,'/watch/2')];
  h.setCards(cards);h.setStuck(true);
  assert.equal(h.paginate(),'dispatched');h.flush();
  assert.equal(h.paginate(),'dispatched');h.flush();
  assert.equal(h.paginate(),'pending');
  cards[1].href='/watch/3';
  assert.equal(h.paginate(),'dispatched');
  h.flush();
  assert.deepEqual(h.stats(),{windowScrolls:0,innerScrolls:3});
});

test('P4-C2 only an explicit stable terminal marker confirms end',()=>{
  const h=fixture(), owner=h.container();
  h.setCards([h.card(owner),h.card(owner)]);
  h.setStuck(true);
  assert.equal(h.paginate(),'dispatched');h.flush();
  assert.equal(h.paginate(),'dispatched');h.flush();
  assert.equal(h.paginate(),'pending'); // stagnation, not end
  h.setTerminal(true);
  assert.equal(h.paginate(),'pending');
  h.advanceTime(750);
  assert.equal(h.paginate(),'end');
});
