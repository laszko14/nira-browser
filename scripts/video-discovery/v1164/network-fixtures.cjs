'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const frozen=require('./frozen-cases.json');
const crypto=require('node:crypto');
const spec=path.join(__dirname,'frozen-cases.json');
assert.equal(crypto.createHash('sha256').update(fs.readFileSync(spec)).digest('hex'),fs.readFileSync(path.join(__dirname,'frozen-cases.sha256'),'utf8').split(' ')[0]);
const assets=process.env.DISCOVERY_ASSETS||path.resolve(__dirname,'../../../app/src/main/assets/extensions/nira-media-detector');
const tick=()=>new Promise(r=>setImmediate(r));
const uuid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
function world(){
 let receive,before,headers,removed,history,navigate,now=100000;
 const costs={callbacks:0,totalCallbackMs:0,maxCallbackMs:0};
 const measured=fn=>(...args)=>{const start=performance.now();try{return fn(...args);}finally{const ms=performance.now()-start;costs.callbacks++;costs.totalCallbackMs+=ms;costs.maxCallbackMs=Math.max(costs.maxCallbackMs,ms);}};
 const sent=[],controls=[],native=[],frameDetails=new Map(),timers=new Map();let timer=0;
 const browser={runtime:{id:'fixture',onMessage:{addListener:f=>receive=f},sendNativeMessage:async(_,m)=>{native.push(m);return true;}},
 tabs:{sendMessage:async(tab,m,options)=>{(m.event==='media_candidate'?sent:controls).push({tab,message:m,options});return true;},onRemoved:{addListener:f=>removed=f}},
 webRequest:{onBeforeRequest:{addListener:f=>before=f},onHeadersReceived:{addListener:f=>headers=f},onCompleted:{addListener:()=>{}},onErrorOccurred:{addListener:()=>{}}},
 webNavigation:{getFrame:async({tabId,frameId})=>frameDetails.get(tabId+':'+frameId)||null,
 onHistoryStateUpdated:{addListener:f=>history=f},onBeforeNavigate:{addListener:f=>navigate=f},onCommitted:{addListener:()=>{}}}};
 const ctx={browser,URL,TextEncoder,TextDecoder,AbortController,Date:{now:()=>now},
 setTimeout:(fn,ms)=>{const id=++timer;timers.set(id,{fn,at:now+ms});return id;},clearTimeout:id=>timers.delete(id)};
 for(const name of ['manifest-parser.js','background.js'])vm.runInNewContext(fs.readFileSync(path.join(assets,name),'utf8'),ctx);
 const sender=(doc=1,frameId=0,url='https://page.test/watch',tab=7)=>({id:'fixture',tab:{id:tab,incognito:false,cookieStoreId:'firefox-container-1'},frameId,url,documentId:uuid(doc)});
 let root;
 async function start(s=sender()){root=await receive({schemaVersion:1,event:'document_started',pageUrl:s.url},s);return root;}
 async function ready(s=sender()){return receive({schemaVersion:1,event:'document_ready',documentKey:root?.documentKey},s);}
 async function frame(doc=2,id=1,url='https://frame.test/embed',parentDoc=1,parentFrameId=0){
 const s=sender(doc,id,url);frameDetails.set('7:'+id,{frameId:id,documentId:uuid(doc),parentDocumentId:uuid(parentDoc),parentFrameId,url});
 const reply=await receive({schemaVersion:1,event:'frame_started',frameUrl:url},s);
 if(reply?.frameKey)await receive({schemaVersion:1,event:'frame_ready',frameKey:reply.frameKey},s);return reply;}
 function request(extra={}){return{tabId:7,requestId:'r',frameId:0,parentFrameId:-1,documentId:uuid(1),documentUrl:'https://page.test/watch',url:'https://cdn.test/movie.mp4',incognito:false,cookieStoreId:'firefox-container-1',type:'xmlhttprequest',statusCode:200,responseHeaders:[{name:'Content-Type',value:'video/mp4'}],...extra};}
 async function advance(ms){now+=ms;for(const[id,t]of [...timers])if(t.at<=now){timers.delete(id);t.fn();}await tick();}
 return{browser,sender,start,ready,frame,request,sent,controls,native,costs,receive:measured(receive),before:measured(before),headers:measured(headers),removed,
 advance,frameDetails,history:e=>history?.(e),navigate:e=>navigate?.(e)};
}
async function run(c){
 const w=world();let r=w.request();
 const root=async(s)=>{await w.start(s);await w.ready(s);};
 const frameRequest=(doc=2,id=1)=>w.request({frameId:id,parentFrameId:0,documentId:uuid(doc),parentDocumentId:uuid(1),documentUrl:'https://frame.test/embed'});
 switch(c.scenario){
 case'early_root':case'early_before':case'early_redirect':case'expired':case'no_uuid':case'invalid_frame':case'closed':case'profile_mismatch':case'private_early':case'signed_early':case'uuid_mismatch':case'late_expired':
  if(c.scenario==='no_uuid')delete r.documentId;
  if(c.scenario==='invalid_frame')r.frameId=-1;
  if(c.scenario==='profile_mismatch')r.cookieStoreId='firefox-container-2';
  if(c.scenario==='private_early')r.incognito=true;
  if(c.scenario==='signed_early'){r.url='https://cdn.test/index.m3u8?token=exact%2Fvalue';r.responseHeaders=[{name:'Content-Type',value:'application/vnd.apple.mpegurl'}];}
  w.before(r);
  if(c.scenario!=='early_before'&&c.scenario!=='late_expired')w.headers(c.scenario==='early_redirect'?{...r,statusCode:302}:r);
  if(c.scenario==='closed')w.removed(7);
  if(c.scenario==='expired'||c.scenario==='late_expired')await w.advance(6000);
  {const s=w.sender(c.scenario==='uuid_mismatch'?99:1);if(c.scenario==='private_early')s.tab.incognito=true;await root(s);}
  if(['early_before','early_redirect','late_expired'].includes(c.scenario))w.headers(r);
  break;
 case'early_frame':case'frame_redirect':case'two_frames':case'frame_replaced':case'foreign_parent':
  await root();r=frameRequest();if(c.scenario==='foreign_parent')r.parentDocumentId=uuid(99);
  w.before(r);w.headers(c.scenario==='frame_redirect'?{...r,statusCode:302}:r);
  if(c.scenario==='two_frames'){const s=frameRequest(3,2);s.requestId='r2';s.url='https://cdn.test/other.mp4';w.before(s);w.headers(s);await w.frame(3,2);}
  await w.frame(c.scenario==='frame_replaced'?3:2);
  if(c.scenario==='frame_redirect')w.headers(r);
  break;
 case'two_tabs':
  w.before(r);w.headers(r);const other={...r,tabId:8,documentId:uuid(8),requestId:'other'};w.before(other);w.headers(other);
  await root();await root(w.sender(8,0,'https://page.test/watch',8));break;
 case'stale_navigation':
  await root();w.before(r);w.navigate({tabId:7,frameId:0,url:'https://page.test/new',timeStamp:100001});
  await root(w.sender(9,0,'https://page.test/new'));w.headers(r);break;
 case'blank':case'srcdoc':case'unproven':case'nested_srcdoc':
  await root();if(c.scenario==='nested_srcdoc')await w.frame(2,1,'about:blank');
  {const url=c.scenario==='blank'?'about:blank':'about:srcdoc';const nested=c.scenario==='nested_srcdoc';
   const reply=await w.frame(nested?3:2,nested?2:1,url,c.scenario==='unproven'?99:nested?2:1,nested?1:0);
   const s=w.sender(nested?3:2,nested?2:1,url);
   const m=await w.receive({schemaVersion:1,event:'media_candidate',source:'page',url:'https://cdn.test/opaque.mp4'},s);
   if(m&&reply)w.sent.push({tab:7,message:m});}
  break;
 case'stale_sender':case'retired_sender':case'forged_uuid':
  await root();const old=await w.frame();
  if(c.scenario==='stale_sender')await w.frame(3);
  if(c.scenario==='retired_sender')await w.receive({schemaVersion:1,event:'frame_ended',frameKey:old?.frameKey},w.sender(2,1,'https://frame.test/embed'));
  {const m=await w.receive({schemaVersion:1,event:'media_candidate',source:'page',url:'https://cdn.test/old.mp4',...(c.scenario==='forged_uuid'?{documentId:uuid(3)}:{})},w.sender(2,1,'https://frame.test/embed'));if(m)w.sent.push({tab:7,message:m});}
  break;
 case'push':case'replace':case'hash':case'two_history':case'stale_history':
  await root();w.controls.length=0;
  w.history({tabId:7,frameId:0,documentId:uuid(c.scenario==='stale_history'?9:1),url:c.scenario==='hash'?'https://page.test/watch#chapter':'https://page.test/next'});
  if(c.scenario==='two_history')w.history({tabId:7,frameId:0,documentId:uuid(1),url:'https://page.test/third'});
  break;
 case'flood':
  for(let i=0;i<200;i++){const e={...r,requestId:'r'+i,url:'https://cdn.test/'+i+'.mp4'};w.before(e);w.headers(e);}await root();break;
 case'root_ready':
  await w.start();w.before(r);w.headers(r);await w.ready();break;
 default:throw Error('unknown frozen scenario');
 }
 for(let i=0;i<5;i++)await tick();
 const actual=c.expectedControls===undefined?w.sent.length:w.controls.filter(x=>x.message.event==='discovery_rescan').length;
 const expected=c.expectedControls??c.expected;
 const pass=c.max!==undefined?actual<=c.max:actual===expected;
 return{id:c.id,actual,expected:c.max===undefined?expected:'<='+c.max,pass,misassigned:w.sent.filter(x=>x.tab!==7&&!(c.scenario==='two_tabs'&&x.tab===8)).length,callbackCost:w.costs,nativeMessages:w.native.length,extraGet:0,messages:w.sent.length,unassigned:w.native.filter(x=>x.event==='network_unassigned').length};
}
async function main(){const group=process.env.DISCOVERY_SET||'B';const results=[];for(const c of frozen[group])results.push(await run(c));
 const report={measured:{nativeMessages:results.reduce((n,x)=>n+x.nativeMessages,0),routedMessages:results.reduce((n,x)=>n+x.messages,0),extraGet:results.reduce((n,x)=>n+x.extraGet,0),unassigned:results.reduce((n,x)=>n+x.unassigned,0),maxSyncCallbackMs:Math.max(0,...results.map(x=>x.callbackCost.maxCallbackMs))},layer:'JS production background with controlled Gecko adapters; not Android',set:group,scenarios:results.length,passed:results.filter(x=>x.pass).length,failed:results.filter(x=>!x.pass).length,misassigned:results.reduce((n,x)=>n+x.misassigned,0),cases:results};
 if(process.env.DISCOVERY_REPORT)fs.writeFileSync(process.env.DISCOVERY_REPORT,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
 if(!process.env.DISCOVERY_BASELINE)assert.equal(report.failed,0);
}
if(require.main===module)main().catch(e=>{console.error(e.message);process.exitCode=1;});
module.exports={world,run};
