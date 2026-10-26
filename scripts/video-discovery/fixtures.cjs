'use strict';
// Real Chromium DOM + controlled WebExtension/network adapters, NOT Gecko/Android.
// No fixture is allowed to reach the network. Only synthetic reserved .test URLs.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const root = path.resolve(__dirname, '../..');
const assets = process.env.DISCOVERY_ASSETS || path.join(root, 'app/src/main/assets/extensions/nira-media-detector');
const read = name => fs.readFileSync(path.join(assets, name), 'utf8');
const cdn = name => 'https://cdn.test/' + name;
const video = (src, attrs='') => `<video preload="none" ${attrs} src="${src}"></video>`;
const script = obj => `<script type="application/json">${JSON.stringify(obj)}</script>`;
const frames = [{url:'https://frame.test/embed', html:video(cdn('framed.mp4'))},
  {url:'https://nested.test/embed',html:`<iframe src="https://frame.test/embed"></iframe>`}];
const cases = [
  {id:'mp4',html:video(cdn('movie.mp4')),films:[['movie.mp4']]},
  {id:'sources',html:'<video preload="none"><source src="https://cdn.test/a.mp4" type="video/mp4"><source src="https://cdn.test/a.webm" type="video/webm"></video>',films:[['a.mp4','a.webm']]},
  {id:'hls-preplay',html:video(cdn('master.m3u8')),films:[['master.m3u8']]},
  {id:'dash-preplay',html:video(cdn('manifest.mpd')),films:[['manifest.mpd']]},
  {id:'mime-extensionless',html:'<video preload="none"><source src="https://cdn.test/play?id=1" type="video/mp4"></video>',films:[['play?id=1']]},
  {id:'iframe',html:'<iframe src="https://frame.test/embed"></iframe>',films:[['framed.mp4']]},
  {id:'nested-iframe',html:'<iframe src="https://nested.test/embed"></iframe>',films:[['framed.mp4']]},
  {id:'dynamic',html:'<main></main>',films:[['dynamic.mp4']],before:async p=>p.evaluate(html=>document.querySelector('main').innerHTML=html,video(cdn('dynamic.mp4')))},
  {id:'lazy-source',html:'<video preload="none"><source data-src="https://cdn.test/lazy" type="video/mp4"></video>',films:[['lazy']]},
  {id:'jsonld',html:'<script type="application/ld+json">'+JSON.stringify({'@type':'VideoObject',name:'Full fixture title',contentUrl:cdn('schema.mp4'),thumbnailUrl:cdn('poster.jpg')})+'</script>',films:[['schema.mp4']]},
  {id:'player-config',html:script({player:{sources:[{file:cdn('player.m3u8')}]}}),films:[['player.m3u8']]},
  {id:'config-variants-playlist',html:script({playlist:[{sources:[{file:cdn('variant-low.mp4')},{file:cdn('variant-high.mp4')}]},{sources:[{file:cdn('separate.mp4')}]}]}),films:[['variant-low.mp4','variant-high.mp4'],['separate.mp4']]},
  {id:'hydration-wrapper',html:script({props:{pageProps:{entry:{attributes:{playerConfig:{sources:[{src:cdn('hydration.mp4')}]}}}}}}),films:[['hydration.mp4']]},
  {id:'relative-player-literal',html:"<script>/* shaka */ player.load('/relative.mpd');</script>",films:[['https://page.test/relative.mpd']]},
  {id:'many-40',html:Array.from({length:40},(_,i)=>video(cdn(`movie${i}.mp4`))).join(''),films:Array.from({length:40},(_,i)=>[`/movie${i}.mp4`])},
  {id:'tail-after-130-placeholders',html:'<video preload="none"></video>'.repeat(130)+Array.from({length:20},(_,i)=>video(cdn(`tail${i}.mp4`))).join(''),films:Array.from({length:20},(_,i)=>[`/tail${i}.mp4`])},
  {id:'late-after-160-links',html:Array.from({length:170},(_,i)=>`<a href="/article/${i}">article</a>`).join(''),films:[['late.mp4']],before:async p=>p.evaluate(()=>{const a=document.createElement('a');a.href='https://cdn.test/late.mp4';document.body.append(a);})},
  {id:'open-shadow',html:'<div id="host"></div>',films:[['shadow.mp4']],before:async p=>p.evaluate(html=>{const host=document.createElement('div');host.attachShadow({mode:'open'}).innerHTML=html;document.body.append(host);},video(cdn('shadow.mp4')))},
  {id:'after-play-only',html:'<video preload="none"></video>',films:[['after.mp4']],postOnly:true,after:async p=>p.evaluate(()=>{document.querySelector('video').src='https://cdn.test/after.mp4';})},
  {id:'multi-player',html:video(cdn('first.mp4'))+video(cdn('second.webm')),films:[['first.mp4'],['second.webm']]},
  {id:'metadata-mime',html:'<meta property="og:video:type" content="video/mp4"><meta property="og:video" content="https://cdn.test/opaque">',films:[['opaque']]},
  {id:'spa-navigation',html:video(cdn('spa.mp4')),films:[['spa.mp4']],before:async p=>p.evaluate(()=>{history.pushState({},'', '/route-2');document.querySelector('video').setAttribute('data-title','SPA title');})},
  {id:'dynamic-config-text',html:'<script id="config" type="application/json">{}</script>',films:[['updated.mp4']],before:async p=>p.evaluate(()=>{document.querySelector('#config').textContent=JSON.stringify({player:{src:'https://cdn.test/updated.mp4'}});})},
  {id:'negative-declarations',html:'<img src="https://cdn.test/p.jpg"><a href="/index.html">x</a><script src="/main.js"></script><link href="/style.css"><div data-src="https://cdn.test/thumb.webp"></div>',films:[]},
  {id:'negative-segments',html:video(cdn('init.mp4'))+video(cdn('chunk-7.m4s'))+video(cdn('seg-2.ts')),films:[]},
];
function background(route, fetchImpl) {
  let receive,before,headers,removed;
  const timers=new Map();let next=0;
  const metrics={fetches:0,cancelled:0,nativeMessages:0};
  const browser={runtime:{id:'fixture',onMessage:{addListener:f=>receive=f},sendNativeMessage:async()=>{metrics.nativeMessages++;return true;}},
    tabs:{sendMessage:route,onRemoved:{addListener:f=>removed=f}},webRequest:{onBeforeRequest:{addListener:f=>before=f},onHeadersReceived:{addListener:f=>headers=f}}};
  const ctx={browser,URL,TextEncoder,TextDecoder,Date,AbortController,
    setTimeout:(f,n)=>{const id=++next;timers.set(id,setTimeout(f,n));return id;},clearTimeout:id=>{clearTimeout(timers.get(id));timers.delete(id);},
    fetch:fetchImpl?async(...args)=>{metrics.fetches++;return fetchImpl(...args);}:undefined};
  vm.runInNewContext(read('manifest-parser.js'),ctx);vm.runInNewContext(read('background.js'),ctx);
  return {receive,before,headers,removed,metrics,close:()=>{for(const t of timers.values())clearTimeout(t);}};
}
async function domCase(browser,c) {
  const context=await browser.newContext();
  const page=await context.newPage();let nextFrame=0;
  const frameIds=new Map(),messages=[];let nativeMessages=0;
  const bg=background(async()=>true);
  await page.exposeBinding('__fixtureSend',async({frame},kind,message)=>{
    if(!frameIds.has(frame))frameIds.set(frame,frame===page.mainFrame()?0:++nextFrame);
    if(kind==='native'){nativeMessages++;if(message.event==='media_candidate')messages.push(message);return true;}
    return bg.receive(message,{id:'fixture',tab:{id:7},frameId:frameIds.get(frame),url:frame.url()});
  });
  await context.route('**/*',route=>{
    const url=route.request().url();
    const frame=frames.find(x=>x.url===url);
    const html=frame?.html||(url.startsWith('https://page.test/')?c.html:'');
    return route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><html><head></head><body>'+html+'</body></html>'});
  });
  await page.goto('https://page.test/watch');
  const install=async frame=>{
    await frame.evaluate(()=>{
      window.__cost={callbacks:0,callbackMs:0,maxCallbackMs:0,queries:0,returned:0,mutations:0,runtimeMessages:0};
      const cost=window.__cost;
      for(const prototype of [Document.prototype,Element.prototype,DocumentFragment.prototype]){
        const original=prototype.querySelectorAll;
        if(original)prototype.querySelectorAll=function(...args){const result=original.apply(this,args);cost.queries++;cost.returned+=result.length;return result;};
      }
      const originalTimeout=window.setTimeout;
      window.setTimeout=(fn,ms,...args)=>originalTimeout(()=>{const start=performance.now();fn(...args);const dt=performance.now()-start;cost.callbacks++;cost.callbackMs+=dt;cost.maxCallbackMs=Math.max(cost.maxCallbackMs,dt);},ms);
      const Original=window.MutationObserver;
      window.MutationObserver=class extends Original{constructor(fn){super(records=>{cost.mutations++;fn(records);});}};
      window.browser={runtime:{id:'fixture',onMessage:{addListener:()=>{}},sendMessage:message=>{cost.runtimeMessages++;return window.__fixtureSend('runtime',message);},sendNativeMessage:(_,message)=>window.__fixtureSend('native',message)}};
    });
    await frame.evaluate(source=>{const start=performance.now();(0,eval)(source);window.__cost.initialMs=performance.now()-start;},read('content.js'));
  };
  await install(page.mainFrame());
  await page.waitForTimeout(60);
  for(const frame of page.frames().slice(1))await install(frame);
  if(c.before)await c.before(page);
  await page.waitForTimeout(1800);
  const pre=[...messages];
  if(c.after){await c.after(page);await page.waitForTimeout(600);}
  const cost=[];
  for(const f of page.frames())cost.push(await f.evaluate(()=>window.__cost||null));
  const match=(msg,film)=>film.some(url=>msg.url.includes(url));
  const detected=list=>c.films.filter(film=>list.some(msg=>match(msg,film))).length;
  const unexpected=[...new Set(messages.filter(msg=>!c.films.some(f=>match(msg,f))).map(x=>x.url))].length;
  const keys=messages.map(x=>JSON.stringify([x.url,x.source,x.groupId,x.title,x.durationSeconds,x.contentType]));
  // Mirror the repository's exact-URL upsert before counting variant groups;
  // duplicate hints for the same URL and results from an old SPA document are
  // not duplicate entries. The real Kotlin repository is separately tested.
  const latestDocument=Math.max(0,...messages.map(message=>message.documentKey));
  const records=new Map();
  for(const message of messages.filter(message=>message.documentKey===latestDocument)){
    const identity=(message.frameKey||0)+":"+message.url;
    if(!records.has(identity))records.set(identity,message);
  }
  const liveMessages=[...records.values()];
  const groupFilms=new Map();
  for(const message of liveMessages){if(!message.groupId)continue;
    const indices=groupFilms.get(message.groupId)||new Set();
    c.films.forEach((film,index)=>{if(match(message,film))indices.add(index);});groupFilms.set(message.groupId,indices);
  }
  const wrongMerges=[...groupFilms.values()].filter(indices=>indices.size>1).length;
  const duplicateMaterials=c.films.reduce((n,film)=>{
    const identities=new Set(liveMessages.filter(msg=>match(msg,film)).map(msg=>msg.groupId||msg.url));
    return n+Math.max(0,identities.size-1);
  },0);
  const result={id:c.id,films:c.films.length,preEligible:c.postOnly?0:c.films.length,pre:detected(pre),post:detected(messages),falsePositives:unexpected,
    duplicateMessages:keys.length-new Set(keys).size,messages:messages.length,nativeMessages,wrongMerges,duplicateMaterials,cost:cost.filter(Boolean),extraRequests:bg.metrics.fetches};
  bg.close();await context.close();return result;
}
// Network fixtures execute the same production background/parser. Browser responses
// and follow-up GET bodies are controlled; no media body is downloaded or played.
const networkCases = [
  {id:'network-mime',url:cdn('opaque?id=17'),mime:'video/mp4',films:1},
  {id:'signed-revisions',url:cdn('signed.m3u8?token=first'),mime:'application/vnd.apple.mpegurl',films:1,rotate:true},
  {id:'hls-master-variants',url:cdn('master.m3u8'),mime:'application/vnd.apple.mpegurl',films:1,body:'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=640x360\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=4000,RESOLUTION=1920x1080\nhigh.m3u8'},
  {id:'hls-media-blob-network-source',url:cdn('live.m3u8'),mime:'application/vnd.apple.mpegurl',films:1,body:'#EXTM3U\n#EXTINF:6,\na.ts'},
  {id:'dash-template',url:cdn('manifest.mpd'),mime:'application/dash+xml',films:1,body:'<MPD type="dynamic"><Period><AdaptationSet mimeType="video/mp4"><SegmentTemplate initialization="init.mp4" media="seg-$Number$.m4s"/><Representation height="720"/></AdaptationSet></Period></MPD>'},
  {id:'redirect-cdn',url:cdn('final.mp4'),mime:'video/mp4',films:1,redirect:true},
  {id:'negative-html-mp4',url:cdn('error.mp4'),mime:'text/html',films:0},
  {id:'negative-octet',url:cdn('opaque'),mime:'application/octet-stream',films:0},
  {id:'negative-segment-ts',url:cdn('s.ts'),mime:'video/mp2t',films:0},
  {id:'negative-segment-m4s',url:cdn('s.m4s'),mime:'video/mp4',films:0},
  {id:'negative-init',url:cdn('init.mp4'),mime:'video/mp4',films:0},
  {id:'negative-manifest',url:cdn('bad.m3u8'),mime:'application/vnd.apple.mpegurl',films:0,body:'#EXTM3U\n#comment'},
  {id:'negative-drm',url:cdn('drm.mpd'),mime:'application/dash+xml',films:0,body:'<MPD><Period><AdaptationSet><ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/></AdaptationSet></Period></MPD>'},
  {id:'negative-auth',url:cdn('auth.m3u8'),mime:'text/html',films:0,status:403},
  {id:'negative-stale-doc',url:cdn('stale.mp4'),mime:'video/mp4',films:0,stale:true},
  {id:'tab-close-pending',url:cdn('closing.m3u8'),mime:'application/vnd.apple.mpegurl',films:0,closing:true,body:'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nchild.m3u8'},
];
async function networkCase(c) {
  const messages=[]; let fetchResolve;
  const response=(url,body)=>({ok:true,status:200,url,headers:{get:name=>name==='content-type'?c.mime:null},body:{
    cancel:async()=>{},getReader:()=>{let done=false;return{read:async()=>done?{done:true}:(done=true,{done:false,value:new TextEncoder().encode(body)}),cancel:async()=>{},releaseLock:()=>{}};}
  }});
  const b=background(async(id,msg)=>{messages.push(msg);return true;},c.body?async(url,options)=>{
    if(c.closing)await new Promise(resolve=>fetchResolve=resolve);
    if(url.endsWith('.ts'))return{...response(url,''),headers:{get:()=> 'video/mp2t'}};
    return response(url,c.body);
  }:undefined);
  const sender={id:'fixture',tab:{id:7},frameId:0,url:'https://page.test/watch'};
  const start=()=>b.receive({schemaVersion:1,event:'document_started',pageUrl:sender.url},sender);
  await start();
  const details={tabId:7,requestId:'request1',frameId:0,url:c.url,documentUrl:sender.url,incognito:false,statusCode:c.status||200,responseHeaders:[{name:'Content-Type',value:c.mime}]};
  b.before(details);
  if(c.redirect)b.headers({...details,url:'https://origin.test/play',statusCode:302});
  if(c.stale)await start();
  b.headers(details);
  if(c.rotate){const second={...details,requestId:'request2',url:c.url.replace('first','second')};b.before(second);b.headers(second);}
  for(let i=0;i<4;i++)await new Promise(r=>setImmediate(r));
  if(c.closing){b.removed(7);fetchResolve();for(let i=0;i<4;i++)await new Promise(r=>setImmediate(r));}
  const identities=new Map();
  for(const msg of messages)identities.set(msg.groupId||msg.url,msg);
  const viable=[...identities.values()].filter(x=>!['auth_error','expired','unplayable'].includes(x.verification));
  // A closed tab has no repository scope, even though the initial observed hint
  // was delivered before closing; stale post-close derived events are counted.
  const count=c.closing?messages.filter(x=>x.provenance==='derived').length:viable.length;
  const truePositives=Math.min(count,c.films),falsePositives=Math.max(0,count-c.films);
  const result={id:c.id,films:c.films,preEligible:c.films,pre:truePositives,post:truePositives,falsePositives,duplicateMessages:0,messages:messages.length,nativeMessages:messages.length+b.metrics.nativeMessages,cost:[],extraRequests:b.metrics.fetches,wrongMerges:0,duplicateMaterials:0};
  b.close();return result;
}
async function main(){
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox']});
  const results=[];
  try{for(const c of cases)results.push(await domCase(browser,c));for(const c of networkCases)results.push(await networkCase(c));}finally{await browser.close();}
  const sum=key=>results.reduce((n,c)=>n+c[key],0),cost=results.flatMap(c=>c.cost);
  const total={films:sum('films'),preEligible:sum('preEligible'),pre:sum('pre'),post:sum('post'),falsePositives:sum('falsePositives'),duplicateMessages:sum('duplicateMessages'),messages:sum('messages'),nativeMessages:sum('nativeMessages'),extraRequests:sum('extraRequests'),
    recallPre:sum('pre')/sum('preEligible'),recallPost:sum('post')/sum('films'),precision:sum('post')/(sum('post')+sum('falsePositives')),
    initialScanMs:cost.reduce((n,c)=>n+c.initialMs,0),callbackMs:cost.reduce((n,c)=>n+c.callbackMs,0),maxCallbackMs:Math.max(...cost.map(c=>c.maxCallbackMs)),queries:cost.reduce((n,c)=>n+c.queries,0),returned:cost.reduce((n,c)=>n+c.returned,0),mutations:cost.reduce((n,c)=>n+c.mutations,0),wrongMerges:results.reduce((n,c)=>n+(c.wrongMerges||0),0),duplicateMaterials:sum('duplicateMaterials')};
  const report={engine:'Chromium '+browser.version(),automation:'Playwright '+require('playwright/package.json').version,layer:'real DOM; WebExtension/native/network controlled adapters; no Android',total,cases:results};
  if(process.env.DISCOVERY_REPORT)fs.writeFileSync(process.env.DISCOVERY_REPORT,JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({total,cases:results.map(({id,films,pre,post,falsePositives})=>({id,films,pre,post,falsePositives}))},null,2));
  if(!process.env.DISCOVERY_BASELINE){assert.equal(total.post,total.films,'all available fixture films');assert.equal(total.pre,total.preEligible,'all preplay-available sources');assert.equal(total.falsePositives,0);assert.equal(total.wrongMerges,0);assert.equal(total.duplicateMaterials,0);}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
