'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const content = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');

// Sanitized DOM shapes model real-world GET search forms and repeated grids.
// Run the production WebExtension script, not a copied detection function.
function inspectFixture({cardsSelector, count=4, name='s', submitType='submit',
    method='get', next=false, metadata=true}={}) {
  const posted = [], selectors = new Map();
  let onMessage;
  const url = new URL('https://films.example/');
  const form = {
    tagName:'FORM', id:'searchform', className:'',
    getAttribute(key) { return ({
      method, action:'https://films.example/', role:'search'
    })[key] ?? null; },
    querySelector() { return null; },
    querySelectorAll(key) {
      return key === 'button,input[type="submit"],[role="button"]' ? [submit] : [];
    }
  };
  const input = {
    tagName:'INPUT', id:'s', name, type:'text', placeholder:'Search',
    form, disabled:false, readOnly:false,
    getAttribute(key) {return ({type:'text',id:'s',name,placeholder:'Search'})[key] ?? null;},
    closest() { return null; },
    getBoundingClientRect() { return {width:180,height:30}; }
  };
  const submit = {
    tagName:'INPUT', id:'searchsubmit', name:'', value:'', className:'button',
    classList:['button'], disabled:false, form,
    getAttribute(key) {return ({type:submitType})[key] ?? null;},
    matches(selector) {
      return selector === 'button,input[type="submit"],[role="button"]' ||
        selector === 'input[type="submit"],button[type="submit"]';
    },
    getBoundingClientRect() {return {width:30,height:30};}
  };
  const card = () => ({
    parentElement:null, classList:['item'], tagName:'DIV',
    querySelector(selector) {
      if (selector === 'a[href]') return {tagName:'A'};
      return metadata ? {tagName:'IMG'} : null;
    }
  });
  const list = Array.from({length:count},card);
  const nextLink = {
    tagName:'A', id:'next-page', classList:[], className:'', disabled:false,
    target:'', parentElement:null, innerText:'Next', textContent:'Next',
    getAttribute(key) {
      return ({rel:'next',href:'?page=2','aria-label':'Next page'})[key] ?? null;
    },
    hasAttribute() {return false;},
    closest(key) {
      return key === 'a[href],button,[role="button"]' ? this : null;
    },
    getBoundingClientRect(){return {width:30,height:20};},
  };
  selectors.set('input',[input]); selectors.set('#s',[input]);
  selectors.set('#searchsubmit',[submit]); selectors.set('a[rel="next"]',next ? [nextLink] : []);
  selectors.set('#next-page',[nextLink]);
  if (cardsSelector) selectors.set(cardsSelector,list);
  const document = {
    documentElement:{},
    querySelector(){return null;},
    querySelectorAll(key){return selectors.get(key) || [];},
    contains(){return true;}
  };
  const window = {addEventListener(){}};
  window.top=window;
  const port = {
    onMessage:{addListener(fn){onMessage=fn;}},
    onDisconnect:{addListener(){}},
    postMessage(p){posted.push(p);}
  };
  vm.runInNewContext(content,{
    window, document, location:url, URL, Uint8Array,
    browser:{runtime:{connectNative(){return port;}}},
    crypto:{getRandomValues(b){b.fill(8);return b;}},
    CSS:{escape(v){return v;}}, setTimeout(){}, clearTimeout(){}
  });
  const id = posted[0].documentId;
  onMessage({schemaVersion:1,event:'bound',documentId:id});
  onMessage({schemaVersion:1,event:'inspect',documentId:id,
    requestId:'1'.padStart(32,'0')});
  return posted.find(p=>p.event==='inspection');
}

test('recognizes repeated article cards under a video list and GET search form',()=>{
  const result = inspectFixture({cardsSelector:'.videos-list article'});
  assert.equal(result.status,'ready');
  assert.equal(result.resultsCardSelector,'.videos-list article');
  assert.equal(result.inputSelector,'#s');
  assert.equal(result.submitSelector,'#searchsubmit');
  assert.equal(result.submission,'CLICK_SUBMIT');
});

test('recognizes repeatable list items and validated next-page locator',()=>{
  const result = inspectFixture({cardsSelector:'.list-videos .item',next:true});
  assert.equal(result.status,'ready');
  assert.equal(result.resultsCardSelector,'.list-videos .item');
  assert.equal(result.pagination,'NEXT_LINK');
  assert.equal(result.nextPageSelector,'#next-page');
});

test('recognizes semantic grid cards without relying on a global class',()=>{
  const result = inspectFixture({cardsSelector:'.video-grid article'});
  assert.equal(result.status,'ready');
  assert.equal(result.resultsCardSelector,'.video-grid article');
});

test('does not treat links without card-local metadata as film cards',()=>{
  const result = inspectFixture({cardsSelector:'.list-videos .item',metadata:false});
  assert.equal(result.status,'partial');
  assert.equal(result.resultsCardSelector,null);
});

test('rejects unsafe POST search forms before selecting any control',()=>{
  const result = inspectFixture({cardsSelector:'.list-videos .item',method:'post'});
  assert.equal(result.status,'missing');
});
