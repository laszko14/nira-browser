"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

// Execute the actual pure title resolver, not a copied implementation.
const script = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/content.js"), "utf8");
const start = script.indexOf("  function safeTitle(");
const end = script.indexOf("  function posterFor(", start);
assert.ok(start > 0 && end > start);
const section = script.slice(start, end);
class Element {
  constructor(tag, attrs = {}, cls = "", text = "") {
    this.tag = tag.toLowerCase(); this.attrs = attrs; this.cls = cls;
    this.textContent = text; this.parentElement = null; this.children = [];
  }
  add(el) { el.parentElement = this; this.children.push(el); return el; }
  getAttribute(key) { return this.attrs[key] ?? null; }
  matches(selector) {
    return selector.split(",").some(raw => {
      const s = raw.trim();
      if (s.startsWith(".")) return this.cls.split(" ").includes(s.slice(1));
      if (s.startsWith("[")) return Object.hasOwn(this.attrs, s.slice(1,-1));
      if (s === "a[href]") return this.tag === "a" && !!this.attrs.href;
      return this.tag === s;
    });
  }
  contains(el) { return this === el || this.children.some(c => c.contains(el)); }
  closest(selector) { for (let e=this; e; e=e.parentElement) if (e.matches(selector)) return e; return null; }
  querySelectorAll(selector) {
    const all = [];
    const walk = x => { for (const child of x.children) { all.push(child); walk(child); } };
    walk(this);
    return all.filter(el => el.matches(selector));
  }
}
const ctx = { TextEncoder, document: { title: "Page-wide listing title" },
  attribute: (el,key) => el.getAttribute(key), URL };
vm.runInNewContext(section + "\n globalThis.titleApi = {videoTitle, scopedMediaCard, mediaTitleHint};",ctx);
const { videoTitle, scopedMediaCard, mediaTitleHint } = ctx.titleApi;

test("12 individually titled DOM video cards never inherit listing heading", () => {
  const list = new Element("main");
  const videos = [];
  for (let i=1;i<=12;i++) {
    const card=list.add(new Element("li"));
    card.add(new Element("h3", {}, "", "Episode "+i));
    videos.push(card.add(new Element("video")));
  }
  for (let i=0;i<videos.length;i++) {
    assert.equal(videoTitle(videos[i]),"Episode "+(i+1));
    assert.strictEqual(scopedMediaCard(videos[i]).card,videos[i].parentElement);
  }
  const outside=list.add(new Element("video"));
  assert.equal(videoTitle(outside),null);
});

test("single-media declaration, iframe embedding and filename links get only card-local titles", () => {
  const card=new Element("div",{},"video-card");
  card.add(new Element("h3",{},"","Special episode"));
  const src=card.add(new Element("div",{"data-hls":"https://media.example/master.m3u8"}));
  assert.equal(videoTitle(src),"Special episode");
  const wrap=new Element("figure");
  wrap.add(new Element("figcaption",{},"","Frame-local title"));
  const iframe=wrap.add(new Element("iframe"));
  assert.equal(videoTitle(iframe),"Frame-local title");
  const linkCard=new Element("li");
  linkCard.add(new Element("h4",{},"","Film with link"));
  const link=linkCard.add(new Element("a",{href:"https://cdn.example/film.mp4"}));
  assert.equal(videoTitle(link),"Film with link");
});

test("shared article containing multiple players or media links stays unknown", () => {
  const article=new Element("article");
  article.add(new Element("h2",{},"","Global heading"));
  const first=article.add(new Element("video"));
  article.add(new Element("video"));
  assert.equal(videoTitle(first),null);
  const card=new Element("li");
  card.add(new Element("h3",{},"","Mixed gallery"));
  const a=card.add(new Element("a",{href:"a.mp4"}));
  card.add(new Element("a",{href:"b.mp4"}));
  assert.equal(videoTitle(a),null);
});

test("explicit player title takes precedence, generic control labels do not", () => {
  const card=new Element("figure");
  card.add(new Element("figcaption",{},"","Real caption"));
  const player=card.add(new Element("video",{"aria-label":"Play"}));
  assert.equal(videoTitle(player),"Real caption");
  player.attrs["data-video-title"]="Actual movie";
  assert.equal(videoTitle(player),"Actual movie");
  assert.equal(mediaTitleHint("HD"),null);
  assert.equal(mediaTitleHint("Odtwórz"),null);
});

test("SPA titles are re-evaluated without cross-card bleed", () => {
  const card=new Element("li");
  const heading=card.add(new Element("h3",{},"","Old film"));
  const video=card.add(new Element("video"));
  assert.equal(videoTitle(video),"Old film");
  heading.textContent="Next film";
  assert.equal(videoTitle(video),"Next film");
});

function titleMutationRoots(records) {
  // Exercise the actual content-script mutation handler with its queue stubbed.
  const start = script.indexOf("  function titleMutationRoot(");
  const end = script.indexOf("  function onMediaEvent(", start);
  assert.ok(start > 0 && end > start);
  const roots = [];
  const mutationContext = {
    enqueueRoot: root => { if (root) roots.push(root); },
    attribute: (node, key) => node.getAttribute(key),
    document: {},
    scriptVersions: new WeakSet(), jobs: [], MAX_JOBS: 64,
    note: () => {}, setTimeout: () => 1, pump: () => {},
    alive: true, scanTimer: null, scheduled: false, overflowRoot: false,
  };
  vm.runInNewContext(script.slice(start, end) +
    "\n globalThis.mutationScan = scheduleScan;", mutationContext);
  mutationContext.mutationScan(records);
  return roots;
}

test("late heading text change queues its one-video card, not neighbouring cards", () => {
  const list = new Element("main");
  const first = list.add(new Element("li"));
  const heading = first.add(new Element("h3", {}, "", ""));
  const video = first.add(new Element("video"));
  const second = list.add(new Element("li"));
  second.add(new Element("h3", {}, "", "Other film"));
  second.add(new Element("video"));
  assert.equal(videoTitle(video), null);
  heading.textContent = "Odcinek ą 12: Finał";
  const roots = titleMutationRoots([{type: "childList", target: heading, addedNodes: []}]);
  assert.ok(roots.includes(first));
  assert.ok(!roots.includes(second));
  assert.equal(videoTitle(video), "Odcinek ą 12: Finał");
});

test("characterData and inserted caption trigger scoped rescans for same media link", () => {
  const card = new Element("figure");
  const heading = card.add(new Element("figcaption", {}, "", "Film"));
  card.add(new Element("a", {href: "https://cdn.example/watch.mp4"}));
  const textNode = {nodeType: 3, parentElement: heading};
  assert.ok(titleMutationRoots([{type: "characterData", target: textNode, addedNodes: []}]).includes(card));
  const fresh = card.add(new Element("h4", {}, "", "Late"));
  assert.ok(titleMutationRoots([{type: "childList", target: card, addedNodes: [fresh]}]).includes(card));
});

test("global headings and multi-player cards cannot claim unrelated videos", () => {
  const pageHeading = new Element("h1", {}, "", "Listing");
  assert.ok(!titleMutationRoots([{type:"characterData", target: pageHeading, addedNodes: []}])
    .some(e => e.tag === "li" || e.tag === "article"));
  const card = new Element("article");
  const heading = card.add(new Element("h2", {}, "", "Shared title"));
  card.add(new Element("video")); card.add(new Element("video"));
  assert.ok(!titleMutationRoots([{type:"childList", target: heading, addedNodes: []}]).includes(card));
});
