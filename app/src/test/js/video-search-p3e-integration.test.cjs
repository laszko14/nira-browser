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
    getAttribute(key) { return {type: 'submit', 'aria-label': 'Search'}[key] || null; },
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
      textContent: title, className: 'movie-link', id: '',
      getAttribute(key) { return key === 'href' ? url : null; },
      hasAttribute() { return false; },
      matches(selector) { return selector === 'a[href]' ||
        selector.includes('.movie-link'); },
      querySelector(selector) {
        if (selector === 'img') return image;
        if (selector.includes('[itemprop="name"]') || selector.includes('h2,h3,h4'))
          return heading;
        return null;
      }
    };
    const ld = {
      '@type': 'VideoObject', url, name: title,
      image: [{url: origin + '/structured?id=' + id}],
      duration: 'PT10M', datePublished: '2024-04-10T00:00:00Z'
    };
    return {
      tagName: 'ARTICLE', id: '', className: 'video-card',
      classList: ['video-card'], isConnected: true,
      parentElement: parent, ownerDocument: document,
      getAttribute() { return null; },
      closest() { return null; },
      querySelectorAll(selector) {
        if (selector === 'a[href]') return [anchor];
        if (selector.includes('script[type="application/ld+json"]'))
          return options.structured === false ? [] :
            [{textContent: JSON.stringify(ld)}];
        if (selector.includes('h1,h2,h3,h4') ||
            selector === '[itemprop="name"]') return [heading];
        if (selector === 'img' ||
            selector.includes('img,picture,video')) return [image];
        return [];
      },
      querySelector(selector) {
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
