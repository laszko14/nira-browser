'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const { TextEncoder } = require('node:util');

const extension = path.resolve(__dirname, '../../main/assets/extensions/zavro-video-search');
const recognition = fs.readFileSync(path.join(extension, 'recognition.js'), 'utf8');
const bridge = fs.readFileSync(path.join(extension, 'content.js'), 'utf8');
const origin = 'https://films.example';
const pageUrl = origin + '/search?q=Aurora+City';

/**
 * The REAL recognition.js and content.js run on the SAME VM/native port.
 * DOM, timeouts and site-provided cards are deterministic test fixtures.
 * Not a physical browser, a real internet site or real GeckoView.
 */
function harness() {
  let cards = [], clicked = 0, receive;
  const submitOverrides = Object.create(null);
  const messages = [], timers = [];
  const location = new URL(pageUrl);
  const parent = {
    className: 'search-results', classList: ['search-results'], id: 'search-results',
    getAttribute() { return null; }, parentElement: null
  };
  const document = {
    documentElement: {},
    baseURI: pageUrl, location: {origin: location.origin, href: pageUrl},
    querySelector() { return null; },
    querySelectorAll(selector) {
      if (selector === 'input' || selector === '#query') return [input];
      if (selector === '#searchSubmit') return [button];
      if (selector === 'article,div,section,main,form,iframe,*')
        return [input, button, ...cards];
      if (selector === 'article,li,figure,div,section' ||
          selector === 'article.video-card' ||
          selector === '.search-results article.video-card' ||
          selector === 'article') return cards;
      return [];
    }
  };
  const form = {
    tagName: 'FORM', id: 'searchForm', className: '', ownerDocument: document,
    getAttribute(key) {
      return {role: 'search', method: 'GET', action: '/search'}[key] || null;
    },
    querySelector() { return null; },
    querySelectorAll(selector) {
      return selector === 'button,input[type="submit"],[role="button"]' ? [button] : [];
    }
  };
  const input = {
    tagName: 'INPUT', id: 'query', className: '', classList: [], type: 'search',
    name: 'q', placeholder: 'Search', readOnly: false, disabled: false,
    isConnected: true, form, ownerDocument: document, labels: [],
    getRootNode() { return document; },
    getAttribute(key) {
      return {type: 'search', 'aria-label': 'Search films'}[key] || null;
    },
    closest() { return null; },
    getBoundingClientRect() { return {width: 180, height: 32}; },
    dispatchEvent() {}, matches(s) { return s === 'input'; }
  };
  class Input {}
  Object.defineProperty(Input.prototype, 'value', {
    get() { return this._value; }, set(value) { this._value = value; }
  });
  Object.setPrototypeOf(input, Input.prototype);
  const button = {
    tagName: 'BUTTON', id: 'searchSubmit', className: '', classList: [],
    form, ownerDocument: document, disabled: false, isConnected: true,
    getRootNode() { return document; },
    getAttribute(key) {
      return Object.prototype.hasOwnProperty.call(submitOverrides, key) ?
        submitOverrides[key] : ({type: 'submit', 'aria-label': 'Search'}[key] || null);
    },
    closest() { return null; },
    matches(q) { return q.includes('button') || q.includes('submit'); },
    getBoundingClientRect() { return {width: 48, height: 32}; },
    click() { clicked++; },
    querySelector() { return null; }
  };
  function card(id, options = {}) {
    const url = options.url || origin + '/watch?id=' + id + '&lang=pl';
    const title = options.title || 'Aurora City ' + id;
    const heading = {
      textContent: title,
      getAttribute() { return null; },
      hasAttribute() { return false; }
    };
    const image = {
      currentSrc: null,
      getAttribute(key) {
        if (key === 'data-src') return origin + '/poster?id=' + id;
        if (key === 'alt') return title;
        return null;
      }
    };
    const anchor = {
      textContent: options.linkText || title,
      className: options.anchorClass || 'movie-link', id: '',
      getAttribute(key) {
        return key === 'href' ? (options.linkState?.href || url) : null;
      },
      hasAttribute() { return false; },
      matches(selector) { return selector === 'a[href]' ||
        (selector.includes('.movie-link') && this.className === 'movie-link'); },
      querySelector(selector) {
        if (selector === 'img') return options.anchorImage === false ? null : image;
        if (selector.includes('[itemprop="name"]') || selector.includes('h2,h3,h4'))
          return options.anchorHeading === false ? null : heading;
        return null;
      }
    };
    const ld = {
      '@type': 'VideoObject', url, name: title,
      image: [{url: origin + '/structured?id=' + id}],
      duration: 'PT10M', datePublished: '2024-04-10T00:00:00Z'
    };
    return {
      tagName: 'ARTICLE', id: '', className: options.className || 'video-card',
      classList: [options.className || 'video-card'], isConnected: true,
      parentElement: options.region ? {
        id: '', className: options.region, classList: [options.region],
        parentElement: parent, getAttribute() {return null;}
      } : parent, ownerDocument: document,
      getAttribute() { return null; },
      closest() { return null; },
      querySelectorAll(selector) {
        if (selector === 'a[href]') return [anchor];
        if (selector.includes('script[type="application/ld+json"]'))
          return options.structured === false ? [] :
            [{textContent: JSON.stringify(options.structuredData === undefined ?
              ld : options.structuredData)}];
        if (selector.includes('h1,h2,h3,h4') ||
            selector === '[itemprop="name"]') return [heading];
        if (selector === 'img' ||
            selector.includes('img,picture,video')) return [image];
        return [];
      },
      querySelector(selector) {
        if (selector === 'a[href]') return anchor;
        if (selector.includes('h1,h2,h3,h4') ||
            selector === '[itemprop="name"]') return heading;
        if (selector.includes('img,picture,video')) return image;
        if (selector === 'img') return image;
        return null;
      }
    };
  }
  const window = {addEventListener() {}};
  window.top = window;
  const port = {
    onMessage: {addListener(fn) { receive = fn; }},
    onDisconnect: {addListener() {}},
    postMessage(m) { messages.push(m); }
  };
  const vmContext = {
    window, document, location, URL, TextEncoder, Uint8Array, console,
    HTMLInputElement: Input, Event: class Event {},
    CSS: {escape: x => x},
    btoa: x => Buffer.from(x, 'binary').toString('base64'),
    atob: x => Buffer.from(x, 'base64').toString('binary'),
    crypto: {getRandomValues(bytes) { bytes.fill(7); return bytes; }},
    browser: {runtime: {connectNative() { return port; }}},
    setTimeout(fn) { timers.push(fn); return timers.length; },
    clearTimeout() {}
  };
  vm.runInNewContext(recognition, vmContext);
  vm.runInNewContext(bridge, vmContext);
  assert.equal(messages[0].event, 'hello');
  const documentId = messages[0].documentId;
  receive({schemaVersion: 1, event: 'bound', documentId});
  let requestIndex = 0;
  function send(event, extra = {}) {
    const start = messages.length;
    const requestId = (++requestIndex).toString(16).padStart(32, '0');
    receive({schemaVersion: 1, event, documentId, requestId, ...extra});
    return messages.slice(start).find(m => m.requestId === requestId) || null;
  }
  return {
    send, card, document, location, input, button,
    setCards(next) { cards = next; },
    getCards() { return cards; },
    clicks() { return clicked; },
    submitOverride(key,value) {submitOverrides[key] = value;},
    flush() { for (const fn of timers.splice(0)) fn(); }
  };
}

function extract(h, selector, start) {
  return h.send('extract_crawl', {
    resultsCardSelector: selector, startIndex: start,
    linkSelector: null, titleSelector: null,
    thumbnailSelector: null, durationSelector: null
  });
}

test('P3-E real recognition/bridge: explicit search -> 18 recognized cards -> two exact-URL batches', () => {
  const h = harness();
  const before = h.send('analyze');
  assert.equal(before.event, 'analysis');
  assert.ok(['ready', 'partial'].includes(before.status));
  assert.equal(typeof before.inputSelector, 'string');
  assert.equal(typeof before.submitSelector, 'string');
  assert.equal(h.clicks(), 0);

  // Native requests the action; mere analysis or recognition never clicks.
  const ack = h.send('search', {
    query: 'Aurora City', inputSelector: before.inputSelector,
    submission: before.submission, submitSelector: before.submitSelector
  });
  assert.equal(ack.event, 'submitted');
  assert.equal(h.clicks(), 0);
  h.flush();
  assert.equal(h.clicks(), 1);

  const records = Array.from({length: 18}, (_, i) => h.card(i + 1));
  h.setCards(records);
  const found = h.send('recognize');
  assert.equal(found.event, 'recognition');
  assert.equal(found.status, 'found');
  assert.ok(found.confidence >= 85);
  assert.equal(found.matchedCards, 18);
  assert.equal(typeof found.resultsCardSelector, 'string');

  const first = extract(h, found.resultsCardSelector, 0);
  assert.equal(first.event, 'crawl_results');
  assert.equal(first.status, 'ok');
  assert.equal(first.inspectedCards, 16);
  assert.equal(first.films.length, 16);
  assert.equal(first.moreOnPage, true);
  const second = extract(h, found.resultsCardSelector, first.inspectedCards);
  assert.equal(second.status, 'ok');
  assert.equal(second.inspectedCards, 2);
  assert.equal(second.films.length, 2);
  assert.equal(second.moreOnPage, false);
  assert.deepEqual(Array.from([...first.films, ...second.films], x => x.filmPageUrl),
    Array.from({length: 18}, (_, i) => origin + '/watch?id=' + (i + 1) + '&lang=pl'));
  assert.equal(first.films[0].titleSource, 'STRUCTURED');
  assert.equal(first.films[0].imageSource, 'STRUCTURED');
  assert.equal(first.films[0].thumbnailUrl, origin + '/structured?id=1');
  assert.equal(first.films[0].durationSeconds, 600);
  assert.equal(first.films[0].publishedAtMs, Date.parse('2024-04-10T00:00:00Z'));
  assert.equal(h.clicks(), 1, 'extraction must remain read-only');
});

test('P3-E recycled SPA results cannot continue from an obsolete absolute cursor', () => {
  const h = harness();
  h.setCards(Array.from({length: 18}, (_, i) => h.card(i + 1)));
  const found = h.send('recognize');
  assert.equal(found.status, 'found');
  const old = extract(h, found.resultsCardSelector, 0);
  assert.equal(old.status, 'ok');
  assert.equal(old.inspectedCards, 16);
  // The DOM recycles cards while the native side still holds offset 16.
  h.setCards(Array.from({length: 18}, (_, i) => h.card(i + 101)));
  const stale = extract(h, found.resultsCardSelector, 16);
  assert.equal(stale.status, 'stale');
  assert.equal(stale.inspectedCards, 0);
  assert.equal(stale.films.length, 0);
  // A new window may be PROBED from zero; only native exact-film-URL
  // evidence (tested in VideoSearchV4P3EIntegrationTest) can accept it.
  const replacement = extract(h, found.resultsCardSelector, 0);
  assert.equal(replacement.status, 'ok');
  assert.equal(replacement.films[0].filmPageUrl,
    origin + '/watch?id=101&lang=pl');
  assert.equal(h.clicks(), 0);
});

test('P3-E rejected cross-origin and fake cards do not become recognized films', () => {
  const h = harness();
  const records = [h.card(1), h.card(2),
    h.card(3, {url: 'https://elsewhere.example/watch?id=3'}),
    h.card(4, {url: origin + '/category/action'})];
  const baseline = h.send('recognize');
  assert.equal(baseline.status, 'missing');
  h.setCards(records);
  const found = h.send('recognize');
  assert.equal(found.status, 'found');
  const data = extract(h, found.resultsCardSelector, 0);
  assert.equal(data.status, 'ok');
  assert.equal(data.inspectedCards, 4);
  assert.equal(data.films.length, 2);
  assert.deepEqual(Array.from(data.films, f => f.filmPageUrl), [
    origin + '/watch?id=1&lang=pl', origin + '/watch?id=2&lang=pl']);
  assert.equal(h.clicks(), 0);
});


test('P4-A2 delayed search never clicks submit control disabled after ACK',()=>{
  const h=harness();
  const form=h.send('analyze');
  assert.ok(['ready','partial'].includes(form.status));
  const ack=h.send('search',{
    query:'Aurora City',inputSelector:form.inputSelector,
    submission:form.submission,submitSelector:form.submitSelector
  });
  assert.equal(ack.event,'submitted');
  assert.equal(h.clicks(),0);
  h.button.disabled=true;
  h.flush();
  assert.equal(h.clicks(),0,'AJAX-disabled submit control must not be clicked');
});

test('P4-A2 delayed search never clicks submit button detached by SPA hydration',()=>{
  const h=harness();
  const form=h.send('analyze');
  const ack=h.send('search',{
    query:'Aurora City',inputSelector:form.inputSelector,
    submission:form.submission,submitSelector:form.submitSelector
  });
  assert.equal(ack.event,'submitted');
  h.button.isConnected=false;
  h.flush();
  assert.equal(h.clicks(),0,'detached control must not be reused');
});

test('P4-A2 recognition awaits AJAX cards rather than treating search ACK as results',()=>{
  const h=harness();
  const form=h.send('analyze');
  const before=h.send('recognize');
  assert.equal(before.status,'missing');
  const ack=h.send('search',{
    query:'Aurora City',inputSelector:form.inputSelector,
    submission:form.submission,submitSelector:form.submitSelector
  });
  assert.equal(ack.event,'submitted');
  assert.equal(h.send('recognize').status,'missing');
  assert.equal(h.clicks(),0);
  h.flush();
  h.setCards([h.card(1),h.card(2),h.card(3)]);
  const after=h.send('recognize');
  assert.equal(after.status,'found');
  assert.equal(after.matchedCards,3);
  const batch=extract(h,after.resultsCardSelector,0);
  assert.equal(batch.status,'ok');
  assert.deepEqual(Array.from(batch.films,f=>f.filmPageUrl),[
    origin+'/watch?id=1&lang=pl',origin+'/watch?id=2&lang=pl',
    origin+'/watch?id=3&lang=pl'
  ]);
  assert.equal(h.clicks(),1);
});


test('P4-B1 a POST override injected after search ACK cancels deferred click',()=>{
  const h=harness();
  const config=h.send('analyze');
  assert.equal(config.submitSelector,'#searchSubmit');
  const ack=h.send('search',{
    query:'Aurora City',inputSelector:config.inputSelector,
    submission:config.submission,submitSelector:config.submitSelector
  });
  assert.equal(ack.event,'submitted');
  h.submitOverride('formmethod','POST');
  h.flush();
  assert.equal(h.clicks(),0,'submit method must be revalidated at click time');
});

test('P4-B1 a cross-origin formaction injected after search ACK cancels click',()=>{
  const h=harness();
  const config=h.send('analyze');
  const ack=h.send('search',{
    query:'Aurora City',inputSelector:config.inputSelector,
    submission:config.submission,submitSelector:config.submitSelector
  });
  assert.equal(ack.event,'submitted');
  h.submitOverride('formaction','https://elsewhere.example/collect?q=secret');
  h.flush();
  assert.equal(h.clicks(),0,'cross-origin override must not run after ACK');
});

test('P4-B1 a new-tab target injected after search ACK cancels click',()=>{
  const h=harness();
  const config=h.send('analyze');
  const ack=h.send('search',{
    query:'Aurora City',inputSelector:config.inputSelector,
    submission:config.submission,submitSelector:config.submitSelector
  });
  assert.equal(ack.event,'submitted');
  h.submitOverride('formtarget','_blank');
  h.flush();
  assert.equal(h.clicks(),0,'search must not escape to a new tab after ACK');
});


test('P4-B2 broad saved CSS selector excludes sponsored, recommendations, profile and fragment cards',()=>{
  const h=harness();
  h.setCards([
    h.card(1),
    h.card(2,{className:'sponsored-card'}),
    h.card(3,{className:'recommended-card'}),
    h.card(4,{url:origin+'/users/editor'}),
    h.card(5,{url:pageUrl+'#episode'}),
    h.card(6,{region:'related-videos'}),
    h.card(7)
  ]);
  // A real site's broad selector may still match sponsored cards in the same
  // grid. The extractor must re-check each card and URL before persistence.
  const batch=extract(h,'.search-results article.video-card',0);
  assert.equal(batch.status,'ok');
  assert.equal(batch.inspectedCards,7);
  assert.deepEqual(Array.from(batch.films,f=>f.filmPageUrl),[
    origin+'/watch?id=1&lang=pl',origin+'/watch?id=7&lang=pl'
  ]);
  assert.deepEqual(Array.from(batch.films,f=>f.originalIndex),[0,6]);
});

test('P4-B2 card selection fails closed when all matching entries are non-results',()=>{
  const h=harness();
  h.setCards([
    h.card(1,{className:'recommended-card'}),
    h.card(2,{url:origin+'/profiles/curator'}),
    h.card(3,{url:pageUrl+'#discussion'}),
    h.card(4,{region:'trending-videos'})
  ]);
  const batch=extract(h,'.search-results article.video-card',0);
  assert.equal(batch.status,'empty');
  assert.equal(batch.films.length,0);
  assert.equal(batch.inspectedCards,4);
  assert.equal(h.clicks(),0,'read-only results extraction must never click');
});


test('P4-B2 an unproven utility URL with only a neighboring card poster is excluded',()=>{
  const h=harness();
  h.setCards([
    h.card(8,{url:origin+'/item/8',linkText:'Read more',
      anchorClass:'utility',anchorImage:false,anchorHeading:false,
      structured:false}),
    h.card(9)
  ]);
  const batch=extract(h,'.search-results article.video-card',0);
  assert.equal(batch.status,'ok');
  assert.equal(batch.inspectedCards,2);
  assert.deepEqual(Array.from(batch.films,f=>f.filmPageUrl),[
    origin+'/watch?id=9&lang=pl'
  ]);
});


test('P4-C2 virtualized cursor is stale when the same last card node changes its film URL',()=>{
  const h=harness();
  const linkState={href:origin+'/watch?id=16&lang=pl'};
  const cards=Array.from({length:18},(_,i)=>i===15 ?
    h.card(16,{linkState}) : h.card(i+1));
  h.setCards(cards);
  const first=extract(h,'.search-results article.video-card',0);
  assert.equal(first.status,'ok');
  assert.equal(first.inspectedCards,16);
  assert.equal(first.moreOnPage,true);
  linkState.href=origin+'/watch?id=816&lang=pl';
  const stale=extract(h,'.search-results article.video-card',16);
  assert.equal(stale.status,'stale');
  assert.equal(stale.inspectedCards,0);
  assert.equal(stale.films.length,0);
});

test('P4-D1 schema.org URL TVEpisode keeps exact-URL season, episode, poster and long duration',()=> {
  const h=harness();
  const url=origin+'/watch?id=51&lang=pl';
  h.setCards([h.card(51,{title:'The Aurora',structuredData:{
    '@type':'https://schema.org/TVEpisode',url,name:'The Aurora',
    episodeNumber:'3',partOfSeason:{seasonNumber:'2'},
    image:[{'@type':'ImageObject',contentUrl:origin+'/poster/51.jpg'}],
    duration:'PT120M',datePublished:'2024-02-29'
  }})]);
  const result=extract(h,'.search-results article.video-card',0);
  assert.equal(result.status,'ok');
  const film=result.films[0];
  assert.equal(film.filmPageUrl,url);
  assert.equal(film.originalTitle,'The Aurora · S02E03');
  assert.equal(film.titleSource,'STRUCTURED');
  assert.equal(film.thumbnailUrl,origin+'/poster/51.jpg');
  assert.equal(film.imageSource,'STRUCTURED');
  assert.equal(film.durationSeconds,7200);
  assert.equal(film.publishedAtMs,Date.parse('2024-02-29'));
});

test('P4-D1 distinct episodes with identical names retain separate exact URL and identity',()=> {
  const h=harness();
  h.setCards([1,2].map(episode=>h.card(episode,{
    title:'Aurora',structuredData:{
      '@type':'TVEpisode',url:origin+'/watch?id='+episode+'&lang=pl',
      name:'Aurora',episodeNumber:episode,partOfSeason:{seasonNumber:1}
    }
  })));
  const result=extract(h,'.search-results article.video-card',0);
  assert.equal(result.status,'ok');
  assert.deepEqual(Array.from(result.films,f=>f.originalTitle),
    ['Aurora · S01E01','Aurora · S01E02']);
  assert.equal(h.clicks(),0);
});

test('P4-D1 embedded structured data for a different exact film URL is never borrowed',()=> {
  const h=harness();
  h.setCards([h.card(52,{title:'Correct Local Title',structuredData:{
    '@type':'Movie',url:origin+'/watch?id=999&lang=pl',
    name:'Wrong Neighbor Film',image:origin+'/wrong-neighbor.jpg',
    duration:'PT83M',datePublished:'2023-03-10'
  }})]);
  const result=extract(h,'.search-results article.video-card',0);
  assert.equal(result.status,'ok');
  const film=result.films[0];
  assert.equal(film.originalTitle,'Correct Local Title');
  assert.equal(film.thumbnailUrl,origin+'/poster?id=52');
  assert.equal(film.durationSeconds,null);
  assert.equal(film.publishedAtMs,null);
});

test('P4-D1 invalid calendar date is not normalized into another day',()=> {
  const h=harness();
  h.setCards([h.card(53,{structuredData:{
    '@type':'Movie',url:origin+'/watch?id=53&lang=pl',
    name:'Leap Day Error',datePublished:'2024-02-31',duration:'PT3M'
  }})]);
  const film=extract(h,'.search-results article.video-card',0).films[0];
  assert.equal(film.publishedAtMs,null);
  assert.equal(film.durationSeconds,180);
});

test('P4-D1 title with existing SxxExx marker is not duplicated by exact matching JSON-LD',()=> {
  const h=harness();
  h.setCards([h.card(54,{structuredData:{
    '@type':'http://schema.org/TVEpisode',
    url:origin+'/watch?id=54&lang=pl',
    name:'Aurora S02E03',episodeNumber:3,
    partOfSeason:{seasonNumber:2}
  }})]);
  const film=extract(h,'.search-results article.video-card',0).films[0];
  assert.equal(film.originalTitle,'Aurora S02E03');
});
