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
