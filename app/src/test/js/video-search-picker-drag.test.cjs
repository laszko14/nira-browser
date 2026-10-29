'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');

function harness() {
  const packets = [], events = new Map(), windowEvents = new Map(), timers = [];
  let receive;
  const port = {
    onMessage: { addListener(fn) { receive = fn; } },
    onDisconnect: { addListener() {} },
    postMessage(packet) { packets.push(packet); }
  };
  function node(tag) {
    return {
      tagName: tag.toUpperCase(), children: [], style: {}, textContent: '',
      attrs: {},
      setAttribute(name, value) { this.attrs[name] = value; },
      addEventListener(name, fn) { this['on_' + name] = fn; },
      appendChild(el) { this.children.push(el); },
      remove() { this.removed = true; },
      getBoundingClientRect() {
        return { left: this.style.left ? parseFloat(this.style.left) : 112,
          top: this.style.top ? parseFloat(this.style.top) : 8,
          width: 240, height: 64 };
      }
    };
  }
  const root = node('html');
  const document = {
    documentElement: root,
    createElement: node,
    addEventListener(name, fn) { events.set(name, fn); },
    removeEventListener(name, fn) {
      if (events.get(name) === fn) events.delete(name);
    }
  };
  const win = {
    innerWidth: 360, innerHeight: 640,
    addEventListener(name, fn) { windowEvents.set(name, fn); },
    removeEventListener(name, fn) {
      if (windowEvents.get(name) === fn) windowEvents.delete(name);
    }
  };
  win.top = win;
  vm.runInNewContext(script, {
    window: win, document,
    location: new URL('https://films.example/home'),
    browser: { runtime: { connectNative() { return port; } } },
    crypto: { getRandomValues(bytes) { bytes.fill(9); return bytes; } },
    CSS: { escape(value) { return value; } },
    URL, Uint8Array, TextEncoder, clearTimeout() {},
    setTimeout(fn) { timers.push(fn); return timers.length; }
  });
  const hello = packets[0];
  assert.equal(hello.event, 'hello');
  receive({schemaVersion: 1, event: 'bound', documentId: hello.documentId});
  let counter = 0;
  return {
    packets, events, windowEvents, root, win,
    pick(kind) {
      receive({schemaVersion: 1, event: 'pick', documentId: hello.documentId,
        requestId: (++counter).toString(16).padStart(32, '0'), pickKind: kind});
      const banner = root.children.find(el => el.children?.length >= 2);
      return { banner, title: banner.children[0], controls: banner.children[1] };
    },
    action(ui, name) {
      const button = ui.controls.children.find(el => el.textContent === name);
      assert.ok(button, 'action button ' + name);
      button.on_click({preventDefault() {}, stopPropagation() {}});
      return packets.at(-1);
    }
  };
}
function pointer(id, x, y, trusted = true) {
  return {pointerId:id, clientX:x, clientY:y, button:0, isTrusted:trusted,
    preventDefault() {}, stopPropagation() {}};
}

test('picker is a small 240px floating overlay with touch-only drag handle', () => {
  const h = harness(), ui = h.pick('submit');
  assert.match(ui.banner.style.cssText, /width:240px/);
  assert.match(ui.banner.style.cssText, /max-width:calc\(100vw - 16px\)/);
  assert.doesNotMatch(ui.banner.style.cssText, /left:8px;right:8px/);
  assert.match(ui.title.style.cssText, /touch-action:none/);
  assert.equal(ui.title.attrs.role, 'button');
  assert.ok(ui.title.attrs['aria-label'].includes('Przeciągnij'));
  assert.ok(ui.title.textContent.includes('Szukaj'));
  assert.equal(ui.controls.children.length, 2);
  assert.equal(h.action(ui, 'Anuluj').event, 'pick_cancelled');
  assert.equal(h.windowEvents.has('resize'), false);
});

test('trusted pointer drag repositions the overlay and clamps to viewport bounds', () => {
  const h = harness(), ui = h.pick('cards');
  ui.title.on_pointerdown(pointer(4, 290, 20));
  ui.title.on_pointermove(pointer(4, 190, 140));
  assert.equal(ui.banner.style.left, '12px');
  assert.equal(ui.banner.style.top, '128px');
  ui.title.on_pointermove(pointer(4, -1000, -1000));
  assert.equal(ui.banner.style.left, '8px');
  assert.equal(ui.banner.style.top, '8px');
  ui.title.on_pointermove(pointer(4, 9999, 9999));
  assert.equal(ui.banner.style.left, '112px');
  assert.equal(ui.banner.style.top, '568px');
  ui.title.on_pointerup(pointer(4, 9999, 9999));
  ui.title.on_pointermove(pointer(4, -100, -100));
  assert.equal(ui.banner.style.left, '112px');
  assert.equal(ui.banner.style.top, '568px');
  assert.equal(h.packets.filter(p=>p.event==='picked').length, 0);
  assert.equal(h.action(ui, 'Pomiń').event, 'pick_skipped');
});

test('header is the only drag target; synthetic/mismatched pointers are ignored', () => {
  const h = harness(), ui = h.pick('next');
  ui.title.on_pointerdown(pointer(1, 280, 20, false));
  ui.title.on_pointermove(pointer(1, 100, 100));
  assert.equal(ui.banner.style.left, undefined);
  ui.title.on_pointerdown(pointer(2, 280, 20));
  ui.title.on_pointermove(pointer(3, 100, 100));
  assert.equal(ui.banner.style.left, undefined);
  ui.title.on_pointercancel(pointer(2, 280, 20));
  ui.title.on_pointermove(pointer(2, 100, 100));
  assert.equal(ui.banner.style.left, undefined);
  h.action(ui, 'Anuluj');
});

test('picker location survives successive site-selection steps and reclamps on resize', () => {
  const h = harness(), first = h.pick('submit');
  first.title.on_pointerdown(pointer(1, 280, 20));
  first.title.on_pointermove(pointer(1, 190, 100));
  first.title.on_pointerup(pointer(1, 190, 100));
  const pos = [first.banner.style.left, first.banner.style.top];
  assert.equal(h.action(first, 'Pomiń').event, 'pick_skipped');
  const second = h.pick('cards');
  assert.equal(second.banner.style.left, pos[0]);
  assert.equal(second.banner.style.top, pos[1]);
  h.win.innerWidth = 260;
  h.win.innerHeight = 90;
  h.windowEvents.get('resize')();
  assert.equal(second.banner.style.left, '12px');
  assert.equal(second.banner.style.top, '18px');
  assert.equal(h.action(second, 'Anuluj').event, 'pick_cancelled');
  assert.equal(h.events.has('pointerdown'), false);
  assert.equal(h.events.has('touchstart'), false);
});
