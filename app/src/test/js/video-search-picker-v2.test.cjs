'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {TextEncoder} = require('node:util');

const extension = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');
const recognition = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/recognition.js'), 'utf8');

function fixture({unsafe = false, customRole = false, shadow = false} = {}) {
  const sent = [], events = new Map(), selectors = new Map();
  let receive, seq = 0, pageClicks = 0;
  function element(tag, attrs = {}, parent = null) {
    return {
      tagName: tag.toUpperCase(), attrs, id: attrs.id || '',
      type: attrs.type || '', name: attrs.name || '',
      className: attrs.class || '', classList: (attrs.class || '').split(' ').filter(Boolean),
      innerText: '', textContent: '', style: {}, parentElement: parent, form: null,
      children: [], disabled: false, readOnly: false,
      getAttribute(key) { return attrs[key] ?? null; },
      hasAttribute(key) { return attrs[key] != null; },
      setAttribute(key, value) { attrs[key] = value; },
      getBoundingClientRect() { return {left: 24, right: 120, top: 65, bottom: 105, width: 96, height: 40}; },
      appendChild(child) { this.children.push(child); },
      addEventListener(key, cb) {this['on_' + key] = cb;},
      remove() {this.removed = true;},
      contains(target) {
        let current = target;
        while (current) { if (current === this) return true; current = current.parentElement; }
        return false;
      },
      querySelector(selector) {
        if (selector === 'input[type="password"]') return unsafe ? {tagName:'INPUT'} : null;
        if (selector === 'a[href]' && this.tagName === 'ARTICLE') return {tagName:'A'};
        return null;
      },
      querySelectorAll() { return []; },
      matches(sel) {
        return sel.includes('button') && this.tagName === 'BUTTON' ||
          sel.includes('[role="button"]') && this.getAttribute('role') === 'button' ||
          sel.includes('input') && this.tagName === 'INPUT' && this.type === 'submit';
      },
      closest(sel) {
        let current = this;
        while (current) {
          if (sel === '[role="search"]' && current.getAttribute('role') === 'search')
            return current;
          if (sel === 'input' && current.tagName === 'INPUT') return current;
          if (sel === 'a[href],button,[role="button"]' &&
              (current.tagName === 'A' || current.tagName === 'BUTTON' ||
               current.getAttribute('role') === 'button')) return current;
          if (sel.includes('button,input[type="submit"],[role="button"]') &&
              (current.tagName === 'BUTTON' || current.getAttribute('role') === 'button'))
            return current;
          if (sel === 'form' && current.tagName === 'FORM') return current;
          current = current.parentElement;
        }
        return null;
      },
      click() { pageClicks++; }
    };
  }
  const root = element('html');
  const searchArea = element('div', {role:'search', id:'site-search'}, root);
  const input = element('input', {type:'search', name:'query'}, searchArea);
  const button = element(customRole ? 'div' : 'button',
    {'aria-label':'Search', 'data-testid':'site-search-submit',
      ...(customRole ? {role:'button'} : {})}, searchArea);
  const svg = element('svg', {}, button);
  searchArea.children.push(input, button);
  button.children.push(svg);
  const outside = element('button', {'aria-label':'Sign up'}, root);
  const cardA = element('article', {class:'video-card'}, searchArea);
  const cardB = element('article', {class:'video-card'}, searchArea);
  const nextLink = element('a', {id:'nextLink',href:'/page2',rel:'next'}, searchArea);
  nextLink.textContent='Next';
  selectors.set('article.video-card',[cardA,cardB]);
  selectors.set('#nextLink',[nextLink]);
  selectors.set('input[name="query"]',[input]);
  selectors.set('input[type="search"]',[input]);
  selectors.set('button[data-testid="site-search-submit"]',customRole?[]:[button]);
  selectors.set('div[data-testid="site-search-submit"]',customRole?[button]:[]);
  selectors.set('#site-search',[searchArea]);
  // Trusted composed events are retargeted at the Shadow host in the main document.
  const host = shadow ? element('film-search',{id:'searchHost'},root) : null;
  const shadowRoot = shadow ? {
    mode:'open',baseURI:'https://films.example/home',
    querySelectorAll:selector=>selectors.get(selector)||[]
  } : null;
  if (shadow) {
    host.shadowRoot=shadowRoot;
    host.isConnected=true;
    searchArea.parentElement=null; // document.contains(shadowChild) is false
    for(const node of [input,button,cardA,cardB,nextLink]) {
      node.getRootNode=()=>shadowRoot;
      node.isConnected=true;
    }
  }
  const doc = {
    documentElement:root,
    createElement:tag=>element(tag),
    querySelectorAll:selector=>shadow ?
      (selector==='#searchHost' || selector==='article,div,section,main,form,iframe,*'
        ? [host]:[]) : selectors.get(selector)||[],
    querySelector:selector=>shadow ? null : (selectors.get(selector)||[])[0]||null,
    contains:node=>root.contains(node),
    addEventListener(name,fn){events.set(name,fn);},
    removeEventListener(name,fn){if(events.get(name)===fn) events.delete(name);}
  };
  const win = {innerWidth:360,innerHeight:640,addEventListener(){},removeEventListener(){}};
  win.top=win;
  const port = {
    onMessage:{addListener(fn){receive=fn;}},
    onDisconnect:{addListener(){}},
    postMessage(packet){sent.push(packet);}
  };
  const context={
    window:win,document:doc,location:new URL('https://films.example/home'),
    browser:{runtime:{connectNative(){return port;}}},
    crypto:{getRandomValues(bytes){bytes.fill(11);return bytes;}},
    CSS:{escape(value){return value;}},TextEncoder,Uint8Array,URL,
    btoa:x=>Buffer.from(x,'binary').toString('base64'),
    atob:x=>Buffer.from(x,'base64').toString('binary'),
    setTimeout(){return 1;},clearTimeout(){}
  };
  if (shadow) vm.runInNewContext(recognition,context);
  vm.runInNewContext(extension,context);
  const id=sent[0].documentId;
  receive({schemaVersion:1,event:'bound',documentId:id});
  return {
    sent,input,button,svg,outside,searchArea,cardA,cardB,nextLink,host,
    recognition:win.__zavroRecognition,selectors,events,doc,
    swapShadowRoot() { if(shadow) host.shadowRoot={...shadowRoot}; },
    clicks:()=>pageClicks,
    pick(kind) {
      receive({schemaVersion:1,event:'pick',documentId:id,
        pickKind:kind,requestId:(++seq).toString(16).padStart(32,'0')});
    },
    tap(node) {
      assert.equal(typeof events.get('click'),'function');
      events.get('click')({isTrusted:true,
        target:shadow && node!==outside ? host : node,
        composedPath(){return shadow && node!==outside ? [node,searchArea,host]:[node];},
        preventDefault(){},stopImmediatePropagation(){}});
    },
    action(label) {
      const banner=root.children.filter(x=>x.children.length>=2).at(-1);
      const button=banner.children[1].children.find(x=>x.textContent===label);
      assert.ok(button,'picker action '+label);
      button.on_click({preventDefault(){},stopPropagation(){}});
    },
    status() {
      const banner=root.children.filter(x=>x.children.length>=2).at(-1);
      return banner.children[2].textContent;
    }
  };
}

test('SVG inside real search button requires a second deliberate confirmation',()=>{
  const f=fixture();
  f.pick('input'); f.tap(f.input);
  assert.equal(f.sent.filter(x=>x.event==='picked').length,0);
  f.action('Zatwierdź');
  assert.equal(f.sent.at(-1).selector,'input[name="query"]');
  f.pick('submit'); f.tap(f.svg);
  assert.match(f.status(),/Potwierdź/);
  assert.equal(f.sent.filter(x=>x.event==='picked').length,1);
  assert.equal(f.clicks(),0);
  f.action('Zatwierdź');
  assert.equal(f.sent.at(-1).selector,'button[data-testid="site-search-submit"]');
  assert.equal(f.clicks(),0);
});

test('custom search role button is accepted only inside associated search region',()=>{
  const f=fixture({customRole:true});
  f.pick('input'); f.tap(f.input); f.action('Zatwierdź');
  f.pick('submit'); f.tap(f.svg); f.action('Zatwierdź');
  assert.equal(f.sent.at(-1).selector,'div[data-testid="site-search-submit"]');
  assert.equal(f.clicks(),0);
});

test('login-like unrelated button cannot be selected and cancellation is not a pick',()=>{
  const f=fixture();
  f.pick('input'); f.tap(f.input); f.action('Zatwierdź');
  f.pick('submit'); f.tap(f.outside);
  assert.match(f.status(),/bezpiecznego przycisku/);
  assert.equal(f.sent.filter(x=>x.event==='picked').length,1);
  f.action('Anuluj');
  assert.equal(f.sent.at(-1).event,'pick_cancelled');
  assert.equal(f.clicks(),0);
});

test('unsafe password context does not produce any saved input selector',()=>{
  const f=fixture({unsafe:true});
  f.pick('input'); f.tap(f.input);
  assert.match(f.status(),/Nie rozpoznano aktywnego/);
  assert.equal(f.sent.filter(x=>x.event==='picked').length,0);
  f.action('Anuluj');
  assert.equal(f.clicks(),0);
});

test('P2-G1 manual picker binds actual Shadow input and submit through zcs1',()=>{
  const f=fixture({shadow:true});
  f.pick('input'); f.tap(f.input); f.action('Zatwierdź');
  const input=f.sent.at(-1).selector;
  assert.match(input,/^zcs1:/);
  assert.equal(f.recognition.resolve(input)[0],f.input);
  f.pick('submit'); f.tap(f.svg); f.action('Zatwierdź');
  const button=f.sent.at(-1).selector;
  assert.match(button,/^zcs1:/);
  assert.equal(f.recognition.resolve(button)[0],f.button);
  assert.equal(f.clicks(),0);
});

test('P2-G1 manual Shadow cards bind the touched member of a multi-card group',()=>{
  const f=fixture({shadow:true});
  f.pick('cards'); f.tap(f.cardB); f.action('Zatwierdź');
  const locator=f.sent.at(-1).selector;
  assert.match(locator,/^zcs1:/);
  const cards=f.recognition.resolve(locator);
  assert.equal(cards.length,2);
  assert.equal(cards[1],f.cardB);
  assert.equal(f.clicks(),0);
});

test('P2-G1 pending Shadow manual selection rejects a replaced ShadowRoot',()=>{
  const f=fixture({shadow:true});
  f.pick('cards'); f.tap(f.cardA);
  f.swapShadowRoot();
  f.action('Zatwierdź');
  assert.equal(f.sent.filter(x=>x.event==='picked').length,0);
  assert.match(f.status(),/Lokator przestał pasować/);
});

test('P2-G1 an unrelated CSS group cannot override the user-touched main card',()=>{
  const f=fixture();
  f.selectors.set('article.video-card',[f.cardA]);
  f.pick('cards'); f.tap(f.cardB);
  assert.match(f.status(),/Nie rozpoznano karty/);
  assert.equal(f.sent.filter(x=>x.event==='picked').length,0);
});

test('P2-G1 manual next-page link in open Shadow DOM stores scoped locator',()=>{
  const f=fixture({shadow:true});
  f.pick('next'); f.tap(f.nextLink); f.action('Zatwierdź');
  assert.equal(f.sent.at(-1).pagination,'NEXT_LINK');
  assert.match(f.sent.at(-1).selector,/^zcs1:/);
  assert.equal(f.recognition.resolve(f.sent.at(-1).selector)[0],f.nextLink);
});
