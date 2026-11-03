'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../main/assets/extensions/zavro-video-search');
const source=fs.readFileSync(path.join(root,'recognition.js'),'utf8');
const manifest=JSON.parse(fs.readFileSync(path.join(root,'manifest.json'),'utf8'));

function fixture({count=3, links='film', ads=false, surface='MAIN', resultPage=true}={}) {
  const base='https://films.example/'+(resultPage?'search?q=book':'');
  const parent={id:'',className:'search-results',classList:['search-results'],
    getAttribute(){return null;}};
  function makeCard(i) {
    const url=links==='film'?'/video/'+(i+1):links==='category'?'/category/'+(i+1):
      'javascript:alert(1)';
    const heading={textContent:'Video '+(i+1),getAttribute(){return null;}};
    const link={getAttribute(k){return k==='href'?url:null;},
      hasAttribute(){return false;}, querySelector(){return heading;},
      textContent:'Video '+(i+1)};
    return {tagName:'ARTICLE',className:ads?'sponsored':'video-card',
      classList:ads?['sponsored']:['video-card'], parentElement:parent,
      getAttribute(){return null;},closest(){return null;},
      querySelectorAll(s){return s==='a[href]'?[link]:[];},
      querySelector(s){
        if (/h1,h2,h3,h4/.test(s))return heading;
        if (/img,picture,video/.test(s))return {tagName:'IMG'};
        return null;
      }};
  }
  const cards=Array.from({length:count},(_,i)=>makeCard(i));
  function rootNodes(s) {
    if (s==='article,div,section,main,form,iframe,*')return cards;
    if (s==='article,li,figure,div,section')return cards;
    if (/article\.video-card/.test(s) || s==='article')return cards;
    return [];
  }
  const sheet={mode:'open',location:{origin:'https://films.example'},querySelectorAll:rootNodes};
  const outer={location:{origin:'https://films.example'},querySelectorAll(s){
    if(s==='article,div,section,main,form,iframe,*') {
      if(surface==='SHADOW')return [{tagName:'VIDEO-LIST',shadowRoot:sheet}];
      if(surface==='FRAME')return [{tagName:'IFRAME',contentDocument:sheet}];
    }
    return surface==='MAIN'?rootNodes(s):[];
  }};
  const win={};win.top=win;
  vm.runInNewContext(source,{window:win,document:outer,
    URL,location:new URL(base),console});
  return win.__zavroRecognition.scan(outer);
}
test('P1 built-in extension loads recognition before content bridge',()=>{
  assert.deepEqual(manifest.content_scripts[0].js,['recognition.js','content.js']);
  assert.equal(manifest.content_scripts[0].all_frames,false);
});
test('P1 identifies repeated card-local film links with structural score',()=>{
  const result=fixture();
  assert.equal(result.selector,'.search-results article.video-card');
  assert.equal(result.reason,'CANDIDATE_FOUND');
  assert.ok(result.confidence>=85);
  assert.ok(result.evidence.includes('REPEATED_STRUCTURE'));
});
test('P1 accepts one strongly structured search result',()=>{
  const result=fixture({count:1});
  assert.ok(result.confidence>=85);
  assert.equal(result.matchedCards,1);
  assert.equal(result.selector,'.search-results article.video-card');
});
test('P1 refuses category links even when poster and title exist',()=>{
  const result=fixture({links:'category'});
  assert.equal(result.selector,null);
  assert.equal(result.confidence,0);
});
test('P1 refuses sponsored cards and javascript links',()=>{
  assert.equal(fixture({ads:true}).selector,null);
  assert.equal(fixture({links:'javascript'}).selector,null);
});
test('P1 observes open shadow trees without pretending CSS can cross roots',()=>{
  const result=fixture({surface:'SHADOW'});
  assert.equal(result.surface,'SHADOW');
  assert.equal(result.reason,'SHADOW_RESULTS');
  assert.equal(result.selector,null);
});
test('P1 identifies same-origin iframe surface without returning an unsafe selector',()=>{
  const result=fixture({surface:'FRAME'});
  assert.equal(result.surface,'FRAME');
  assert.equal(result.reason,'FRAME_RESULTS');
  assert.equal(result.selector,null);
});
test('P1 limits scanned DOM candidates per analysis',()=>{
  const result=fixture({count:500});
  assert.ok(result.scannedElements<=260);
  assert.ok(result.contextsChecked<=12);
});
test('P1 structural read-only engine contains no form submission or network calls',()=>{
  for(const forbidden of [/\.click\s*\(/,/requestSubmit\s*\(/,/\.submit\s*\(/,/fetch\s*\(/,/XMLHttpRequest/,/sendBeacon/])
    assert.doesNotMatch(source,forbidden);
});

test('P2 TreeWalker candidates avoid a global document-wide card query',()=>{
  const nodes=Array.from({length:3},(_,i)=>{
    const parent={classList:['search-results'],id:'',className:'search-results',getAttribute(){return null;}};
    const heading={textContent:'Video '+i,getAttribute(){return null;}};
    const link={getAttribute(k){return k==='href'?'/video/'+i:null;},
      hasAttribute(){return false;},textContent:'Video '+i,querySelector(){return heading;}};
    return {tagName:'ARTICLE',classList:['video-card'],className:'video-card',
      parentElement:parent,getAttribute(){return null;},closest(){return null;},
      matches(s){return s==='article,li,figure,div,section';},
      querySelectorAll(s){return s==='a[href]'?[link]:[];},
      querySelector(s){if(/h1,h2,h3,h4/.test(s))return heading;
        if(/img,picture,video/.test(s))return {tagName:'IMG'};return null;}};
  });
  let broadQueries=0;
  const doc={
    createTreeWalker(){let i=0;return {nextNode(){return nodes[i++]||null;}};},
    querySelectorAll(s){
      if(s==='article,li,figure,div,section'){broadQueries++;throw Error('global DOM allocation');}
      if(s==='article.video-card'||s==='article')return nodes;
      return [];
    }
  };
  const win={};win.top=win;
  vm.runInNewContext(source,{window:win,document:doc,URL,
    location:new URL('https://films.example/search?q=book'),console});
  const result=win.__zavroRecognition.scan(doc);
  assert.equal(result.selector,'article.video-card');
  assert.equal(broadQueries,0);
  assert.ok(result.scannedElements<=260);
});

test('P2 single homepage recommendation remains unverified',()=>{
  const result=fixture({resultPage:false,count:1});
  assert.equal(result.selector,null);
});

function scopedFixture(surface = 'SHADOW') {
  const link = i => ({
    getAttribute(key) { return key === 'href' ? '/video/' + i : null; },
    hasAttribute() { return false; }, textContent: 'Video ' + i,
    querySelector() { return {textContent: 'Video ' + i, getAttribute() {return null;}}; }
  });
  const parent = {classList:['search-results'], className:'search-results',
    getAttribute() {return null;}};
  const cards = [1,2].map(i => ({
    tagName:'ARTICLE', classList:['video-card'], className:'video-card',
    parentElement:parent, getAttribute() {return null;}, closest() {return null;},
    querySelectorAll(q) {return q === 'a[href]' ? [link(i)] : [];},
    querySelector(q) {
      if (q.includes('h1,h2,h3,h4')) return {textContent:'Video '+i,
        getAttribute() {return null;}};
      if (q.includes('img,picture')) return {tagName:'IMG'};
      return null;
    }
  }));
  const inner = {mode:'open', location:{origin:'https://films.example'},
    baseURI:'https://films.example/search?q=book',
    querySelectorAll(q) {
      return q === 'article,li,figure,div,section' ||
        q === '.search-results article.video-card' ? cards :
        q === 'article,div,section,main,form,iframe,*' ? cards : [];
    }};
  const host = {tagName:surface === 'FRAME' ? 'IFRAME' : 'VIDEO-LIST',
    id:'playerResults', classList:[], contentDocument:surface === 'FRAME' ? inner : null,
    shadowRoot:surface === 'SHADOW' ? inner : null};
  const outer = {querySelectorAll(q) {
    if (q === 'article,div,section,main,form,iframe,*' || q === '#playerResults')
      return [host];
    return [];
  }};
  const win = {}; win.top = win;
  vm.runInNewContext(source, {
    window:win, document:outer, URL, location:new URL('https://films.example/search?q=book'),
    console,
    btoa:x => Buffer.from(x,'binary').toString('base64'),
    atob:x => Buffer.from(x,'base64').toString('binary')
  });
  return {win,outer,host,inner};
}
test('P2 locates and resolves cards within an open shadow root', () => {
  const f = scopedFixture();
  const result = f.win.__zavroRecognition.scan(f.outer);
  assert.equal(result.reason,'CANDIDATE_FOUND');
  assert.equal(result.surface,'SHADOW');
  assert.match(result.selector,/^zcs1:[A-Za-z0-9_-]+$/);
  assert.equal(f.win.__zavroRecognition.resolve(result.selector).length,2);
  f.host.shadowRoot = null;
  assert.equal(f.win.__zavroRecognition.resolve(result.selector),null);
});
test('P2 same-origin frame locator revokes access on origin change', () => {
  const f = scopedFixture('FRAME');
  const result = f.win.__zavroRecognition.scan(f.outer);
  assert.equal(result.surface,'FRAME');
  assert.ok(result.selector?.startsWith('zcs1:'));
  assert.equal(f.win.__zavroRecognition.resolve(result.selector).length,2);
  f.inner.location.origin = 'https://elsewhere.example';
  assert.equal(f.win.__zavroRecognition.resolve(result.selector),null);
  assert.equal(f.win.__zavroRecognition.resolve('zcs1:garbage'),null);
});
test('P2 rejects ambiguous host and inaccessible frame without guessing', () => {
  const f = scopedFixture('FRAME');
  const other = {tagName:'IFRAME', id:'playerResults'};
  const query = f.outer.querySelectorAll;
  f.outer.querySelectorAll = q => q === '#playerResults' ? [f.host,other] : query(q);
  const result = f.win.__zavroRecognition.scan(f.outer);
  assert.equal(result.selector,null);
  assert.equal(result.reason,'FRAME_RESULTS');
  Object.defineProperty(f.host,'contentDocument',{get(){throw new Error('SecurityError');}});
  assert.equal(f.win.__zavroRecognition.scan(f.outer).selector,null);
});
test('P2 icon-only SVG search name is read without clicking the page',()=>{
  const win={};win.top=win;
  vm.runInNewContext(source,{window:win,document:{},URL,
    location:new URL('https://films.example'),console});
  const hint=win.__zavroRecognition.controlHint({
    getAttribute(){return null;},
    querySelector(){return {getAttribute(key){return key==='href'?'#search-icon':null;}};}
  });
  assert.match(hint,/search-icon/);
  assert.doesNotMatch(source,/\\.click\\s*\\(/);
});
