'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'), path=require('node:path'), vm=require('node:vm');
const content=fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'),'utf8');

// Production bridge and native messages, backed by a bounded deterministic DOM.
// Non-clickable current-page markers mimic common numbered pagers.
function fixture({showMore=false}={}) {
  const uri='https://films.example/results?q=movie';
  const location=new URL(uri);
  let receive, seq=0, current=1, duplicateActive=false;
  const messages=[], timers=[], clicks=[];
  const main={tagName:'MAIN',id:'main',className:'page',parentElement:null,
    getAttribute(){return null;}};
  const results={tagName:'SECTION',id:'results',className:'search-results',
    parentElement:main,getAttribute(){return null;}};
  const pager={tagName:'NAV',id:'pager',className:'pagination',
    parentElement:main,getAttribute(k){return k==='aria-label'?'Pagination':null;},
    matches(){return false;}};
  const sidebar={tagName:'ASIDE',id:'side',className:'sidebar-carousel',
    parentElement:main,getAttribute(){return null;}};
  const active={
    tagName:'SPAN',className:'current',classList:['current'],
    parentElement:pager,get textContent(){return String(current);},
    getAttribute(k){return k==='aria-current'?'page':null;},
    getBoundingClientRect(){return {width:20,height:20};},
    closest(){return null;}
  };
  const extraActive={
    ...active, get textContent(){return String(current+1);}
  };
  function anchor(n) {
    const obj={
      tagName:'A',id:'',className:'page-number',classList:['page-number'],
      parentElement:pager,ownerDocument:null,disabled:false,target:'',
      innerText:String(n),textContent:String(n),
      getAttribute(k){
        if(k==='href')return '/results?q=movie&page='+n;
        if(k==='aria-label')return 'Page '+n;
        return null;
      },
      hasAttribute(){return false;},
      matches(q){return q==='a[href]';},
      closest(q){return q==='a[href],button,[role="button"]'?this:null;},
      getBoundingClientRect(){return {width:40,height:25};},
      click(){clicks.push(n);}
    };return obj;
  }
  const last=anchor(9999);
  last.innerText='Last';last.textContent='Last';
  last.getAttribute=function(k) {
    if(k==='href')return '/results?q=movie&page=9999';
    if(k==='aria-label')return 'Last page';
    return null;
  };
  const pages=[anchor(2),anchor(3),anchor(4),last];
  const more={
    tagName:'BUTTON',id:'loadVideos',className:'',classList:[],
    parentElement:results,ownerDocument:null,form:null,disabled:false,
    innerText:'',textContent:'',
    getAttribute(k){return {type:'button','aria-label':'Load more',
      'data-action':'load-more'}[k]||null;},
    matches(q){return q==='button'||q.includes('button');},
    closest(q){return q==='a[href],button,[role="button"]'?this:null;},
    getBoundingClientRect(){return {width:80,height:28};},
    click(){clicks.push('more');}
  };
  const cards=Array.from({length:2},(_,n)=>({
    tagName:'ARTICLE',parentElement:results,ownerDocument:null,
    querySelector(q){return q==='a[href]'||q.includes('img,picture')?{tagName:'A'}:null;}
  }));
  const document={
    documentElement:{},baseURI:uri,location:{href:uri,origin:location.origin},
    querySelector(){return null;},
    querySelectorAll(q){
      if(q==='#pager')return [pager];
      if(q==='#loadVideos')return showMore?[more]:[];
      if(q==='.search-results article')return cards;
      if(q==='input'||q==='article,div,li,figure')return [];
      if(q==='a[rel="next"]')return [];
      if(q==='button[data-action="load-more"]')return showMore?[more]:[];
      if(q==='.search-results button[aria-label="Load more"]')return showMore?[more]:[];
      if(q==='.pagination a,.pagination button,.pager a,.pager button,'+
          'nav[aria-label="pagination"] a,nav[aria-label="pagination"] button')
        return showMore?[]:pages;
      return [];
    }
  };
  for(const el of [...pages,more,active,extraActive,pager,results,sidebar,main,...cards])
    el.ownerDocument=document;
  pager.querySelectorAll=function(q){
    if(q==='a[href],button,[role="button"]')return pages;
    if(q==='[aria-current="page"],[aria-selected="true"],.active,.current')
      return duplicateActive?[active,extraActive]:[active];
    return [];
  };
  results.querySelectorAll=function(){return showMore?[more]:[];};
  const window={addEventListener(){},__zavroRecognition:{
    roots(){return [document];},
    scan(){return {selector:'.search-results article',confidence:95,
      reason:'CANDIDATE_FOUND',matchedCards:2};},
    resultRegion(){return {excluded:false};},controlHint(){return '';}
  }};
  window.top=window;
  const port={onMessage:{addListener(fn){receive=fn;}},onDisconnect:{addListener(){}},
    postMessage(m){messages.push(m);}};
  vm.runInNewContext(content,{window,document,location,URL,Uint8Array,
    browser:{runtime:{connectNative(){return port;}}},
    crypto:{getRandomValues(b){b.fill(3);return b;}},CSS:{escape:v=>v},
    setTimeout(fn){timers.push(fn);return timers.length;},clearTimeout(){}});
  const documentId=messages[0].documentId;
  receive({schemaVersion:1,event:'bound',documentId});
  function send(event,extra={}) {
    const from=messages.length;
    receive({schemaVersion:1,event,documentId,
      requestId:(++seq).toString(16).padStart(32,'0'),...extra});
    return messages.slice(from).at(-1)||null;
  }
  return {
    send,main,pager,sidebar,pages,more,clicks,
    setCurrent(v){current=v;},setDuplicate(v){duplicateActive=v;},
    setPagerParent(v){pager.parentElement=v;},
    setMoreParent(v){more.parentElement=v;},
    flush(){for(const fn of timers.splice(0))fn();}
  };
}
test('P4-C1 uses non-clickable aria-current page marker to discover reusable pager root',()=>{
  const h=fixture();
  const a=h.send('analyze');
  assert.equal(a.resultsCardSelector,'.search-results article');
  assert.equal(a.pagination,'NEXT_LINK');
  assert.equal(a.nextPageSelector,'#pager');
  assert.equal(h.send('paginate',{pagination:'NEXT_LINK',nextPageSelector:'#pager'}).status,
    'dispatched');
  h.flush();
  assert.deepEqual(h.clicks,[2]);
  h.setCurrent(2);
  assert.equal(h.send('paginate',{pagination:'NEXT_LINK',nextPageSelector:'#pager'}).status,
    'dispatched');
  h.flush();
  assert.deepEqual(h.clicks,[2,3]);
});
test('P4-C1 conflicting current-page markers do not guess the next number',()=>{
  const h=fixture();h.setDuplicate(true);
  assert.equal(h.send('analyze').pagination,'NONE');
  assert.equal(h.send('paginate',{pagination:'NEXT_LINK',nextPageSelector:'#pager'}).status,
    'pending');
  h.flush();assert.deepEqual(h.clicks,[]);
});
test('P4-C1 unrelated sidebar pager is neither learned nor clicked',()=>{
  const h=fixture();h.setPagerParent(h.sidebar);
  assert.equal(h.send('analyze').pagination,'NONE');
  assert.equal(h.send('paginate',{pagination:'NEXT_LINK',nextPageSelector:'#pager'}).status,
    'pending');
  h.flush();assert.deepEqual(h.clicks,[]);
});
test('P4-C1 a pager without shared search result owner cannot become a learned rule',()=>{
  const h=fixture();
  const unrelated={tagName:'SECTION',id:'other',className:'unrelated',
    parentElement:null,getAttribute(){return null;}};
  h.setPagerParent(unrelated);
  assert.equal(h.send('analyze').pagination,'NONE');
});
test('P4-C1 a queued pager click is revoked when the control moves into a sidebar',()=>{
  const h=fixture();
  assert.equal(h.send('paginate',{pagination:'NEXT_LINK',nextPageSelector:'#pager'}).status,
    'dispatched');
  h.setPagerParent(h.sidebar);
  h.flush();
  assert.deepEqual(h.clicks,[]);
});
test('P4-C1 a classless named Load more button within results is discovered and clicked',()=>{
  const h=fixture({showMore:true});
  const a=h.send('analyze');
  assert.equal(a.pagination,'LOAD_MORE');
  assert.equal(a.nextPageSelector,'#loadVideos');
  assert.equal(h.send('paginate',{pagination:'LOAD_MORE',
    nextPageSelector:'#loadVideos'}).status,'dispatched');
  h.flush();
  assert.deepEqual(h.clicks,['more']);
});
test('P4-C1 Load more relocated to an unrelated sidebar is neither learned nor executed',()=>{
  const h=fixture({showMore:true});
  h.setMoreParent(h.sidebar);
  assert.equal(h.send('analyze').pagination,'NONE');
  assert.equal(h.send('paginate',{pagination:'LOAD_MORE',
    nextPageSelector:'#loadVideos'}).status,'unsafe');
  h.flush();
  assert.deepEqual(h.clicks,[]);
});
