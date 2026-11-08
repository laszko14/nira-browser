'use strict';

/*
 * P4-B2: production structural recognizer, sanitized card-local HTML shapes.
 * These tests neither fetch websites nor claim physical GeckoView acceptance.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/recognition.js'), 'utf8');
const pageUrl = 'https://films.example/search?q=Aurora';

function recognize(specs) {
  const parent = {
    id:'results', className:'search-results', classList:['search-results'],
    getAttribute() {return null;}, parentElement:null
  };
  const cards = specs.map((item, index) => {
    const title = item.title || 'Aurora Film '+(index+1);
    const heading = {textContent:title,getAttribute(){return null;}};
    const className = item.className || 'video-card';
    const links = (item.links || [{href:'/watch/'+(index+1),label:title,innerTitle:true}])
      .map((link) => ({
        textContent:link.label ?? 'Read more',
        getAttribute(key) {return key==='href'?link.href:null;},
        hasAttribute() {return false;},
        querySelector() {return link.innerTitle ? heading : null;}
      }));
    return {
      tagName:'ARTICLE', id:'', className, classList:[className],
      parentElement:parent, getAttribute(){return null;}, closest(){return null;},
      querySelectorAll(key) {return key==='a[href]'?links:[];},
      querySelector(key) {
        if(key.includes('h1,h2,h3,h4'))return heading;
        if(key.includes('img,picture,video'))return {tagName:'IMG'};
        if(key.includes('time[datetime]'))return item.duration===true?{tagName:'TIME'}:null;
        return null;
      }
    };
  });
  const document = {
    baseURI:pageUrl,
    querySelectorAll(selector) {
      if(selector==='article,li,figure,div,section' ||
         selector==='article,div,section,main,form,iframe,*')return cards;
      if(selector==='article')return cards;
      const m=selector.match(/(?:^|\s)article\.([A-Za-z][\w-]*)$/);
      if(m)return cards.filter(c=>c.classList.includes(m[1]) &&
        (!selector.includes(' ') || selector.startsWith('.search-results ')));
      return [];
    }
  };
  const window = {};window.top=window;
  vm.runInNewContext(source,{window,document,URL,location:new URL(pageUrl),console});
  return window.__zavroRecognition.scan(document);
}

test('P4-B2 rejects author and profile links even with matching title and poster',()=>{
  for(const section of ['users','profiles','people','actors','cast','comments','reviews','forum']) {
    const result=recognize([
      {links:[{href:'/'+section+'/alice',label:'Aurora Film 1',innerTitle:true}]},
      {links:[{href:'/'+section+'/bob',label:'Aurora Film 2',innerTitle:true}]}
    ]);
    assert.equal(result.selector,null,section+' must not be treated as a film URL');
    assert.equal(result.reason,'CARDS_NOT_FOUND');
  }
});

test('P4-B2 a same-page fragment cannot be mistaken for a film detail page',()=>{
  const result=recognize([
    {links:[{href:'/search?q=Aurora#film1',label:'Aurora Film 1',innerTitle:true}]},
    {links:[{href:'/search?q=Aurora#film2',label:'Aurora Film 2',innerTitle:true}]}
  ]);
  assert.equal(result.selector,null);
  assert.equal(result.reason,'CARDS_NOT_FOUND');
});

test('P4-B2 unrelated generic action links do not inherit poster/heading evidence',()=>{
  const result=recognize([
    {duration:true,links:[{href:'/item/a',label:'Read more',innerTitle:false}]},
    {duration:true,links:[{href:'/item/b',label:'Read more',innerTitle:false}]}
  ]);
  assert.equal(result.selector,null,'poster on card does not prove anchor destination');
  assert.equal(result.reason,'CARDS_NOT_FOUND');
});

test('P4-B2 recommended card class is excluded even inside a search-results grid',()=>{
  const result=recognize([
    {className:'recommended-card'},
    {className:'recommended-card'}
  ]);
  assert.equal(result.selector,null);
  assert.equal(result.reason,'CARDS_NOT_FOUND');
});

test('P4-B2 keeps real detail links with nonstandard paths when title matches',()=>{
  const result=recognize([
    {links:[{href:'/details?id=1',label:'Aurora Film 1',innerTitle:false}]},
    {links:[{href:'/details?id=2',label:'Aurora Film 2',innerTitle:false}]}
  ]);
  assert.equal(result.reason,'CANDIDATE_FOUND');
  assert.equal(result.selector,'.search-results article.video-card');
  assert.equal(result.matchedCards,2);
});

test('P4-B2 a film anchor wins over an unrelated account link within each card',()=>{
  const result=recognize([
    {links:[
      {href:'/profiles/editor',label:'Aurora Film 1',innerTitle:true},
      {href:'/watch/1',label:'Aurora Film 1',innerTitle:true}
    ]},
    {links:[
      {href:'/users/mod',label:'Aurora Film 2',innerTitle:true},
      {href:'/watch/2',label:'Aurora Film 2',innerTitle:true}
    ]}
  ]);
  assert.equal(result.reason,'CANDIDATE_FOUND');
  assert.equal(result.matchedCards,2);
});

test('P4-B2 two distinct equally strong film anchors remain ambiguous',()=>{
  const result=recognize([
    {links:[
      {href:'/watch/1',label:'Aurora Film 1',innerTitle:true},
      {href:'/film/1',label:'Aurora Film 1',innerTitle:true}
    ]},
    {links:[
      {href:'/watch/2',label:'Aurora Film 2',innerTitle:true},
      {href:'/film/2',label:'Aurora Film 2',innerTitle:true}
    ]}
  ]);
  assert.equal(result.matchedCards,0,'ambiguous links must not be counted as verified cards');
  assert.equal(result.selector,null);
  assert.equal(result.reason,'CARDS_NOT_FOUND');
});
