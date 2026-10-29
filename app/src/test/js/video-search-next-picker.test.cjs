'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {TextEncoder} = require('node:util');

const script = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');

function fixture() {
  const sent = [], listeners = new Map(), timers = [];
  const selectors = new Map();
  let receiver;
  const document = {
    documentElement: {
      appendChild(child) { if (child.children?.length >= 2) this.picker = child; },
      clientWidth: 360, clientHeight: 720
    },
    createElement(tag) {
      return {
        tagName: tag.toUpperCase(), children: [], attrs: {}, style: {},
        setAttribute(key, value) { this.attrs[key] = value; },
        addEventListener(key, fn) { this['on_' + key] = fn; },
        appendChild(child) { this.children.push(child); },
        remove() { this.removed = true; }
      };
    },
    querySelectorAll(selector) {return selectors.get(selector) || [];},
    querySelector() {return null;},
    contains() {return true;},
    addEventListener(name, fn) {listeners.set(name, fn);},
    removeEventListener(name, fn) {
      if (listeners.get(name) === fn) listeners.delete(name);
    }
  };
  const win = {innerWidth:360, innerHeight:720,
    addEventListener() {}, removeEventListener() {}};
  win.top = win;
  const port = {
    onMessage: {addListener(fn) {receiver=fn;}},
    onDisconnect: {addListener() {}},
    postMessage(packet) {sent.push(packet);}
  };
  vm.runInNewContext(script, {
    window:win, document, location:new URL('https://films.example/results?q=drama'),
    browser:{runtime:{connectNative() {return port;}}},
    crypto:{getRandomValues(bytes) {bytes.fill(7);return bytes;}},
    CSS:{escape(value) {return value;}}, URL, Uint8Array, TextEncoder,
    setTimeout(fn) {timers.push(fn);return timers.length;}, clearTimeout() {}
  });
  const documentId = sent[0].documentId;
  receiver({schemaVersion:1,event:'bound',documentId});
  let n = 0;
  function command(event, extra) {
    const before = sent.length;
    receiver({schemaVersion:1,event,documentId,
      requestId:(++n).toString(16).padStart(32,'0'), ...extra});
    return sent.slice(before);
  }
  function selectPage(node) {
    command('pick',{pickKind:'next'});
    const click = listeners.get('click');
    assert.equal(typeof click, 'function');
    click({isTrusted:true,target:node,preventDefault(){},
      stopImmediatePropagation(){}});
    // Selection is read-only. A separate deliberate confirmation emits "picked".
    assert.equal(sent.some(p => p.event === 'picked'), false);
    const approve = document.documentElement.picker?.children[1]?.children
      .find(x => x.textContent === 'Zatwierdź');
    if (approve) approve.on_click({preventDefault(){},stopPropagation(){}});
    const last = sent.at(-1);
    return last.event==='picked' ? last : null;
  }
  function node(tag, settings={}) {
    const attributes = settings.attributes || {};
    const el = {
      tagName:tag.toUpperCase(), id:settings.id || '',
      className:settings.classes?.join(' ') || '',
      classList:settings.classes || [],
      disabled:false, type: settings.type ?? 'button',
      target:settings.target || '',
      parentElement:settings.parent || null,
      innerText:settings.text || '',
      getAttribute(name) {return attributes[name] ?? null;},
      hasAttribute(name) {return Object.hasOwn(attributes,name);},
      getBoundingClientRect() {return {width:32,height:32};},
      matches(expr) {
        if (expr==='a[href]') return this.tagName==='A' && this.hasAttribute('href');
        if (expr==='button') return this.tagName==='BUTTON';
        return false;
      },
      closest(selector) {
        if (selector.includes('a[href],button,[role="button"]')) {
          let current=this;
          while(current) {
            if(current.tagName==='BUTTON' ||
                current.tagName==='A' && current.hasAttribute('href') ||
                current.getAttribute('role')==='button') return current;
            current=current.parentElement;
          }
          return null;
        }
        return null;
      },
      click() {this.clicks=(this.clicks||0)+1;}
    };
    if(tag.toUpperCase()==='A') el.href=new URL(attributes.href||'', 'https://films.example/results?q=drama').href;
    return el;
  }
  return {selectPage,command,sent,timers,node,map:(sel,...value)=>selectors.set(sel,value)};
}

test('NEXT_LINK accepts a nested icon with non-unique page-link class and unique aria-label',()=>{
  const h=fixture();
  const prev=h.node('a',{classes:['page-link'],attributes:{href:'?page=1', 'aria-label':'Previous page'}});
  const next=h.node('a',{classes:['page-link'],attributes:{href:'?page=2','aria-label':'Następna strona'}});
  const icon=h.node('span',{parent:next});
  h.map('a.page-link',prev,next);
  h.map('a[aria-label="Następna strona"]',next);
  const picked=h.selectPage(icon);
  assert.equal(picked?.event,'picked');
  assert.equal(picked.selector,'a[aria-label="Następna strona"]');
  assert.equal(picked.pagination,'NEXT_LINK');
  const outcome=h.command('paginate',{pagination:picked.pagination,
    nextPageSelector:picked.selector});
  assert.equal(outcome.at(-1)?.status,'dispatched');
  h.timers.at(-1)();
  assert.equal(next.clicks,1);
});

test('next link inside a uniquely identified parent is selectable despite identical link classes',()=>{
  const h=fixture();
  const nav=h.node('nav',{classes:['pages']});
  const prevLi=h.node('li',{classes:['page-item'],parent:nav});
  const nextLi=h.node('li',{classes:['page-item','next'],parent:nav});
  const prev=h.node('a',{classes:['page-link'],parent:prevLi,attributes:{href:'?page=1'}});
  const next=h.node('a',{classes:['page-link'],parent:nextLi,attributes:{href:'?page=2'}});
  h.map('a.page-link',prev,next);
  h.map('li.page-item',prevLi,nextLi);
  h.map('li.next',nextLi);
  h.map('li.next a',next);
  const picked=h.selectPage(next);
  assert.equal(picked?.selector,'li.next a');
  assert.equal(picked.pagination,'NEXT_LINK');
});

test('shared button class can use stable data-testid for LOAD_MORE',()=>{
  const h=fixture();
  const older=h.node('button',{classes:['pager'],attributes:{'data-testid':'older'}});
  const next=h.node('button',{classes:['pager'],attributes:{'data-testid':'load-next'}});
  h.map('button.pager',older,next);
  h.map('button[data-testid="load-next"]',next);
  const picked=h.selectPage(next);
  assert.equal(picked?.selector,'button[data-testid="load-next"]');
  assert.equal(picked.pagination,'LOAD_MORE');
  const outcome=h.command('paginate',{pagination:'LOAD_MORE',nextPageSelector:picked.selector});
  assert.equal(outcome.at(-1)?.status,'dispatched');
  h.timers.at(-1)();
  assert.equal(next.clicks,1);
});

test('default submit-type button outside a form is a valid pagination control',()=>{
  const h=fixture();
  const next=h.node('button',{type:'submit',classes:['next-page']});
  h.map('button.next-page',next);
  const picked=h.selectPage(next);
  assert.equal(picked?.pagination,'LOAD_MORE');
  const outcome=h.command('paginate',{pagination:picked.pagination,nextPageSelector:picked.selector});
  assert.equal(outcome.at(-1)?.status,'dispatched');
  h.timers.at(-1)();
  assert.equal(next.clicks,1);
});

test('form-associated submit buttons cannot be mistaken for pagination',()=>{
  const h=fixture();
  const next=h.node('button',{type:'submit',classes:['next-page']});
  next.form={};
  h.map('button.next-page',next);
  assert.equal(h.selectPage(next),null);
});

test('same-document hash SPA next links are accepted only with next-page semantics',()=>{
  const h=fixture();
  const next=h.node('a',{classes:['pagination-next'],attributes:{href:'#','aria-label':'Next page'}});
  h.map('a.pagination-next',next);
  const picked=h.selectPage(next);
  assert.equal(picked?.pagination,'LOAD_MORE');
  const outcome=h.command('paginate',{pagination:'LOAD_MORE',nextPageSelector:picked.selector});
  assert.equal(outcome.at(-1)?.status,'dispatched');
  h.timers.at(-1)();
  assert.equal(next.clicks,1);
});

test('non-native role=button next control is accepted only when explicitly named',()=>{
  const h=fixture();
  const next=h.node('div',{classes:['step'],attributes:{role:'button','aria-label':'Next page'}});
  h.map('div.step',next);
  const picked=h.selectPage(next);
  assert.equal(picked?.pagination,'LOAD_MORE');
  const outcome=h.command('paginate',{pagination:'LOAD_MORE',nextPageSelector:picked.selector});
  assert.equal(outcome.at(-1)?.status,'dispatched');
});

test('never accept cross-origin, script links or unlabeled same-page anchors',()=>{
  for (const href of ['https://other.example/results','javascript:void(0)','#']) {
    const h=fixture();
    const next=h.node('a',{classes:['unique-pager'],attributes:{href}});
    h.map('a.unique-pager',next);
    assert.equal(h.selectPage(next),null,href);
    assert.equal(h.sent.filter(p=>p.event==='picked').length,0);
  }
});

test('do not invent selectors for ambiguous matching navigation controls',()=>{
  const h=fixture();
  const prev=h.node('a',{classes:['page-link'],attributes:{href:'?page=2'}});
  const next=h.node('a',{classes:['page-link'],attributes:{href:'?page=3'}});
  h.map('a.page-link',prev,next);
  assert.equal(h.selectPage(next),null);
  assert.equal(h.sent.filter(p=>p.event==='picked').length,0);
});
