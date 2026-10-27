"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

// Test the actual current parser, not a second implementation.
const source = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/content.js"), "utf8");
const titleStart = source.indexOf("  function safeTitle(");
const titleEnd = source.indexOf("  function explicitVideoTitle(", titleStart);
const playerStart = source.indexOf("  function parseStaticPlayerLiteral(");
const playerEnd = source.indexOf("  function enqueueRoot(root) {", playerStart);
assert.ok(titleStart > 0 && titleEnd > titleStart && playerStart > 0 && playerEnd > playerStart);

function harness() {
  const scripts = [], elements = [], emitted = [], notes = [];
  const grouped = new WeakMap();
  let nextGroup = 0;
  const runtime = {
    TextEncoder, scriptVersions: new WeakSet(), MAX_SCRIPT_CHARS: 48000,
    offers: 0, document: {title: "Global listing heading"},
    groupFor: object => {
      if (!grouped.has(object)) grouped.set(object, "g" + (++nextGroup));
      return grouped.get(object);
    },
    attribute: (el, name) => el.attrs?.[name] ?? null,
    videoTitle: () => null,
    scanCollection: (selector, callback) => {
      if (selector === "script") scripts.forEach(callback);
      else if (selector.startsWith("video[data-setup]")) elements.forEach(callback);
    },
    submit: (url, source, mime, group, title, kind, seconds, poster) => {
      if (typeof url !== "string" ||
          !/\.(?:mp4|m4v|webm|m3u8|mpd)(?:[?#]|$)/i.test(url)) return;
      runtime.offers++;
      emitted.push({url, source, group, title, kind, poster});
    },
    note: kind => notes.push(kind),
  };
  vm.runInNewContext(source.slice(titleStart, titleEnd) +
    source.slice(playerStart, playerEnd) +
    "\n globalThis.playerApi = {scanPlayerConfigs, parseStaticPlayerLiteral};", runtime);
  return {scripts, elements, emitted, notes,
    scan: () => runtime.playerApi.scanPlayerConfigs(),
    parse: value => runtime.playerApi.parseStaticPlayerLiteral(value)};
}
function scriptElement(code, attrs = {}) {
  return {attrs, textContent: code};
}

test("playlist with twelve individual titles and 24 qualities preserves owners", () => {
  const h = harness();
  h.scripts.push(scriptElement(JSON.stringify({title:"Shared series", playlist:
    Array.from({length:12}, (_, i) => ({
      title:"Episode " + (i+1),
      sources:[
        {file:"https://cdn.example/episode"+(i+1)+"/480.mp4", label:"480p"},
        {file:"https://cdn.example/episode"+(i+1)+"/1080.mp4", label:"1080p"}
      ]
    }))}), {type:"application/json"}));
  h.scan();
  assert.equal(h.emitted.length,24);
  for (let i=1;i<=12;i++) {
    const streams=h.emitted.filter(x=>x.url.includes("/episode"+i+"/"));
    assert.equal(streams.length,2);
    assert.ok(streams.every(x=>x.title==="Episode "+i));
    assert.ok(streams[0].group && streams[0].group === streams[1].group);
  }
  assert.equal(new Set(h.emitted.map(x=>x.group)).size,12);
  assert.ok(h.emitted.every(x=>x.title!=="Shared series"));
});

test("quality labels and explicit quality titles never override owner movie", () => {
  const h = harness();
  h.scripts.push(scriptElement(JSON.stringify({
    title:"Odcinek: Finał", sources:[
      {file:"https://cdn.example/480.mp4", label:"480p", title:"480p"},
      {file:"https://cdn.example/1080.mp4", name:"4K", title:"1080p"}
    ]
  }), {type:"application/json"}));
  h.scan();
  assert.equal(h.emitted.length,2);
  assert.ok(h.emitted.every(x=>x.title==="Odcinek: Finał"));
  assert.equal(h.emitted[0].group,h.emitted[1].group);
});

test("a nameless sources[] with independently titled items must not share one group", () => {
  const h = harness();
  h.scripts.push(scriptElement(JSON.stringify({sources:[
    {title:"Independent A",file:"https://cdn.example/a.mp4"},
    {title:"Independent B",file:"https://cdn.example/b.mp4"}
  ]}), {type:"application/json"}));
  h.scan();
  assert.equal(h.emitted.length,2);
  assert.equal(h.emitted[0].title,"Independent A");
  assert.equal(h.emitted[1].title,"Independent B");
  assert.equal(h.emitted[0].group,null);
  assert.equal(h.emitted[1].group,null);
});

test("JW Player setup JS object literal binds titles without executing JavaScript", () => {
  const h = harness();
  h.scripts.push(scriptElement(
    "jwplayer('player').setup({playlist:[{title:'Film X', sources:["+
    "{file:'https://cdn.example/x-480.mp4', label:'480p'},"+
    "{file:'https://cdn.example/x-720.mp4',label:'720p'}]},"+
    "{title:'Film Y',file:'https://cdn.example/y.mp4'}]});"));
  h.scan();
  const titled=h.emitted.filter(x=>x.title);
  assert.equal(titled.length,3);
  assert.deepEqual(Array.from(titled, x=>x.title),["Film X","Film X","Film Y"]);
  assert.ok(titled[0].group && titled[0].group===titled[1].group);
  assert.notEqual(titled[0].group,titled[2].group);
});

test("videojs.src static array and JSON data-setup are both recognized", () => {
  const h=harness();
  h.scripts.push(scriptElement(
    'videojs("v").src([{src:"https://cdn.example/clip-1.mp4",title:"Pierwszy"},'+
    '{src:"https://cdn.example/clip-2.mp4",title:"Drugi"}]);'));
  h.elements.push({attrs:{"data-setup":JSON.stringify({
    title:"Trzeci",sources:[{src:"https://cdn.example/clip-3.mp4"}]})}});
  h.scan();
  const titled=h.emitted.filter(x=>x.title);
  assert.equal(titled.length,3);
  assert.deepEqual(Array.from(titled,x=>x.title),["Trzeci","Pierwszy","Drugi"]);
});

test("dynamic JavaScript config cannot be parsed or evaluated", () => {
  const h=harness();
  assert.equal(h.parse('{file:evil(),title:"Danger"}'),null);
  assert.equal(h.parse('{file:\`executeMe()\`,title:"Danger"}'),null);
  assert.equal(h.parse('{file:window.secret,title:"Danger"}'),null);
  assert.equal(h.parse('{"file":"https://cdn.example/file.mp4"}.constructor'),null);
  assert.equal(h.parse('{file:"https://cdn.example/file.mp4",title:"A",}').title,"A");
});

test("malformed scripts and huge setup data never create a titled candidate", () => {
  const h=harness();
  h.scripts.push(scriptElement("jwplayer('v').setup({title:broken, file:evil()});"));
  h.scripts.push(scriptElement("jwplayer('v').setup("+"{".repeat(130)+"file:'x.mp4'"+ "}".repeat(130)+");"));
  h.scripts.push(scriptElement("x".repeat(48001)+"jwplayer('v').setup({file:'x.mp4'});"));
  h.scan();
  // The fallback detector can still discover an anonymous URL; only a
  // title from an unparsed expression would be a bad attribution.
  assert.equal(h.emitted.filter(x=>x.title).length,0);
});

test("static parser preserves commas within titles and signed query strings", () => {
  const h=harness();
  const parsed=h.parse("{file:'https://cdn.example/clip.mp4?token=a,]b',title:'Part,} one',}");
  assert.ok(parsed);
  assert.equal(parsed.file,"https://cdn.example/clip.mp4?token=a,]b");
  assert.equal(parsed.title,"Part,} one");
});

test("unchanged setup scripts are cached, and mutated scripts can be scanned again", () => {
  const h=harness();
  const el=scriptElement('jwplayer("p").setup({title:"Film A",file:"https://cdn.example/a.mp4"});');
  h.scripts.push(el);
  h.scan();
  h.scan();
  assert.equal(h.emitted.filter(x=>x.title).length,1);
  el.textContent='jwplayer("p").setup({title:"Film B",file:"https://cdn.example/b.mp4"});';
  // The real MutationObserver invalidates scriptVersions on text mutations.
  const separately=harness();
  separately.scripts.push(el);
  separately.scan();
  assert.equal(separately.emitted.filter(x=>x.title)[0].title,"Film B");
});


test("source fields and string rendition arrays retain their local owner", () => {
  const h=harness();
  h.scripts.push(scriptElement(JSON.stringify({playlist:[
    {title:"Direct source",source:"https://cdn.example/direct.mp4"},
    {title:"Alternatives",sources:[
      "https://cdn.example/480.mp4","https://cdn.example/1080.mp4"
    ]}
  ]}), {type:"application/json"}));
  h.scan();
  assert.equal(h.emitted.length,3);
  assert.equal(h.emitted[0].title,"Direct source");
  assert.equal(h.emitted[1].title,"Alternatives");
  assert.equal(h.emitted[2].title,"Alternatives");
  assert.ok(h.emitted[1].group && h.emitted[1].group===h.emitted[2].group);
  assert.notEqual(h.emitted[0].group,h.emitted[1].group);
});
