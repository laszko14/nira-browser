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
    method='get', next=false, metadata=true, analyze=false,
    anonymousPager=false, secondInput=false, inputId='s', formRole='search',
    excludeResultRegion=false,
    ariaLabel=null, placeholder='Search', recognitionReport=null,
    onScan=null, treeNodes=null, onWideQuery=null}={}) {
  const posted = [], selectors = new Map();
  let onMessage;
  const url = new URL('https://films.example/');
  const form = {
    tagName:'FORM', id:'searchform', className:'',
    getAttribute(key) { return ({
      method, action:'https://films.example/', role:formRole
    })[key] ?? null; },
    querySelector() { return null; },
    querySelectorAll(key) {
      return key === 'button,input[type="submit"],[role="button"]' ? [submit] : [];
    }
  };
  const input = {
    tagName:'INPUT', id:inputId, name, type:'text', placeholder,
    form, disabled:false, readOnly:false,
    getAttribute(key) {return ({type:'text',id:inputId,name,placeholder,
      'aria-label':ariaLabel})[key] ?? null;},
    closest() { return null; },
    getBoundingClientRect() { return {width:180,height:30}; }
  };
  const submit = {
    tagName:'INPUT', id:'searchsubmit', name:'', value:'', className:'button',
    classList:['button'], disabled:false, form,
    getAttribute(key) {return ({type:submitType})[key] ?? null;},
    closest() {return null;},
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
  const input2 = secondInput ? {
    ...input, id:'secondary', getAttribute(key) {
      return ({type:'text',id:'secondary',name,placeholder,
        'aria-label':ariaLabel})[key] ?? null;
    }
  } : null;
  selectors.set('input',input2 ? [input,input2] : [input]);
  selectors.set('#'+inputId,[input]);
  selectors.set('input[name="'+name+'"]',input2 ? [input,input2] : [input]);
  if (input2) selectors.set('#secondary',[input2]);
  selectors.set('#searchsubmit',[submit]); selectors.set('a[rel="next"]',next ? [nextLink] : []);
  selectors.set('#next-page',[nextLink]);
  if (cardsSelector) selectors.set(cardsSelector,list);
  if (anonymousPager) {
    const unlabeled = {
      tagName:'BUTTON', id:'pager-action', className:'', classList:[],
      disabled:false, parentElement:null, form:null, innerText:'',
      textContent:'', getAttribute(){return null;},
      getBoundingClientRect(){return {width:35,height:24};},
      closest(sel) {
        return sel === 'a[href],button,[role="button"]' ? this : null;
      }, matches(sel){ return sel === 'button'; },
      hasAttribute(){return false;}
    };
    selectors.set('.pagination a,.pagination button,.pager a,.pager button,' +
      'nav[aria-label="pagination"] a,nav[aria-label="pagination"] button', [unlabeled]);
    selectors.set('#pager-action',[unlabeled]);
  }
  const document = {
    documentElement:{},
    querySelector(query){
      return method.toLowerCase() === 'post' &&
        query.includes('form[method="post"]') ? input : null;
    },
    querySelectorAll(key){
      if (key==='article,div,li,figure' && onWideQuery) onWideQuery();
      return selectors.get(key) || [];
    },
    createTreeWalker:treeNodes ? function() {
      let i=0;
      return {nextNode(){return treeNodes[i++]||null;}};
    } : undefined,
    contains(){return true;}
  };
  const window = {addEventListener(){},
    __zavroRecognition: recognitionReport ? {
      scan(){onScan?.();return recognitionReport;},controlHint(){return '';},
      resultRegion(){return {excluded:excludeResultRegion};}
    } : undefined};
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
  onMessage({schemaVersion:1,event:analyze?'analyze':'inspect',documentId:id,
    requestId:'1'.padStart(32,'0')});
  return posted.find(p=>p.event===(analyze?'analysis':'inspection'));
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

test('P0 never chooses a reset button called Search as a submit action',()=>{
  const result=inspectFixture({submitType:'reset'});
  assert.equal(result.inputSelector,'#s');
  assert.equal(result.submitSelector,null);
  assert.equal(result.submission,'FORM_SUBMIT');
});

test('P0 rejects a unique unlabeled pager control without forward evidence',()=>{
  const result=inspectFixture({anonymousPager:true});
  assert.equal(result.pagination,'NONE');
  assert.equal(result.nextPageSelector,null);
});

test('P0 analysis has separate stage and bounded, non-DOM evidence',()=>{
  const result=inspectFixture({cardsSelector:'.videos-list article',
    next:true,analyze:true});
  assert.equal(result.event,'analysis');
  assert.equal(result.status,'ready');
  assert.equal(result.stage,'PAGINATION');
  assert.equal(result.reason,'complete');
  assert.ok(result.confidence>=70);
  assert.ok(result.evidence.includes('REPEATED_CARDS'));
  assert.ok(result.evidence.includes('PAGER_SEMANTIC'));
  assert.equal(JSON.stringify(result).includes('https://films.example'),false);
});

test('P0 analyzes a search-only homepage without requiring results yet',()=>{
  const result=inspectFixture({analyze:true});
  assert.equal(result.status,'partial');
  assert.equal(result.stage,'SEARCH_FORM');
  assert.equal(result.reason,'search_only');
  assert.equal(result.resultsCardSelector,null);
});

test('P0 detects a semantic aria-label on a nonstandard search input',()=>{
  const result=inspectFixture({name:'term',inputId:'entry',placeholder:'Type',
    formRole:'',ariaLabel:'Search videos',analyze:true});
  assert.equal(result.status,'partial');
  assert.equal(result.inputSelector,'#entry');
  assert.ok(result.evidence.includes('ACCESSIBLE_NAME'));
});

test('P0 refuses tied search fields rather than sending an ambiguous command',()=>{
  const result=inspectFixture({secondInput:true,analyze:true});
  assert.equal(result.status,'ambiguous');
  assert.equal(result.reason,'ambiguous_input');
  assert.equal(result.confidence,0);
  assert.equal(result.inputSelector,null);
});

test('P0 classifies POST-only search as unsafe and does not choose a control',()=>{
  const result=inspectFixture({method:'post',analyze:true});
  assert.equal(result.status,'unsafe');
  assert.equal(result.reason,'unsafe_form');
  assert.equal(result.inputSelector,null);
});

test('P2-E replaced iframe evidence cannot fall back to unrelated legacy homepage cards',()=>{
  const found=inspectFixture({cardsSelector:'.movie-card',
    recognitionReport:{selector:null,confidence:0,reason:'FRAME_REPLACED'}});
  assert.equal(found.status,'partial');
  assert.equal(found.resultsCardSelector,null);
});
test('P2-E low-confidence cards cannot silently become a legacy result selector',()=>{
  const found=inspectFixture({cardsSelector:'.movie-card',
    recognitionReport:{selector:null,confidence:78,reason:'LOW_CONFIDENCE'}});
  assert.equal(found.resultsCardSelector,null);
});
test('P2-E dynamic input ID is rejected in favor of unique stable form name',()=>{
  const found=inspectFixture({inputId:'search_1234567890',name:'s',
    cardsSelector:'.videos-list article'});
  assert.equal(found.inputSelector,'input[name="s"]');
  assert.equal(found.status,'ready');
});

test('P2-F replaced Shadow/iframe host cannot fall back to unrelated legacy cards',()=>{
  const report=inspectFixture({cardsSelector:'.movie-card',
    recognitionReport:{selector:null,confidence:0,reason:'CONTEXT_REPLACED'}});
  assert.equal(report.resultsCardSelector,null);
  assert.equal(report.status,'partial');
});

test('P2-G2 analyze performs a single failed structural discovery',()=>{
  let scans=0;
  const result=inspectFixture({analyze:true,recognitionReport:{
    selector:null,confidence:0,reason:'CARDS_NOT_FOUND'
  },onScan(){scans++;}});
  assert.equal(result.status,'partial');
  assert.equal(scans,1);
});

test('P2-G2 result-only unsafe-form page still detects cards once',()=>{
  let scans=0;
  const result=inspectFixture({analyze:true,method:'post',
    cardsSelector:'.videos-list article',recognitionReport:{
      selector:null,confidence:0,reason:'CARDS_NOT_FOUND'
    },onScan(){scans++;}});
  assert.equal(result.resultsCardSelector,'.videos-list article');
  assert.equal(scans,1);
});

test('P2-G2 large legacy DOM fallback avoids global matching-NodeList allocation',()=>{
  let scans=0, wide=0, visits=0;
  const nodes=Array.from({length:4000},()=>({
    matches(){visits++;return false;}
  }));
  const result=inspectFixture({cardsSelector:'.videos-list article',analyze:true,
    recognitionReport:{selector:null,confidence:0,reason:'CARDS_NOT_FOUND'},
    onScan(){scans++;},treeNodes:nodes,onWideQuery(){wide++;}});
  assert.equal(result.resultsCardSelector,'.videos-list article');
  assert.equal(scans,1);
  assert.equal(wide,0);
  assert.equal(visits,2400);
});

test('P3-B legacy fallback refuses recommendation cards even with familiar CSS',()=>{
  const report=inspectFixture({
    cardsSelector:'.videos-list article',analyze:true,
    recognitionReport:{selector:null,confidence:0,reason:'CARDS_NOT_FOUND'},
    excludeResultRegion:true,
  });
  assert.equal(report.status,'partial');
  assert.equal(report.resultsCardSelector,null);
});
test('P3-B legacy compatibility remains for a valid unobstructed results list',()=>{
  const report=inspectFixture({
    cardsSelector:'.videos-list article',
    recognitionReport:{selector:null,confidence:0,reason:'CARDS_NOT_FOUND'},
  });
  assert.equal(report.status,'ready');
  assert.equal(report.resultsCardSelector,'.videos-list article');
});
