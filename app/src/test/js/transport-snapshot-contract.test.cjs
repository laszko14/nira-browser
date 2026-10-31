'use strict';
const { test } = require('node:test'), assert = require('node:assert/strict');
const { observeRoutedMessages } = require('./helpers/transport-snapshot.cjs');
test('captured values are frozen at send time, including nested JSON fields', () => {
  const routed = [], cap = observeRoutedMessages(routed);
  const m = { verification: 'manifest_verified', proof: { duration: 6, ids: [1] } };
  routed.push([7,m]);
  m.verification = 'stream_verified';
  m.proof.duration = 9;
  m.proof.ids.push(2);
  routed.push([7,m]);
  cap.restore();
  assert.deepEqual(cap.messages, [
    { verification: 'manifest_verified', proof: { duration: 6, ids: [1] } },
    { verification: 'stream_verified', proof: { duration: 9, ids: [1,2] } }
  ]);
  assert.equal(routed[0][1].verification, 'stream_verified');
});
test('negative control: reference and spread lose historical nested evidence', () => {
  const routed=[], cap=observeRoutedMessages(routed);
  const m={verification:'manifest_verified', proof:{duration:6}};
  const shallow={...m}; routed.push([1,m]);
  m.proof.duration=undefined; m.verification='stream_verified'; cap.restore();
  assert.equal(shallow.proof.duration,undefined);
  assert.equal(cap.messages[0].proof.duration,6);
  assert.equal(cap.messages[0].verification,'manifest_verified');
  assert.throws(()=>assert.equal({ ...cap.messages[0],proof:{} }.proof.duration,6),assert.AssertionError);
});
test('malformed record cannot be claimed as delivered',()=>{
  const r=[], cap=observeRoutedMessages(r);
  assert.throws(()=>r.push(null),/routed/);
  assert.equal(cap.messages.length,0);
  assert.equal(r.length,0);
  cap.restore();
});
