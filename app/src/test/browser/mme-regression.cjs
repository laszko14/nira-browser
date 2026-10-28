"use strict";
// Optional real-DOM verification using an existing Playwright installation.
// No package or browser is downloaded by this runner. The standard Node suite
// independently covers protocol, manifest delivery and pure title decisions.
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { chromium } = require("playwright");
const assets = path.join(__dirname, "../../main/assets/extensions/nira-media-detector");
const fixture = fs.readFileSync(path.join(__dirname, "../fixtures/mme-regression.html"), "utf8");
const content = fs.readFileSync(path.join(assets, "content.js"), "utf8");
let browser;
before(async () => { browser = await chromium.launch({executablePath:process.env.MME_CHROMIUM || undefined,
  headless:true, args:["--no-sandbox"]}); });
after(async () => { await browser?.close(); });

async function harness(t) {
  const context = await browser.newContext();
  t.after(() => context.close());
  await context.route("**/*", route => route.request().resourceType() === "document" ?
    route.fulfill({contentType:"text/html",body:route.request().url().startsWith("https://frame.invalid/") ?
      '<figure><h3>Film ramki</h3><video preload="none" src="https://media.invalid/frame.mp4"></video></figure>' : fixture}) : route.abort());
  const native = [], stats = [], frames = new Map();
  let receive, page;
  const bgBrowser = {
    runtime:{id:"detector", onMessage:{addListener:fn=>{receive=fn;}}, sendNativeMessage:async()=>true},
    tabs:{sendMessage:async(_id,msg)=>page?.evaluate(msg=>window.__mmeReceive?.(msg,{id:"detector"}),msg)},
    webRequest:{onBeforeRequest:{addListener(){}},onHeadersReceived:{addListener(){}}},
  };
  const bg = {browser:bgBrowser, URL, TextEncoder, TextDecoder, Date, setTimeout, clearTimeout, AbortController};
  vm.runInNewContext(fs.readFileSync(path.join(assets,"manifest-parser.js"),"utf8"),bg);
  vm.runInNewContext(fs.readFileSync(path.join(assets,"background.js"),"utf8"),bg);
  await context.exposeBinding("mmeMessage", ({frame},msg) => {
    if (!frames.has(frame)) frames.set(frame, frames.size);
    return receive(msg,{id:"detector",tab:{id:7},frameId:frames.get(frame),url:frame.url()});
  });
  await context.exposeBinding("mmeNative", (_source,msg) => {
    if (msg.event === "media_candidate") native.push(msg);
    if (msg.event === "discovery_stats") stats.push(msg);
    return true;
  });
  const installBridge = () => {
    window.browser={runtime:{id:"detector",onMessage:{addListener:fn=>{window.__mmeReceive=fn;}},
      sendMessage:msg=>window.mmeMessage(msg),sendNativeMessage:(_host,msg)=>window.mmeNative(msg)}};
  };
  // One init script guarantees the bridge exists before the detector executes.
  await context.addInitScript({content:`(${installBridge.toString()})();\n${content}`});
  page = await context.newPage();
  await page.goto("https://fixture.invalid/watch");
  return {page,native,stats};
}
async function waitUntil(page, predicate, timeout=35000) {
  // Only local test synchronization; no GitHub Actions polling or site requests.
  const start=Date.now();
  while (!predicate()) {
    assert.ok(Date.now()-start<timeout,"synthetic detection timed out");
    await page.waitForTimeout(30);
  }
}
async function addCards(page,count) {
  await page.evaluate(count=>{
    for(let i=1;i<=count;i++) {
      const card=document.querySelector("#movie-card").content.cloneNode(true);
      card.querySelector("h3").textContent="Odcinek "+i;
      card.querySelector("video").src="https://media.invalid/episode-"+i+".mp4";
      document.querySelector("#movies").append(card);
    }
  },count);
}
for (const count of [10,100,200]) test(`MME real DOM: ${count} separate titles before Play with bounded IPC`,async t=>{
  const h=await harness(t);
  await addCards(h.page,count);
  await waitUntil(h.page,()=>new Set(h.native.map(m=>m.url)).size===count);
  assert.equal(new Set(h.native.map(m=>m.groupId)).size,count);
  for(let i=1;i<=count;i++) {
    const messages=h.native.filter(m=>m.url===`https://media.invalid/episode-${i}.mp4`);
    assert.ok(messages.length>=1);
    assert.ok(messages.every(m=>m.title===`Odcinek ${i}`));
    assert.ok(messages.every(m=>m.source==="page"));
  }
  assert.equal(h.native.length,count,"rescans must not create duplicate observations");
  assert.equal(h.stats.some(s=>s.code==="queue_native_capacity"),false);
});

test("MME real DOM: late title, source change, Shadow DOM and SPA retain document ownership",async t=>{
  const h=await harness(t);
  await h.page.evaluate(()=>{
    const card=document.querySelector("#movie-card").content.cloneNode(true);
    card.querySelector("figcaption").remove();
    card.querySelector("video").src="https://media.invalid/first.mp4";
    document.querySelector("#movies").append(card);
    const host=document.createElement("div"); document.body.append(host);
    host.attachShadow({mode:"open"}).innerHTML='<figure><h3>Shadow film</h3><video preload="none" src="https://media.invalid/shadow.mp4"></video></figure>';
  });
  await waitUntil(h.page,()=>h.native.length>=2);
  const first=h.native.find(m=>m.url.endsWith("/first.mp4"));
  assert.ok(first);
  assert.equal(h.native.find(m=>m.url.endsWith("/shadow.mp4"))?.title,"Shadow film");
  await h.page.evaluate(()=>{document.querySelector("#movies h3").textContent="Późny tytuł";});
  await waitUntil(h.page,()=>h.native.some(m=>m.url.endsWith("/first.mp4")&&m.title==="Późny tytuł"));
  const updated=h.native.find(m=>m.title==="Późny tytuł");
  assert.equal(updated.groupId,first.groupId);
  await h.page.evaluate(()=>{
    document.querySelector("#movies video").src="https://media.invalid/second.mp4";
    document.querySelector("#movies h3").textContent="Drugi film";
  });
  await waitUntil(h.page,()=>h.native.some(m=>m.url.endsWith("/second.mp4")));
  const second=h.native.find(m=>m.url.endsWith("/second.mp4"));
  assert.equal(second.title,"Drugi film");
  assert.notEqual(second.groupId,first.groupId);
  await h.page.evaluate(()=>{
    history.pushState({},"","/next");
    document.querySelector("#movies video").src="https://media.invalid/third.mp4";
    document.querySelector("#movies h3").textContent="Trzeci film";
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  await waitUntil(h.page,()=>h.native.some(m=>m.url.endsWith("/third.mp4")));
  const third=h.native.find(m=>m.url.endsWith("/third.mp4"));
  assert.notEqual(third.documentKey,first.documentKey);
  assert.equal(third.title,"Trzeci film");
  assert.ok(!JSON.stringify(h.stats).includes("Późny"));
});

test("MME real DOM: iframe-local title and supported blob source declarations before Play",async t=>{
  const h=await harness(t);
  await h.page.evaluate(()=>{
    const frame=document.createElement("iframe");
    frame.src="https://frame.invalid/clip";
    document.body.append(frame);
    const card=document.createElement("figure");
    card.innerHTML='<h3>Film MSE</h3><video preload="none"><source src="https://media.invalid/blob-source.m3u8" type="application/vnd.apple.mpegurl"></video>';
    card.querySelector("video").src=URL.createObjectURL(new MediaSource());
    document.body.append(card);
  });
  await waitUntil(h.page,()=>h.native.some(m=>m.url.endsWith("/frame.mp4"))&&
    h.native.some(m=>m.url.endsWith("/blob-source.m3u8")));
  const framed=h.native.find(m=>m.url.endsWith("/frame.mp4"));
  assert.equal(framed.title,"Film ramki");
  assert.ok(framed.frameKey>0);
  assert.equal(h.native.find(m=>m.url.endsWith("/blob-source.m3u8"))?.title,"Film MSE");
  assert.ok(h.native.every(m=>!m.url.startsWith("blob:")));
});

test("MME real DOM: one player with multiple HLS sources may use its page title",async t=>{
  const h=await harness(t);
  await h.page.evaluate(()=>{
    document.querySelector("#movies").innerHTML='<video preload="none"><source src="https://media.invalid/720.m3u8" type="application/vnd.apple.mpegurl"><source src="https://media.invalid/1080.m3u8" type="application/vnd.apple.mpegurl"></video>';
  });
  await waitUntil(h.page,()=>h.native.length>=2);
  assert.ok(h.native.every(m=>m.title==="Jedyny film z wariantami"&&m.titleKind==="page"));
  assert.equal(new Set(h.native.map(m=>m.groupId)).size,1);
});

test("MME real DOM: page fallback cannot mask conflicting owner titles or name several movies",async t=>{
  const h=await harness(t);
  await h.page.evaluate(()=>{
    document.querySelector("#movies").innerHTML='<figure><h3>Film A</h3><h3>Film B</h3><video preload="none" src="https://media.invalid/conflict.mp4"></video></figure>';
  });
  await waitUntil(h.page,()=>h.native.length>=1);
  assert.equal(h.native[0].title,undefined);
  await h.page.evaluate(()=>{
    document.querySelector("#movies").innerHTML='<video preload="none" src="https://media.invalid/unknown-a.mp4"></video><video preload="none" src="https://media.invalid/unknown-b.mp4"></video>';
  });
  await waitUntil(h.page,()=>h.native.length>=3);
  assert.ok(h.native.slice(1).every(m=>m.title===undefined));
});

test("MME real DOM: late aria-labelledby text updates only its local player",async t=>{
  const h=await harness(t);
  await h.page.evaluate(()=>{
    document.querySelector("#movies").innerHTML='<figure><span id="own-label"></span><video aria-labelledby="own-label" preload="none" src="https://media.invalid/label.mp4"></video></figure>';
  });
  await waitUntil(h.page,()=>h.native.length>=1);
  await h.page.evaluate(()=>{document.querySelector("#own-label").textContent="Późna etykieta";});
  await waitUntil(h.page,()=>h.native.some(m=>m.title==="Późna etykieta"),3000);
  assert.equal(new Set(h.native.map(m=>m.groupId)).size,1);
});

test("MME real DOM: reused declarative player and direct link cannot merge successive movies",async t=>{
  const h=await harness(t);
  await h.page.evaluate(()=>{
    document.querySelector("#movies").innerHTML='<div class="video-card"><h3>Pierwsza deklaracja</h3><div data-hls="https://media.invalid/declared-first.m3u8"></div></div><figure><h3>Pierwszy link</h3><a href="https://media.invalid/link-first.mp4">Pobierz</a></figure>';
  });
  await waitUntil(h.page,()=>h.native.length>=2);
  const first=h.native.find(m=>m.url.endsWith("/declared-first.m3u8"));
  const link=h.native.find(m=>m.url.endsWith("/link-first.mp4"));
  assert.equal(link.title,"Pierwszy link");
  assert.equal(link.titleKind,"video");
  await h.page.evaluate(()=>{
    document.querySelector("[data-hls]").setAttribute("data-hls","https://media.invalid/declared-second.m3u8");
    document.querySelector(".video-card h3").textContent="Druga deklaracja";
    document.querySelector("a").href="https://media.invalid/link-second.mp4";
    document.querySelector("figure h3").textContent="Drugi link";
  });
  await waitUntil(h.page,()=>h.native.length>=4);
  assert.notEqual(h.native.find(m=>m.url.endsWith("/declared-second.m3u8")).groupId,first.groupId);
  assert.notEqual(h.native.find(m=>m.url.endsWith("/link-second.mp4")).groupId,link.groupId);
});
