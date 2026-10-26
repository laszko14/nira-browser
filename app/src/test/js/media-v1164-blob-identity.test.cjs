'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const {webcrypto}=require('node:crypto');
function bridge(identity){
 const events={},runtimeEvents=[],messages=[];let value=identity;
 class Video{constructor(){Object.assign(this,{currentSrc:'blob:https://page.test/player',src:'blob:https://page.test/player',paused:false,isConnected:true,ended:false,mediaKeys:null,srcObject:null,muted:false,loop:false,duration:600,readyState:3,currentTime:1});}
 getBoundingClientRect(){return{left:0,top:0,width:640,height:360};}pause(){this.paused=true;}play(){throw Error('discovery must not call play');}}
 const video=new Video(),window={innerWidth:1000,innerHeight:800,devicePixelRatio:1,addEventListener:()=>{}};window.top=window;
 const document={readyState:'complete',querySelectorAll:()=>[video],addEventListener:(e,f)=>events[e]=f};
 const port={postMessage:m=>{if(m.event==='auto_offer')messages.push(m);},onMessage:{addListener:()=>{}},onDisconnect:{addListener:()=>{}}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/extensions/nira-media-detector/playback-bridge.js','utf8'),{
 location:{href:'https://page.test/watch',protocol:'https:'},document,window,HTMLVideoElement:Video,URL,Date,crypto:webcrypto,
 browser:{runtime:{id:'detector',connectNative:()=>port,sendNativeMessage:async()=>true,
 sendMessage:async()=>value,onMessage:{addListener:f=>runtimeEvents.push(f)}}}});
 return{video,messages,play:()=>{events.pointerup({isTrusted:true,type:'pointerup',target:video});events.play({target:video});},
 ready:async(v,sender='detector')=>{value=v;for(const f of runtimeEvents)await f({schemaVersion:1,event:'discovery_identity_ready'},{id:sender});}};
}
const tick=()=>new Promise(r=>setImmediate(r));
test('v1164: blob offers contain the exact discovery document and frame identity',async()=>{
 const b=bridge({documentKey:7,frameKey:12});b.play();await tick();
 assert.equal(b.messages.length,1);assert.equal(b.messages[0].discoveryDocumentKey,7);assert.equal(b.messages[0].discoveryFrameKey,12);
 assert.equal(b.messages[0].mediaUrl,'');assert.equal(b.messages[0].sourceKind,'blob');
});
test('v1164: unknown blob identity stays WEB and resumes only on a trusted readiness event',async()=>{
 const b=bridge(null);b.play();await tick();assert.equal(b.messages.length,0);
 await b.ready({documentKey:7,frameKey:12},'foreign');await tick();assert.equal(b.messages.length,0);
 await b.ready({documentKey:7,frameKey:12});await tick();assert.equal(b.messages.length,1);
});
test('v1164: a detached pending blob cannot be offered after identity becomes ready',async()=>{
 const b=bridge(null);b.play();await tick();b.video.isConnected=false;
 await b.ready({documentKey:7,frameKey:12});await tick();assert.equal(b.messages.length,0);
});
test('v1164: a changed blob source does not inherit a pending gesture after identity readiness',async()=>{
 const b=bridge(null);b.play();await tick();b.video.currentSrc=b.video.src='blob:https://page.test/other';
 await b.ready({documentKey:7,frameKey:12});await tick();assert.equal(b.messages.length,0);
});
test('v1164: a paused pending blob is never promoted by a readiness event',async()=>{
 const b=bridge(null);b.play();await tick();b.video.paused=true;
 await b.ready({documentKey:7,frameKey:12});await tick();assert.equal(b.messages.length,0);
});
test('v1164: a readiness event during an outstanding lookup is not lost',async()=>{
 let release;const b=bridge(new Promise(resolve=>release=resolve));b.play();await tick();
 await b.ready({documentKey:7,frameKey:12});release(null);await tick();await tick();
 assert.equal(b.messages.length,1);
});
