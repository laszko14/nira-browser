"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");

const script=fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/content.js"),"utf8");
const absoluteStart=script.indexOf("  function absolute(");
const absoluteEnd=script.indexOf("  function groupFor(",absoluteStart);
const titleStart=script.indexOf("  function safeTitle(");
const titleEnd=script.indexOf("  function durationOf(video) {",titleStart);
assert.ok(absoluteStart>0 && absoluteEnd>absoluteStart && titleStart>0 && titleEnd>titleStart);

class Element {
  constructor(tag,attrs={},cls="",text=""){
    this.tag=tag.toLowerCase();
    this.tagName=this.tag.toUpperCase();
    this.attrs=attrs;this.cls=cls;this.textContent=text;
    this.parentElement=null;this.children=[];
    this.src=attrs.src||null;this.currentSrc=attrs.currentSrc||null;
    this.poster=attrs.poster||null;
  }
  add(item){item.parentElement=this;this.children.push(item);return item;}
  getAttribute(name){return this.attrs[name]??null;}
  matches(query){
    return query.split(",").some(selector=>{
      const s=selector.trim();
      const tag=/^[a-z][a-z0-9-]*/i.exec(s)?.[0]||null;
      if(tag && this.tag!==tag.toLowerCase())return false;
      if(s.includes(".")){
        const klass=s.slice(s.indexOf(".")+1);
        if(!this.cls.split(/\s+/).includes(klass))return false;
      }
      const open=s.indexOf("[");
      if(open>=0){
        const close=s.indexOf("]",open+1);
        if(close<0)return false;
        const declaration=s.slice(open+1,close);
        const split=declaration.indexOf("=");
        const name=split<0?declaration:declaration.slice(0,split);
        if(!(name in this.attrs))return false;
        if(split>=0){
          const expected=declaration.slice(split+1).replace(/^["']|["']$/g,"");
          if(String(this.attrs[name])!==expected)return false;
        }
      }
      return !!tag||s[0]==="."||s[0]==="[";
    });
  }
  contains(target){return target===this||this.children.some(x=>x.contains(target));}
  closest(selector){for(let p=this;p;p=p.parentElement)if(p.matches(selector))return p;return null;}
  querySelectorAll(selector){
    const elements=[];
    const visit=root=>{for(const item of root.children){elements.push(item);visit(item);}};
    visit(this);return elements.filter(x=>x.matches(selector));
  }
}
function harness(){
  const ctx={URL,TextEncoder,attribute:(el,name)=>el?.getAttribute?.(name)??null,
    document:{baseURI:"https://site.example/list",title:"Page headline"},
    location:{href:"https://site.example/list"}};
  vm.runInNewContext(script.slice(absoluteStart,absoluteEnd)+
    script.slice(titleStart,titleEnd)+
    "\n globalThis.posters={posterFor,posterForCard,posterMutationRoot: null,"+
    "singleCardPoster,ownedMediaCard,scopedMediaCard};",ctx);
  return ctx.posters;
}

test("untitled video gets verified single-card thumbnail, never requires videoTitle",()=>{
  const api=harness();
  const listing=new Element("main");
  const card=listing.add(new Element("div",{},"video-card"));
  const image=card.add(new Element("img",{"data-src":"https://cdn.example/episode-a.jpg"}));
  const video=card.add(new Element("video"));
  assert.equal(api.scopedMediaCard(video),null);
  assert.equal(api.posterFor(video),"https://cdn.example/episode-a.jpg");
  assert.equal(api.ownedMediaCard(video),card);
});

test("50 separate videos retain their own lazy-loaded posters",()=>{
  const api=harness();
  const listing=new Element("main");
  const outputs=[];
  for(let i=1;i<=50;i++){
    const card=listing.add(new Element("li"));
    card.add(new Element("img",{"data-lazy-src":`https://cdn.example/${i}.jpg`}));
    const video=card.add(new Element("video"));
    outputs.push(api.posterFor(video));
  }
  assert.deepEqual(outputs,Array.from({length:50},(_,i)=>`https://cdn.example/${i+1}.jpg`));
  assert.equal(new Set(outputs).size,50);
});

test("direct media link card can supply own poster without a title",()=>{
  const api=harness();
  const card=new Element("div",{},"video-tile");
  const poster=card.add(new Element("img",{"data-thumbnail":"https://cdn.example/direct.jpg"}));
  const link=card.add(new Element("a",{href:"https://cdn.example/video.mp4"}));
  assert.equal(api.scopedMediaCard(link),null);
  assert.equal(api.posterForCard(api.ownedMediaCard(link)),"https://cdn.example/direct.jpg");
});

test("explicit poster on video outranks neighboring card image",()=>{
  const api=harness();
  const card=new Element("figure");
  card.add(new Element("img",{src:"https://cdn.example/fallback.jpg"}));
  const video=card.add(new Element("video",{poster:"https://cdn.example/authoritative.jpg"}));
  assert.equal(api.posterFor(video),"https://cdn.example/authoritative.jpg");
});

test("multiple gallery images do not create an arbitrary poster",()=>{
  const api=harness();
  const card=new Element("article");
  card.add(new Element("img",{src:"https://cdn.example/a.jpg"}));
  card.add(new Element("img",{src:"https://cdn.example/b.jpg"}));
  const video=card.add(new Element("video"));
  assert.equal(api.posterFor(video),null);
});

test("explicit unique poster survives unrelated gallery pictures",()=>{
  const api=harness();
  const card=new Element("article");
  card.add(new Element("img",{src:"https://cdn.example/avatar.jpg"}));
  card.add(new Element("img",{"data-thumbnail":"https://cdn.example/right.jpg"}));
  const video=card.add(new Element("video"));
  assert.equal(api.posterFor(video),"https://cdn.example/right.jpg");
});

test("conflicting explicit miniatures fail closed",()=>{
  const api=harness();
  const card=new Element("article");
  card.add(new Element("img",{"data-poster":"https://cdn.example/a.jpg"}));
  card.add(new Element("img",{"data-thumbnail":"https://cdn.example/b.jpg"}));
  const video=card.add(new Element("video"));
  assert.equal(api.posterFor(video),null);
});

test("nested recommendation's image cannot be used as outer player poster",()=>{
  const api=harness();
  const card=new Element("article");
  const recommendations=card.add(new Element("li"));
  recommendations.add(new Element("img",{"data-thumbnail":"https://cdn.example/other.jpg"}));
  const video=card.add(new Element("video"));
  assert.equal(api.posterFor(video),null);
});

test("same explicit URL appears twice without introducing a conflict",()=>{
  const api=harness();
  const card=new Element("article");
  card.add(new Element("img",{"data-poster":"https://cdn.example/same.jpg"}));
  card.add(new Element("img",{"data-poster":"https://cdn.example/same.jpg"}));
  const video=card.add(new Element("video"));
  assert.equal(api.posterFor(video),"https://cdn.example/same.jpg");
});

test("no poster leaks across a shared multi-video gallery",()=>{
  const api=harness();
  const card=new Element("article");
  card.add(new Element("img",{"data-thumbnail":"https://cdn.example/gallery.jpg"}));
  const a=card.add(new Element("video"));
  const b=card.add(new Element("video"));
  assert.equal(api.posterFor(a),null);
  assert.equal(api.posterFor(b),null);
});

test("lazy srcset uses bounded valid HTTPS poster, never data URI",()=>{
  const api=harness();
  const card=new Element("li");
  card.add(new Element("img",{"data-srcset":"data:image/png;base64,xxxx, https://cdn.example/lazy.jpg 2x"}));
  const video=card.add(new Element("video"));
  assert.equal(api.posterFor(video),"https://cdn.example/lazy.jpg");
});

test("invalid private file, data and credentialed poster URLs fail validation",()=>{
  const api=harness();
  for(const uri of ["file:///tmp/b.jpg","data:image/png;base64,aaaa",
      "https://person:secret@cdn.example/image.jpg"]){
    const card=new Element("figure");
    card.add(new Element("img",{"data-thumbnail":uri}));
    const video=card.add(new Element("video"));
    assert.equal(api.posterFor(video),null);
  }
});

test("a poster attribute mutation can queue the owning card without scanning neighbors",()=>{
  const api=harness();
  const start=script.indexOf("  function titleMutationRoot(");
  const end=script.indexOf("  function onMediaEvent(",start);
  const roots=[];
  const ctx={
    titleMutationRoot:()=>null,ownedMediaCard:api.ownedMediaCard,
    enqueueRoot:root=>roots.push(root),attribute:(el,name)=>el.getAttribute(name),
    document:{},scriptVersions:new WeakSet(),structuredVersions:new WeakSet(),
    jobs:[],MAX_JOBS:64,note:()=>{},setTimeout:()=>1,pump:()=>{},
    alive:true,scanTimer:null,scheduled:false,overflowRoot:false
  };
  vm.runInNewContext(script.slice(start,end)+"\n globalThis.scan=scheduleScan;",ctx);
  const listing=new Element("main");
  const a=listing.add(new Element("article"));
  const img=a.add(new Element("img",{"data-src":"https://cdn.example/new.jpg"}));
  a.add(new Element("video"));
  const b=listing.add(new Element("article"));
  b.add(new Element("img",{"data-src":"https://cdn.example/other.jpg"}));
  b.add(new Element("video"));
  ctx.scan([{type:"attributes",target:img,addedNodes:[]}]);
  assert.ok(roots.includes(a));
  assert.ok(!roots.includes(b));
});
