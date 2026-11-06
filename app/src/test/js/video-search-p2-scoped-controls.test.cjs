'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const base=path.resolve(__dirname,'../../main/assets/extensions/zavro-video-search');
const recognition=fs.readFileSync(path.join(base,'recognition.js'),'utf8');
const content=fs.readFileSync(path.join(base,'content.js'),'utf8');
function fixture(surface='SHADOW') {
  const posted=[],timers=[];
  let receive,clicks=0;
  const location=new URL('https://films.example/');
  const root={mode:'open',baseURI:location.href,location:{origin:location.origin,href:location.href}};
  const form={tagName:'FORM',id:'form',className:'',ownerDocument:root,
    getAttribute(k){return ({method:'GET',action:'/search',role:'search'})[k]||null;},
    querySelector(){return null;},
    querySelectorAll(k){return k==='button,input[type="submit"],[role="button"]'?[button]:[];}};
  const input={tagName:'INPUT',id:'query',type:'search',name:'q',placeholder:'Search',
    disabled:false,readOnly:false,form,ownerDocument:root,getRootNode(){return root;},
    getAttribute(k){return ({type:'search','aria-label':'Search films'})[k]||null;},
    closest(){return null;},labels:[],
    getBoundingClientRect(){return {width:180,height:35};},
    dispatchEvent(){},matches(s){return s==='input';}};
  const button={tagName:'BUTTON',id:'go',className:'',classList:[],form,
    disabled:false,ownerDocument:root,getRootNode(){return root;},
    getAttribute(k){return ({type:'submit','aria-label':'Search'})[k]||null;},
    closest(){return null;},
    matches(s){return s.includes('button')||s.includes('submit');},
    getBoundingClientRect(){return {width:35,height:35};},
    click(){clicks++;},querySelector(){return null;}};
  root.querySelectorAll=function(k){
    if(k==='input'||k==='#query')return [input];
    if(k==='#go')return [button];
    return [];
  };
  const host={tagName:surface==='FRAME'?'IFRAME':'FILM-SEARCH',id:'searchHost',
    classList:[],isConnected:true,getAttribute(){return null;},
    shadowRoot:surface==='SHADOW'?root:null,
    contentDocument:surface==='FRAME'?root:null};
  const document={documentElement:{},
    querySelectorAll(k){
      return k==='article,div,section,main,form,iframe,*'||k==='#searchHost'?[host]:[];
    },querySelector(){return null;}};
  const window={addEventListener(){}};window.top=window;
  const port={onMessage:{addListener(fn){receive=fn;}},
    onDisconnect:{addListener(){}},postMessage(m){posted.push(m);}};
  class Input {}
  // Real HTMLInputElement.value has BOTH accessors. The query integrity
  // guard must read back the same value after the native setter runs.
  Object.defineProperty(Input.prototype,'value',{
    set(value){this._value=value;},get(){return this._value;}
  });
  Object.setPrototypeOf(input,Input.prototype);
  const context={window,document,URL,location,console,Uint8Array,HTMLInputElement:Input,
    Event:class Event{},CSS:{escape:x=>x},
    btoa:x=>Buffer.from(x,'binary').toString('base64'),
    atob:x=>Buffer.from(x,'base64').toString('binary'),
    crypto:{getRandomValues(b){b.fill(9);return b;}},
    browser:{runtime:{connectNative(){return port;}}},
    setTimeout(fn){timers.push(fn);return timers.length;},clearTimeout(){}};
  vm.runInNewContext(recognition,context);
  vm.runInNewContext(content,context);
  const id=posted[0].documentId;
  receive({schemaVersion:1,event:'bound',documentId:id});
  function send(event,extra={}) {
    const n=posted.length;
    receive({schemaVersion:1,event,documentId:id,requestId:'1'.padStart(32,'0'),...extra});
    return posted.slice(n);
  }
  return {send,posted,host,root,input,button,recognition:window.__zavroRecognition,
    get clicks(){return clicks;},flush(){for(const fn of timers.splice(0))fn();}};
}
for(const surface of ['SHADOW','FRAME']) {
  test('P2 finds and executes scoped '+surface+' GET search',()=>{
    const h=fixture(surface),analysis=h.send('analyze').at(-1);
    assert.equal(analysis.status,'partial');
    assert.match(analysis.inputSelector,/^zcs1:/);
    assert.match(analysis.submitSelector,/^zcs1:/);
    const outcome=h.send('search',{query:'Example',inputSelector:analysis.inputSelector,
      submission:analysis.submission,submitSelector:analysis.submitSelector});
    assert.equal(outcome.at(-1).event,'submitted');
    h.flush();assert.equal(h.clicks,1);
  });
  test('P2 cancels delayed '+surface+' action for detached host',()=>{
    const h=fixture(surface),analysis=h.send('analyze').at(-1);
    h.send('search',{query:'Example',inputSelector:analysis.inputSelector,
      submission:analysis.submission,submitSelector:analysis.submitSelector});
    h.host.isConnected=false;h.flush();assert.equal(h.clicks,0);
  });
}
test('P2 iframe replacement invalidates original locator',()=>{
  const h=fixture('FRAME'),analysis=h.send('analyze').at(-1);
  h.host.contentDocument={location:{origin:'https://films.example'},querySelectorAll(){
    return [h.input];
  }};
  assert.equal(h.recognition.resolve(analysis.inputSelector),null);
});
test('P2 closed shadow and cross-origin iframe have no discovered inputs',()=>{
  for(const surface of ['SHADOW','FRAME']){
    const h=fixture(surface);
    if(surface==='SHADOW')h.host.shadowRoot={mode:'closed'};
    else h.root.location.origin='https://elsewhere.example';
    assert.equal(h.recognition.inputs().length,0);
  }
});
