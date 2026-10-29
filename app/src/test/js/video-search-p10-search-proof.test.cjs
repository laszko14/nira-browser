'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { TextEncoder } = require('node:util');

const content = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');
const base = 'https://films.example';
const pageUrl = base + '/search';

function harness(initialFilm = 'old') {
  let receiver;
  let currentFilm = initialFilm;
  let submissions = 0;
  const messages = [];
  const timers = [];
  const port = {
    onMessage: { addListener(fn) { receiver = fn; } },
    onDisconnect: { addListener() {} },
    postMessage(message) { messages.push(message); }
  };
  const form = {
    tagName: 'FORM', id: 'lookup', className: '',
    getAttribute(name) { return name === 'method' ? 'GET' : ''; },
    querySelector() { return null; },
    requestSubmit() { submissions++; } // blocked by a page's preventDefault
  };
  class Input {}
  Object.defineProperty(Input.prototype, 'value', {
    set(v) { this.current = v; },
    get() { return this.current; }
  });
  const input = new Input();
  Object.assign(input, {
    id: 'search', tagName: 'INPUT', type: 'search',
    name: 'q', placeholder: 'Search', form,
    disabled: false, readOnly: false,
    getBoundingClientRect() { return { width: 150, height: 30 }; },
    getAttribute(name) { return name === 'type' ? 'search' : null; },
    closest() { return null; },
    dispatchEvent() { return true; }
  });
  const document = {
    contains() { return true; },
    querySelector(name) { return name === '#search' ? input : null; },
    querySelectorAll(name) {
      if (name === '#search') return [input];
      if (name === '.cards') {
        const anchor = {
          textContent: 'Film ' + currentFilm, id: '', className: '',
          matches() { return false; },
          querySelector() { return null; },
          getAttribute(name) {
            return name === 'href' ? base + '/watch?id=' + currentFilm : null;
          }
        };
        return [{
          querySelectorAll(selector) {
            return selector === 'a[href]' ? [anchor] : [];
          }
        }];
      }
      return [];
    }
  };
  const window = { addEventListener() {} };
  window.top = window;
  vm.runInNewContext(content, {
    window, document, location: new URL(pageUrl),
    browser: { runtime: { connectNative() { return port; } } },
    crypto: { getRandomValues(bytes) { bytes.fill(3); return bytes; } },
    HTMLInputElement: Input, Event: class Event {},
    CSS: { escape(value) { return value; } },
    URL, Uint8Array, TextEncoder, setTimeout(fn) { timers.push(fn); }
  });
  assert.equal(messages[0].event, 'hello');
  const documentId = messages[0].documentId;
  receiver({ schemaVersion: 1, event: 'bound', documentId });
  let seq = 0;
  function send(event, extra) {
    const before = messages.length;
    receiver({
      schemaVersion: 1, documentId, event,
      requestId: (++seq).toString(16).padStart(32, '0'), ...extra
    });
    return messages.slice(before);
  }
  function extract() {
    const response = send('extract_crawl', {
      resultsCardSelector: '.cards', startIndex: 0,
      linkSelector: null, titleSelector: null,
      thumbnailSelector: null, durationSelector: null
    });
    assert.equal(response.length, 1);
    assert.equal(response[0].event, 'crawl_results');
    return response[0];
  }
  function submit() {
    const response = send('search', {
      inputSelector: '#search', submission: 'FORM_SUBMIT',
      submitSelector: null, query: 'new search'
    });
    assert.equal(response.length, 1);
    assert.equal(response[0].event, 'submitted');
  }
  return {
    extract, submit,
    flush() { for (const fn of timers.splice(0)) fn(); },
    changeFilm(value) { currentFilm = value; },
    get submissions() { return submissions; },
    get documentId() { return documentId; }
  };
}

test('real content script ACKs preventDefault/no-op while old film remains', () => {
  const h = harness();
  const before = h.extract();
  h.submit();
  assert.equal(h.submissions, 0, 'ACK precedes form action');
  h.flush();
  assert.equal(h.submissions, 1);
  const after = h.extract();
  assert.equal(after.status, 'ok');
  assert.equal(after.films[0].filmPageUrl, before.films[0].filmPageUrl);
  assert.equal(after.films[0].originalTitle, before.films[0].originalTitle);
  // Kotlin SearchEvidence gate MUST reject this same-document fingerprint.
});

test('real same-document SPA can replace film URL after a dispatched search', () => {
  const h = harness();
  const before = h.extract();
  h.submit();
  h.flush();
  h.changeFilm('new');
  const after = h.extract();
  assert.equal(after.films.length, 1);
  assert.notEqual(after.films[0].filmPageUrl, before.films[0].filmPageUrl);
  assert.equal(after.films[0].filmPageUrl, base + '/watch?id=new');
  // Kotlin gate allows this only after a valid same-selector pre-search crawl.
});

test('changing query input alone does not change the film identity', () => {
  const h = harness();
  const before = h.extract();
  h.submit();
  const after = h.extract();
  assert.deepEqual(after.films.map(x => x.filmPageUrl),
    before.films.map(x => x.filmPageUrl));
});
