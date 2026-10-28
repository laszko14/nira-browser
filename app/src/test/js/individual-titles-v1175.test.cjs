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

test("30, 50 and 100 card-local CSS captions resolve to distinct movies before Play", () => {
  for (const count of [30,50,100]) {
    const listing = new Element("main");
    listing.add(new Element("h1",{},"","One global listing title"));
    const videos=[];
    for(let i=1;i<=count;i++){
      const card=listing.add(new Element("div",{},"video-card"));
      card.add(new Element("span",{"data-title":"Episode "+i},"video-title","Episode "+i));
      videos.push(card.add(new Element("video")));
    }
    assert.deepEqual(videos.map(video=>videoTitle(video)),
      Array.from({length:count},(_,i)=>"Episode "+(i+1)));
    assert.equal(new Set(videos.map(video=>scopedMediaCard(video)?.card)).size,count);
  }
});

test("unique data-video-title on local card can title a direct source link", () => {
  const card=new Element("div",{"data-video-title":"Unikalny odcinek"},"media-card");
  const link=card.add(new Element("a",{href:"https://cdn.example/stream.m3u8"}));
  assert.equal(videoTitle(link),"Unikalny odcinek");
});

test("two distinct captions on one media card are ambiguous, not guessed", () => {
  const card=new Element("li");
  card.add(new Element("div",{},"video-title","Film A"));
  card.add(new Element("div",{},"card-title","Film B"));
  const video=card.add(new Element("video"));
  assert.equal(videoTitle(video),null);
});

test("two separate videos in shared outer article never inherit CSS heading", () => {
  const card=new Element("article");
  card.add(new Element("span",{},"video-title","Shared series"));
  const one=card.add(new Element("video"));
  card.add(new Element("video"));
  assert.equal(videoTitle(one),null);
});

test("local CSS caption mutation requests only its owner's card", () => {
  const root=new Element("main");
  const first=root.add(new Element("div",{},"video-card"));
  const caption=first.add(new Element("span",{},"video-title","Old title"));
  const video=first.add(new Element("video"));
  const other=root.add(new Element("div",{},"video-card"));
  other.add(new Element("span",{},"video-title","Other"));
  other.add(new Element("video"));
  caption.textContent="New individual title";
  const roots=titleMutationRoots([{type:"characterData",target:{
    nodeType:3,parentElement:caption},addedNodes:[]}]);
  assert.ok(roots.includes(first));
  assert.ok(!roots.includes(other));
  assert.equal(videoTitle(video),"New individual title");
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


test("aria-labelledby uses only a local single-player card label", () => {
  const group = new Element("main");
  const a = group.add(new Element("li"));
  const own = a.add(new Element("span", {id: "local-label"}, "", "Odcinek 7: Życie"));
  const video = a.add(new Element("video", {"aria-labelledby": "local-label"}));
  const b = group.add(new Element("li"));
  const other = b.add(new Element("span", {id: "other-label"}, "", "Nie ten film"));
  b.add(new Element("video"));
  ctx.document.getElementById = id => id === "local-label" ? own :
    id === "other-label" ? other : null;
  assert.equal(videoTitle(video), "Odcinek 7: Życie");
  video.attrs["aria-labelledby"] = "other-label";
  assert.equal(videoTitle(video), null);
  delete ctx.document.getElementById;
});

test("media link inside a different player's card never inherits player heading", () => {
  const article = new Element("article");
  article.add(new Element("h2", {}, "", "Movie A"));
  article.add(new Element("video"));
  const different = article.add(new Element("a", {href:"https://cdn.example/movie-b.mp4"}));
  assert.equal(scopedMediaCard(different), null);
});

function stage3Harness() {
  const submitted = [], diagnosticReasons = [];
  const scripts = [], links = [];
  const runtime = {
    TextEncoder, URL,
    document: {baseURI:"https://site.example/list", title:"Global page title"},
    location: {href:"https://site.example/list"},
    MAX_SCRIPT_CHARS:48000,
    note: (code) => diagnosticReasons.push(code),
    structuredGroups: new WeakMap(),
    structuredVersions: new WeakSet(),
    attribute: (el, key) => el.getAttribute(key),
    scanCollection: (selector, callback) => {
      if (selector === 'script[type="application/ld+json"]')
        scripts.forEach(callback);
      if (selector === "a[href],link[href]")
        links.forEach(callback);
    },
    submit: (url, source, mime, group, title, titleKind, duration, poster, dimensions, posterKind) =>
      submitted.push({url, source, group, title, titleKind, poster, posterKind}),
  };
  const identifiers = new WeakMap();
  let counter = 0;
  runtime.groupFor = object => {
    if (!identifiers.has(object)) identifiers.set(object, "g" + (++counter));
    return identifiers.get(object);
  };
  const absoluteStart = script.indexOf("  function absolute(");
  const absoluteEnd = script.indexOf("  function groupFor(", absoluteStart);
  const scopedStart = script.indexOf("  function safeTitle(");
  const scopedEnd = script.indexOf("  function posterFor(", scopedStart);
  const declarationStart = script.indexOf("  function scanDeclarations(");
  const declarationEnd = script.indexOf("  // Weakly tied", declarationStart);
  const structuredStart = script.indexOf("  function mediaThumbnail(");
  const structuredEnd = script.indexOf("  function scanPlayerConfigs(", structuredStart);
  assert.ok(absoluteStart > 0 && absoluteEnd > absoluteStart);
  assert.ok(declarationStart > 0 && declarationEnd > declarationStart);
  assert.ok(structuredStart > 0 && structuredEnd > structuredStart);
  vm.runInNewContext(
    script.slice(absoluteStart, absoluteEnd) +
    script.slice(scopedStart, scopedEnd) +
    script.slice(declarationStart, declarationEnd) +
    script.slice(structuredStart, structuredEnd) +
    "\n globalThis.stage3 = {scanStructuredData, scanDeclarations};", runtime);
  return {submitted, diagnosticReasons, scripts, links, runtime,
    scanStructured: () => runtime.stage3.scanStructuredData(),
    scanLinks: () => runtime.stage3.scanDeclarations()};
}

test("ten VideoObjects with contentUrls never receive the same site heading", () => {
  const h = stage3Harness();
  h.scripts.push(new Element("script", {}, "", JSON.stringify({"@graph":
    Array.from({length: 10}, (_, i) => ({
      "@type":"VideoObject", name:"Film "+(i+1),
      contentUrl:"https://cdn.example/episode"+(i+1)+".mp4"
    }))})));
  h.scanStructured();
  assert.equal(h.submitted.length, 10);
  for (let i=1;i<=10;i++) {
    const matched = h.submitted.find(v => v.url === "https://cdn.example/episode"+i+".mp4");
    assert.equal(matched.title, "Film "+i);
    assert.equal(matched.titleKind, "structured");
    assert.equal(matched.group, null);
  }
});

test("JSON-LD quality variants get one stable, object-specific group", () => {
  const h = stage3Harness();
  const a = {
    "@type":"VideoObject", name:"Film A", contentUrl:"https://cdn.example/a/master.m3u8",
    encoding:[{contentUrl:"https://cdn.example/a/480.mp4"},
              {contentUrl:"https://cdn.example/a/1080.mp4"}]
  };
  const b = {
    "@type":["Thing","VideoObject"], name:"Film B",
    encoding:[{contentUrl:"https://cdn.example/b/360.mp4"},
              {contentUrl:"https://cdn.example/b/720.mp4"}]
  };
  h.scripts.push(new Element("script", {}, "", JSON.stringify([a,b])));
  h.scanStructured();
  assert.equal(h.submitted.length, 5);
  const gA = h.submitted[0].group, gB = h.submitted[3].group;
  assert.ok(gA && gB && gA !== gB);
  assert.ok(h.submitted.slice(0,3).every(v=>v.group===gA && v.title==="Film A"));
  assert.ok(h.submitted.slice(3).every(v=>v.group===gB && v.title==="Film B"));
  h.scanStructured();
  assert.equal(h.submitted.length, 5); // unchanged scripts are not reparsed
  // A real MutationObserver clears this cache when the script changes.
  h.runtime.structuredVersions.delete(h.scripts[0]);
  h.scanStructured();
  assert.equal(h.submitted.length, 10);
  assert.ok(h.submitted.slice(5,8).every(v=>v.group===gA));
  assert.ok(h.submitted.slice(8).every(v=>v.group===gB));
});

test("unrelated structured recommendations cannot title another source", () => {
  const h = stage3Harness();
  h.scripts.push(new Element("script", {}, "", JSON.stringify({"@graph":[
    {"@type":"VideoObject","name":"Recommended","contentUrl":"https://cdn.example/recommend.mp4"},
    {"@type":"VideoObject","name":"No playable source","embedUrl":"https://embed.example/player"}
  ]})));
  h.scanStructured();
  assert.equal(h.submitted.length, 1);
  assert.equal(h.submitted[0].url, "https://cdn.example/recommend.mp4");
  assert.equal(h.submitted[0].title, "Recommended");
  assert.ok(h.diagnosticReasons.includes("title_no_direct_source"));
});

test("50 independent direct media links receive their own CSS caption, never page title", () => {
  const h=stage3Harness();
  for(let i=1;i<=50;i++){
    const card=new Element("div",{},"video-tile");
    card.add(new Element("span",{"data-video-title":"Film "+i},"video-title","Film "+i));
    const link=card.add(new Element("a",{href:"https://cdn.example/movie-"+i+".mp4"},"","Play"));
    h.links.push(link);
  }
  h.scanLinks();
  assert.equal(h.submitted.length,50);
  for(let i=1;i<=50;i++){
    const item=h.submitted.find(x=>x.url==="https://cdn.example/movie-"+i+".mp4");
    assert.equal(item.title,"Film "+i);
    assert.equal(item.titleKind,"video");
    assert.ok(item.group);
  }
  assert.equal(new Set(h.submitted.map(x=>x.group)).size,50);
});

test("JSON-LD ImageObject thumbnail binds to the correct VideoObject URL", () => {
  const h=stage3Harness();
  h.scripts.push(new Element("script",{},"",JSON.stringify({"@graph":[
    {"@type":"VideoObject",name:"Movie A",
      contentUrl:"https://cdn.example/a.mp4",
      thumbnailUrl:{"@type":"ImageObject",url:"https://cdn.example/a.jpg"}},
    {"@type":"VideoObject",name:"Movie B",
      contentUrl:"https://cdn.example/b.mp4",
      thumbnailUrl:["https://cdn.example/b.jpg","https://cdn.example/b.jpg"]}
  ]})));
  h.scanStructured();
  assert.equal(h.submitted.length,2);
  assert.equal(h.submitted.find(x=>x.title==="Movie A").poster,"https://cdn.example/a.jpg");
  assert.equal(h.submitted.find(x=>x.title==="Movie A").posterKind,"structured");
  assert.equal(h.submitted.find(x=>x.title==="Movie B").poster,"https://cdn.example/b.jpg");
});

test("conflicting JSON-LD thumbnail choices remain unknown rather than choosing another film", () => {
  const h=stage3Harness();
  h.scripts.push(new Element("script",{},"",JSON.stringify({
    "@type":"VideoObject",name:"Movie",
    contentUrl:"https://cdn.example/movie.mp4",
    thumbnailUrl:["https://cdn.example/one.jpg","https://cdn.example/two.jpg"]
  })));
  h.scanStructured();
  assert.equal(h.submitted.length,1);
  assert.equal(h.submitted[0].poster,null);
});

test("local direct media link uses its own card title and grouping", () => {
  const h = stage3Harness();
  const card = new Element("li");
  card.add(new Element("h3", {}, "", "Odcinek 2 – Wiosna"));
  h.links.push(card.add(new Element("a", {href: "https://cdn.example/odc2.mp4"}, "", "Play")));
  h.scanLinks();
  assert.equal(h.submitted.length, 1);
  assert.equal(h.submitted[0].title, "Odcinek 2 – Wiosna");
  assert.equal(h.submitted[0].titleKind, "video");
  assert.ok(h.submitted[0].group);
});

test("oversized or broken JSON-LD does not create a title", () => {
  const h = stage3Harness();
  h.scripts.push(new Element("script", {}, "", "{broken}"));
  h.scripts.push(new Element("script", {}, "", " ".repeat(48001)));
  h.scanStructured();
  assert.equal(h.submitted.length, 0);
});


test("ordinary site navigation links bypass expensive media card matching", () => {
  const h = stage3Harness();
  const card = new Element("li");
  card.add(new Element("h3", {}, "", "Film A"));
  const nonMedia = card.add(new Element("a", {href:"https://site.example/category"}, "", "Category"));
  // The guard must return before trying to read any ancestor or make groups.
  nonMedia.parentElement = {getAttribute: () => {throw Error("unexpected card walk");}};
  h.links.push(nonMedia);
  h.scanLinks();
  assert.equal(h.submitted.length, 0);
});


test("v1186 conflicting direct card metadata vs local caption fails closed", () => {
  const card = new Element("div", { "data-video-title": "Episode 7" }, "video-card");
  card.add(new Element("h3", {}, "", "Episode 8"));
  const player = card.add(new Element("video"));
  assert.equal(videoTitle(player), null);
  card.children[0].textContent = "Episode 7";
  assert.equal(videoTitle(player), "Episode 7");
});

test("v1186 aria-labelledby with two owner-local conflicting IDs is ambiguous", () => {
  const card = new Element("figure");
  const first = card.add(new Element("span", {"id": "labelA"}, "", "Film A"));
  const second = card.add(new Element("span", {"id": "labelB"}, "", "Film B"));
  const player = card.add(new Element("video", {"aria-labelledby": "labelA labelB"}));
  const old = ctx.document.getElementById;
  ctx.document.getElementById = id => id === "labelA" ? first : id === "labelB" ? second : null;
  try {
    assert.equal(videoTitle(player), null);
    second.textContent = "Film A";
    assert.equal(videoTitle(player), "Film A");
  } finally {
    ctx.document.getElementById = old;
  }
});

test("v1186 tile title cannot inherit a filename as verified movie name", () => {
  const panel = fs.readFileSync(path.join(__dirname,
    "../../main/java/com/prirai/android/nira/media/MediaFloatingUi.kt"), "utf8");
  assert.match(panel, /val title = bestTitle \?: "Film #\$\{index \+ 1\}"/);
  assert.doesNotMatch(panel, /bestTitle \?: variants\.firstNotNullOfOrNull/);
});
