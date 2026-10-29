'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { TextEncoder } = require('node:util');

// Real WebExtension protocol, DOM and user action; no duplicate selector code.
const content = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');
const origin = 'https://films.example';
const url = origin + '/results?q=movie';
function fixture() {
  let handle;
  const sent = [], timers = [];
  let clock = 1000, cards = [], terminal = false, empty = false,
    loading = false, nextState = 'disabled', clicked = 0, hidden = false;
  const marker = {
    getBoundingClientRect() { return {width: 100, height: 20}; },
    closest(selector) { return hidden && selector.includes('[hidden]') ? {} : null; }
  };
  const next = {
    id: 'next', className: '', target: '_self', type: 'button',
    get disabled() { return nextState === 'disabled'; },
    matches(selector) { return selector === 'a[href]'; },
    closest() { return null; },
    getBoundingClientRect() { return {width: 80, height: 18}; },
    getAttribute(name) {
      if (name === 'aria-disabled') return nextState === 'aria-disabled' ? 'true' : null;
      if (name === 'href') return origin + '/results?page=2';
      return null;
    },
    hasAttribute() { return false; },
    click() { clicked++; }
  };
  const document = {
    contains() { return true; },
    querySelector(selector) {
      if (selector === 'input[type="password"]') return null;
      if (selector.includes('[data-pagination-end="true"]')) return terminal ? marker : null;
      if (selector.includes('[data-search-empty="true"]')) return empty ? marker : null;
      if (selector.includes('[aria-busy="true"]')) return loading ? marker : null;
      return null;
    },
    querySelectorAll(selector) {
      if (selector === '.next') return nextState === 'missing' ? [] : [next];
      if (selector === '.cards') return cards;
      return [];
    }
  };
  const port = {
    onMessage: { addListener(callback) { handle = callback; } },
    onDisconnect: { addListener() {} },
    postMessage(message) { sent.push(message); }
  };
  const window = {addEventListener() {}};
  window.top = window;
  vm.runInNewContext(content, {
    window, document, location: new URL(url),
    browser: {runtime: {connectNative() {return port;}}},
    crypto: {getRandomValues(bytes) {bytes.fill(4); return bytes;}},
    URL, Uint8Array, TextEncoder, Date: {now: () => clock},
    setTimeout(fn) {timers.push(fn);}
  });
  const doc = sent[0].documentId;
  handle({schemaVersion: 1, event: 'bound', documentId: doc});
  let count = 0;
  function dispatch(event, data) {
    const start = sent.length;
    handle({schemaVersion: 1, documentId: doc,
      requestId: (++count).toString(16).padStart(32, '0'),
      event, ...data});
    assert.equal(sent.length, start + 1);
    return sent[sent.length - 1];
  }
  return {
    pagination() {
      return dispatch('paginate', {pagination:'NEXT_LINK', nextPageSelector: '.next'}).status;
    },
    crawl() {
      return dispatch('extract_crawl', {
        resultsCardSelector:'.cards', startIndex:0,
        titleSelector:null, linkSelector:null, thumbnailSelector:null,
        durationSelector:null
      });
    },
    next(state) {nextState = state;},
    marker(v) {terminal = v;},
    empty(v) {empty = v;},
    hidden(v) {hidden = v;},
    loading(v) {loading = v;},
    time(v) {clock += v;},
    cards(v) {cards = v;},
    flush() {for(const fn of timers.splice(0)) fn();},
    clicks() {return clicked;}
  };
}
test('temporary disabled NEXT_LINK never claims an end', () => {
  const f = fixture();
  assert.equal(f.pagination(), 'pending');
  f.time(10000);
  assert.equal(f.pagination(), 'pending');
});

test('temporarily missing pagination button is pending, not success', () => {
  const f = fixture();
  f.next('missing');
  assert.equal(f.pagination(), 'pending');
});

test('transient disabled next eventually becomes clickable and dispatched', () => {
  const f = fixture();
  assert.equal(f.pagination(), 'pending');
  f.next('enabled');
  assert.equal(f.pagination(), 'dispatched');
  f.flush();
  assert.equal(f.clicks(), 1);
});

test('explicit terminal marker must persist across independent observations', () => {
  const f = fixture();
  f.marker(true);
  assert.equal(f.pagination(), 'pending');
  f.time(400);
  assert.equal(f.pagination(), 'pending');
  f.time(500);
  assert.equal(f.pagination(), 'end');
});

test('loading invalidates prior terminal proof', () => {
  const f = fixture();
  f.marker(true);
  assert.equal(f.pagination(), 'pending');
  f.time(800);
  f.loading(true);
  assert.equal(f.pagination(), 'pending');
  f.loading(false);
  assert.equal(f.pagination(), 'pending');
  f.time(800);
  assert.equal(f.pagination(), 'end');
});

test('marker hidden from users never confirms end', () => {
  const f = fixture();
  f.marker(true);
  f.hidden(true);
  assert.equal(f.pagination(), 'pending');
  f.time(2000);
  assert.equal(f.pagination(), 'pending');
});

test('confirmed last page can have removed next button', () => {
  const f = fixture();
  f.next('missing');
  f.marker(true);
  assert.equal(f.pagination(), 'pending');
  f.time(700);
  assert.equal(f.pagination(), 'end');
});

test('ordinary empty selector without explicit proof is not no-results', () => {
  const f = fixture();
  const x = f.crawl();
  assert.equal(x.status, 'empty');
  assert.equal(x.films.length, 0);
  assert.equal(x.inspectedCards, 0);
});

test('explicit visible empty result state is a distinct protocol result', () => {
  const f = fixture();
  f.empty(true);
  const x = f.crawl();
  assert.equal(x.status, 'no_results');
  assert.equal(x.inspectedCards, 0);
  assert.equal(x.moreOnPage, false);
  assert.equal(x.films.length, 0);
});

test('empty state during loading is not falsely completed', () => {
  const f = fixture();
  f.empty(true);
  f.loading(true);
  assert.equal(f.crawl().status, 'empty');
});

test('hidden no-results marker cannot be used as a proof', () => {
  const f = fixture();
  f.empty(true);
  f.hidden(true);
  assert.equal(f.crawl().status, 'empty');
});
