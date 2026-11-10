const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { webcrypto } = require('node:crypto');
const root = path.resolve(__dirname, '../../../..');
const script = fs.readFileSync(path.join(root,
  'app/src/main/assets/extensions/zavro-preview-diagnostics/content.js'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(root,
  'app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json'), 'utf8'));
const ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

function harness(frame = false) {
  const packets = [];
  const listeners = new Map();
  let receive, disconnected;
  const node = {
    tagName: 'VIDEO', paused: true, muted: false, autoplay: false,
    controls: false, loop: false, poster: 'https://example.org/SUPER_SECRET',
    playsInline: true, preload: 'metadata', duration: 27.2,
    currentTime: 0, readyState: 2, networkState: 1, error: null,
    getBoundingClientRect: () => ({ x: 20, y: 30, width: 220, height: 120 }),
    getRootNode: () => document,
    getAttribute: () => 'SUPER_SECRET',
    closest() { return this; }
  };
  const document = {
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: (name, fn) => {
      if (listeners.get(name) === fn) listeners.delete(name);
    },
    querySelectorAll: () => [node],
    createTreeWalker: () => ({nextNode: () => null})
  };
  const window = {
    addEventListener: (name, fn) => listeners.set('window:' + name, fn),
    removeEventListener: (name, fn) => {
      if (listeners.get('window:' + name) === fn) listeners.delete('window:' + name);
    }
  };
  window.top = frame ? {} : window;
  let observeCount = 0, disconnectCount = 0;
  class MutationObserver {
    observe() { observeCount++; }
    disconnect() { disconnectCount++; }
  }
  const port = {
    postMessage(message) { packets.push(JSON.parse(JSON.stringify(message))); },
    onMessage: { addListener(fn) { receive = fn; } },
    onDisconnect: { addListener(fn) { disconnected = fn; } }
  };
  vm.runInNewContext(script, { window, document, NodeFilter:{SHOW_ELEMENT:1},
    crypto:webcrypto, MutationObserver, browser: {
      runtime: {connectNative(name) {
        assert.equal(name, 'zavro.preview.forensics'); return port;
      }}
    }, getComputedStyle: () => ({
      display:'block', visibility:'visible', pointerEvents:'auto', zIndex:'2'
    }) }, {timeout: 1000});
  return {packets, listeners, node, command: command => receive(command),
    disconnect: () => disconnected(), observerState: () => [observeCount,disconnectCount]};
}

test('manifest runs frame-aware content probe, but no page observers before user START', () => {
  assert.equal(manifest.version, '1.0.0');
  assert.equal(manifest.content_scripts[0].all_frames, true);
  assert.equal(manifest.content_scripts[0].run_at, 'document_start');
  const x = harness();
  assert.deepEqual(x.packets, []);
  assert.equal(x.listeners.size, 0);
  assert.deepEqual(x.observerState(), [0,0]);
  x.command({schemaVersion:2,action:'start',sessionId:'bad'});
  assert.deepEqual(x.packets, []);
});

test('START captures bounded metadata not page text, passwords, raw source URL or token', () => {
  const x = harness();
  x.command({schemaVersion:2,action:'start',sessionId:ID});
  assert.ok(x.packets.length >= 2);
  assert.equal(x.packets[0].event, 'observation');
  assert.equal(x.packets[0].schemaVersion, 2);
  assert.equal(x.packets[0].kind, 'MEDIA');
  assert.equal(x.packets[0].metrics.poster, true);
  assert.equal(x.packets[0].metrics.readyState, 2);
  assert.equal(x.packets[0].metrics.paused, true);
  assert.equal(x.packets[0].metrics.controls, false);
  assert.equal(Object.keys(x.packets[0].metrics).length <= 16, true);
  assert.equal(x.packets[0].metrics.frame, 'TOP');
  assert.doesNotMatch(JSON.stringify(x.packets), /SUPER_SECRET|https:\/\/example\.org/);
  assert.deepEqual(x.observerState(), [1,0]);
  x.command({schemaVersion:2,action:'snapshot',sessionId:'ffffffff-ffff-ffff-ffff-ffffffffffff'});
  assert.equal(x.observerState()[0], 1);
  const old = x.packets.length;
  x.command({schemaVersion:2,action:'snapshot',sessionId:ID});
  assert.ok(x.packets.length > old);
  x.command({schemaVersion:2,action:'stop',sessionId:ID});
  assert.equal(x.listeners.size, 0);
  assert.deepEqual(x.observerState(), [1,1]);
  const done = x.packets.length;
  x.command({schemaVersion:2,action:'snapshot',sessionId:ID});
  assert.equal(x.packets.length, done);
});

test('picker consumes exactly the authorised touch, and can be disarmed across frames', () => {
  const x = harness(true);
  x.command({schemaVersion:2,action:'start',sessionId:ID});
  x.command({schemaVersion:2,action:'pick',sessionId:ID});
  let prevented = 0;
  const touch = {type:'pointerdown',target:x.node,
    preventDefault() { prevented++; },
    stopImmediatePropagation() { prevented++; }};
  x.listeners.get('pointerdown')(touch);
  assert.equal(prevented, 2);
  assert.equal(x.packets.at(-1).kind, 'PICK');
  assert.equal(x.packets.at(-1).metrics.frame, 'SUBFRAME');
  x.listeners.get('pointerdown')(touch);
  assert.equal(prevented, 2);
  x.command({schemaVersion:2,action:'pick',sessionId:ID});
  x.command({schemaVersion:2,action:'cancel_pick',sessionId:ID});
  x.listeners.get('pointerdown')(touch);
  assert.equal(prevented, 2);
  x.disconnect();
  assert.equal(x.listeners.size, 0);
});

test('no page rewriting, media playback or background persistence in probe source', () => {
  assert.doesNotMatch(script, /\.play\s*\(|\.pause\s*\(|\.innerHTML|\.textContent/);
  assert.doesNotMatch(script, /\bfetch\s*\(|XMLHttpRequest|localStorage|indexedDB|sendNativeMessage/);
  assert.match(script, /MAX_EVENTS = 160/);
  assert.match(script, /MAX_NODES = 40/);
  assert.match(script, /stopImmediatePropagation/);
});
