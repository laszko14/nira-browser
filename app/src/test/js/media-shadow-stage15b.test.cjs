"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const base = path.join(__dirname, "../../main/assets/extensions/nira-media-detector");
const source = fs.readFileSync(path.join(base, "open-shadow-hook.js"), "utf8");
const manifest = JSON.parse(fs.readFileSync(path.join(base, "manifest.json"), "utf8"));

test("audit60 15B: main-world bridge is loaded before isolated detector in all frames", () => {
  const [main, isolated] = manifest.content_scripts;
  assert.equal(main.world, "MAIN");
  assert.equal(main.js.join(","), "open-shadow-hook.js");
  assert.equal(main.run_at, "document_start");
  assert.equal(main.all_frames, true);
  assert.equal(main.match_about_blank, true);
  assert.equal(isolated.js[0], "content.js");
  assert.equal(isolated.run_at, "document_start");
  assert.equal(isolated.all_frames, true);
});

test("audit60 15B: native attachShadow return/errors are preserved; only open roots signal", () => {
  const events = [];
  class EventTarget {
    dispatchEvent(event) { events.push({ host: this, event }); return true; }
  }
  class Element extends EventTarget {
    attachShadow(init) {
      if (!init) throw new TypeError("missing options");
      const root = { mode: init.mode, host: this };
      if (root.mode === "open") this.shadowRoot = root;
      return root;
    }
  }
  class TestEvent {
    constructor(type, options) { this.type = type; Object.assign(this, options); }
  }
  const native = Element.prototype.attachShadow;
  const ctx = { Element, EventTarget, Event: TestEvent, Reflect, Symbol, Object };
  vm.runInNewContext(source, ctx);
  const host = new Element();
  const open = host.attachShadow({ mode: "open" });
  assert.equal(open.host, host);
  assert.equal(host.shadowRoot, open);
  assert.equal(events.length, 1);
  assert.equal(events[0].event.type, "nira-media-shadow-root-open");
  assert.equal(events[0].event.bubbles, true);
  assert.equal(events[0].event.composed, true);
  const another = new Element();
  const closed = another.attachShadow({ mode: "closed" });
  assert.equal(closed.mode, "closed");
  assert.equal(another.shadowRoot, undefined);
  assert.equal(events.length, 1, "closed roots cannot be signaled");
  assert.throws(() => new Element().attachShadow(null), /missing options/);
  assert.equal(events.length, 1);
  assert.notEqual(Element.prototype.attachShadow, native);
  vm.runInNewContext(source, ctx);
  const third = new Element();
  third.attachShadow({ mode: "open" });
  assert.equal(events.length, 2, "repeat injection may not double-wrap the hook");
});
