'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {world}=require('../../../../scripts/video-discovery/v1164/network-fixtures.cjs');

test('v1164: a child registered before its parent is retried only after proven parent registration',async()=>{
 const w=world();await w.start();await w.ready();w.controls.length=0;
 assert.equal(await w.frame(3,2,'about:srcdoc',2,1),null);
 await w.frame(2,1,'https://frame.test/parent',1,0);
 const wake=w.controls.filter(x=>x.message.event==='discovery_rescan'&&x.message.kind==='parent_ready');
 assert.equal(wake.length,1);assert.equal(wake[0].options.frameId,2);
 const child=await w.frame(3,2,'about:srcdoc',2,1);assert.ok(child.frameKey>0);
});
test('v1164: a negative frame ID in an otherwise registered document cannot bind a stream',async()=>{
 const w=world();await w.start();await w.ready();const r=w.request({frameId:-1});w.before(r);w.headers(r);
 await new Promise(r=>setImmediate(r));assert.equal(w.sent.length,0);
});
test('v1164: late delivery of a navigation-start event cannot erase a newer document registration',async()=>{
 const w=world();await w.start();await w.ready();
 w.navigate({tabId:7,frameId:0,url:'https://page.test/watch',timeStamp:99999});
 const r=w.request();w.before(r);w.headers(r);await new Promise(r=>setImmediate(r));
 assert.equal(w.sent.length,1);
});
test('v1164: Gecko frozen sender URL after pushState is verified against the current document UUID',async()=>{
 const w=world();await w.start();await w.ready();
 w.history({tabId:7,frameId:0,documentId:w.sender().documentId,url:'https://page.test/next'});
 w.frameDetails.set('7:0',{frameId:0,documentId:w.sender().documentId,parentFrameId:-1,url:'https://page.test/next'});
 const reply=await w.receive({schemaVersion:1,event:'document_started',pageUrl:'https://page.test/next'},w.sender());
 assert.ok(reply?.documentKey>1);
 assert.equal(await w.receive({schemaVersion:1,event:'document_ready',documentKey:reply.documentKey},w.sender()),true);
 const identity=await w.receive({schemaVersion:1,event:'frame_identity'},w.sender());
 assert.equal(identity.documentKey,reply.documentKey);
});
test('v1164: a forged SPA route is rejected when trusted current frame metadata differs',async()=>{
 const w=world();await w.start();await w.ready();
 w.frameDetails.set('7:0',{frameId:0,documentId:w.sender().documentId,parentFrameId:-1,url:'https://page.test/current'});
 assert.equal(await w.receive({schemaVersion:1,event:'document_started',pageUrl:'https://page.test/forged'},w.sender()),null);
});
test('v1164: opaque fallback injects only into a proven child document, once per UUID',async()=>{
 const w=world(),injected=[];
 w.browser.tabs.executeScript=async(tab,options)=>{injected.push({tab,...options});};
 w.browser.webNavigation.getAllFrames=async()=>[
  {frameId:0,documentId:w.sender().documentId,parentFrameId:-1,url:'https://page.test/watch'},
  {frameId:1,documentId:w.sender(2).documentId,parentDocumentId:w.sender().documentId,parentFrameId:0,url:'about:blank'},
  {frameId:2,documentId:w.sender(3).documentId,parentDocumentId:w.sender(99).documentId,parentFrameId:0,url:'about:blank'},
  {frameId:3,documentId:w.sender(4).documentId,parentDocumentId:w.sender().documentId,parentFrameId:0,url:'data:text/html,test'}];
 await w.start();await w.ready();await new Promise(r=>setImmediate(r));
 await w.receive({schemaVersion:1,event:'opaque_frames_changed',documentKey:1},w.sender());
 await w.advance(300);await new Promise(r=>setImmediate(r));
 assert.equal(injected.length,1);assert.equal(injected[0].frameId,1);assert.equal(injected[0].file,'content.js');
 assert.equal(injected[0].matchAboutBlank,true);
});
test('v1164: a delayed current-location lookup cannot overwrite a newer root registration',async()=>{
 const w=world();await w.start();await w.ready();let release;
 w.browser.webNavigation.getFrame=()=>new Promise(resolve=>release=resolve);
 const stale=w.receive({schemaVersion:1,event:'document_started',pageUrl:'https://page.test/next'},w.sender());
 const newer=await w.start(w.sender(9,0,'https://page.test/new'));
 release({frameId:0,documentId:w.sender().documentId,url:'https://page.test/next'});
 assert.equal(await stale,null);
 assert.equal(await w.receive({schemaVersion:1,event:'document_ready',documentKey:newer.documentKey},w.sender(9,0,'https://page.test/new')),true);
});
test('v1164: a frame-change event during an outstanding enumeration is coalesced, not lost',async()=>{
 const w=world(),injected=[];let release,calls=0;
 w.browser.tabs.executeScript=async(_,options)=>injected.push(options);
 w.browser.webNavigation.getAllFrames=()=>++calls===1?new Promise(resolve=>release=resolve):Promise.resolve([
  {frameId:1,documentId:w.sender(2).documentId,parentDocumentId:w.sender().documentId,parentFrameId:0,url:'about:blank'}]);
 await w.start();await w.ready();await w.advance(100);
 await w.receive({schemaVersion:1,event:'opaque_frames_changed',documentKey:1},w.sender());
 release([]);await new Promise(r=>setImmediate(r));await w.advance(100);await new Promise(r=>setImmediate(r));
 assert.equal(injected.length,1);assert.equal(calls,2);
});
async function spaRoot(w,ready=true){
 await w.start();await w.ready();
 w.history({tabId:7,frameId:0,documentId:w.sender().documentId,url:'https://page.test/next'});
 w.frameDetails.set('7:0',{frameId:0,documentId:w.sender().documentId,parentFrameId:-1,url:'https://page.test/next'});
 const root=await w.receive({schemaVersion:1,event:'document_started',pageUrl:'https://page.test/next'},w.sender());
 if(ready)await w.receive({schemaVersion:1,event:'document_ready',documentKey:root.documentKey},w.sender());
 return root;
}
test('v1164: loading-principal URL may remain frozen after SPA while the exact Gecko UUID owns a live request',async()=>{
 const w=world();await spaRoot(w);const r=w.request();w.before(r);w.headers(r);await new Promise(r=>setImmediate(r));
 assert.equal(w.sent.length,1);assert.equal(w.sent[0].message.pageUrl,'https://page.test/next');
});
test('v1164: a current SPA request waiting for native ACK is not rejected only for its frozen principal URL',async()=>{
 const w=world(),root=await spaRoot(w,false);const r=w.request();w.before(r);w.headers(r);
 await w.receive({schemaVersion:1,event:'document_ready',documentKey:root.documentKey},w.sender());
 await new Promise(r=>setImmediate(r));assert.equal(w.sent.length,1);
});
test('v1164: a proven opaque frame may inherit the HTTP loading principal without inheriting root ownership',async()=>{
 const w=world();await w.start();await w.ready();
 const r=w.request({frameId:1,parentFrameId:0,documentId:w.sender(2).documentId,parentDocumentId:w.sender().documentId});
 w.before(r);w.headers(r);const child=await w.frame(2,1,'about:blank');await new Promise(r=>setImmediate(r));
 assert.equal(w.sent.length,1);assert.equal(w.sent[0].message.frameKey,child.frameKey);assert.equal(w.sent[0].message.frameUrl,'about:blank');
});
test('v1164: a request started before a SPA transition retains its old epoch and cannot be promoted',async()=>{
 const w=world();await w.start();await w.ready();const old=w.request();w.before(old);
 await spaRoot(w);w.headers(old);await new Promise(r=>setImmediate(r));assert.equal(w.sent.length,0);
});
