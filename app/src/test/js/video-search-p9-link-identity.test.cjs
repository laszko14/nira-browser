'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { TextEncoder } = require('node:util');

// Run the real Gecko WebExtension content script, not a copy of its selector.
const script = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');
const origin = 'https://films.example';
const documentUrl = origin + '/results?q=drama';

function titleNode(value) {
  return {
    textContent: value,
    getAttribute() { return null; },
    hasAttribute() { return false; }
  };
}
function anchor(href, label = '', options = {}) {
  const image = options.poster ? {
    currentSrc: null,
    getAttribute(name) {
      if (name === 'data-src') return origin + '/poster.jpg';
      if (name === 'alt') return options.alt || null;
      return null;
    }
  } : null;
  return {
    textContent: label,
    className: options.className || '',
    id: options.id || '',
    matches(selector) {
      if (selector === 'a[href]') return true;
      return selector.includes('.movie-link') ? !!options.semantic : false;
    },
    getAttribute(name) {
      return ({
        href, rel: options.rel || null, title: options.title || null,
        'aria-label': options.aria || null, 'data-type': options.dataType || null,
        'data-role': options.role || null, 'data-testid': options.testid || null
      })[name] ?? null;
    },
    querySelector(selector) {
      if (selector === 'img') return image;
      if (selector.includes('[itemprop="name"]')) return options.childHeading ?
        titleNode(options.childHeading) : null;
      return null;
    },
    pick: options.pick || false
  };
}
function card(links, title = null) {
  return {
    querySelectorAll(selector) {
      if (selector === 'a[href]') return links;
      if (selector === 'a.film') return links.filter(a => a.pick);
      if (selector.includes('script[type=')) return [];
      if (selector.includes('h1,h2,h3,h4') && title) return [titleNode(title)];
      return [];
    }
  };
}
function run(cards, overrides = {}, event = 'extract_crawl') {
  let onMessage;
  const posted = [];
  const port = {
    onMessage: { addListener(handler) { onMessage = handler; } },
    onDisconnect: { addListener() {} },
    postMessage(message) { posted.push(message); }
  };
  const window = { addEventListener() {} };
  window.top = window;
  vm.runInNewContext(script, {
    window, document: { querySelectorAll(s) { return s === '.cards' ? cards : []; } },
    location: new URL(documentUrl),
    browser: { runtime: { connectNative() { return port; } } },
    crypto: { getRandomValues(bytes) { bytes.fill(2); return bytes; } },
    URL, Uint8Array, TextEncoder, setTimeout
  });
  assert.equal(posted[0].event, 'hello');
  const documentId = posted[0].documentId;
  onMessage({ schemaVersion: 1, event: 'bound', documentId });
  onMessage({
    schemaVersion: 1, event, documentId,
    requestId: 'a'.repeat(32), startIndex: 0,
    resultsCardSelector: '.cards', titleSelector: null,
    linkSelector: null, thumbnailSelector: null, durationSelector: null,
    ...overrides
  });
  assert.equal(posted.length, 2);
  const response = posted[1];
  assert.equal(response.event, event === 'extract_crawl' ? 'crawl_results' : 'results');
  assert.equal(response.inspectedCards, cards.length);
  return response;
}

test('category link before unmarked film link resolves by exact card-local heading', () => {
  const packet = run([card([
    anchor(origin + '/category/action', 'Action'),
    anchor(origin + '/details?id=1&lang=pl', 'Film A')
  ], 'Film A')]);
  assert.equal(packet.films.length, 1);
  assert.equal(packet.films[0].filmPageUrl, origin + '/details?id=1&lang=pl');
  assert.equal(packet.films[0].originalTitle, 'Film A');
});

test('unmarked category and film links: unique film path is evidence, never first link', () => {
  const packet = run([card([
    anchor(origin + '/browse', 'Browse'),
    anchor(origin + '/watch?id=33&cut=2', 'Watch')
  ])]);
  assert.equal(packet.films[0].filmPageUrl, origin + '/watch?id=33&cut=2');
});

test('two unmarked safe links without film evidence are explicitly ambiguous', () => {
  const packet = run([card([
    anchor(origin + '/catalog-one', 'Browse one'),
    anchor(origin + '/catalog-two', 'Browse two')
  ], 'Niepowiązany tytuł')]);
  assert.equal(packet.status, 'empty');
  assert.equal(packet.inspectedCards, 1);
  assert.equal(packet.films.length, 0);
});

test('two distinct preferred film URLs with no title evidence are ambiguous', () => {
  const packet = run([card([
    anchor(origin + '/watch?a', 'Play A', { semantic: true }),
    anchor(origin + '/watch?b', 'Play B', { semantic: true })
  ])]);
  assert.equal(packet.films.length, 0);
});

test('exact heading agreement disambiguates two different preferred links', () => {
  const packet = run([card([
    anchor(origin + '/watch?a', 'Other film', { semantic: true }),
    anchor(origin + '/watch?b', 'My Film', { semantic: true })
  ], 'My Film')]);
  assert.equal(packet.films[0].filmPageUrl, origin + '/watch?b');
});

test('duplicate anchors for the SAME film URL cannot create false ambiguity', () => {
  const film = origin + '/watch?id=7&lang=pl';
  const packet = run([card([
    anchor(film, 'Film 7'), anchor(film, 'Watch', { semantic: true }),
    anchor(origin + '/category/drama', 'Drama')
  ], 'Film 7')]);
  assert.equal(packet.films.length, 1);
  assert.equal(packet.films[0].filmPageUrl, film);
});

test('two distinct film URLs with similar titles stay distinct across cards', () => {
  const packet = run([
    card([anchor(origin + '/watch?id=7&lang=pl', 'Film X')], 'Film X'),
    card([anchor(origin + '/watch?id=7&lang=en', 'Film X')], 'Film X'),
    card([anchor(origin + '/watch?lang=pl&id=7', 'Film X')], 'Film X')
  ]);
  assert.equal(packet.films.length, 3);
  assert.deepEqual(Array.from(packet.films, f => f.filmPageUrl), [
    origin + '/watch?id=7&lang=pl',
    origin + '/watch?id=7&lang=en',
    origin + '/watch?lang=pl&id=7'
  ]);
});

test('category-only and rel=tag-only cards do not become film results', () => {
  const packet = run([
    card([anchor(origin + '/genre/drama', 'Drama')], 'Drama'),
    card([anchor(origin + '/whatever', 'Drama', { rel: 'tag' })], 'Drama')
  ]);
  assert.equal(packet.films.length, 0);
  assert.equal(packet.inspectedCards, 2);
});

test('explicit link selector cannot silently fall back to another card anchor', () => {
  const packet = run([card([
    anchor(origin + '/category/drama', 'Category', { pick: true }),
    anchor(origin + '/watch?id=9', 'Film')
  ], 'Film')], { linkSelector: 'a.film' });
  assert.equal(packet.films.length, 0);
  const valid = run([card([
    anchor(origin + '/category/drama', 'Category'),
    anchor(origin + '/watch?id=9', 'Film', { pick: true })
  ], 'Film')], { linkSelector: 'a.film' });
  assert.equal(valid.films[0].filmPageUrl, origin + '/watch?id=9');
});

test('sole poster link to a film wins over unmarked secondary navigation', () => {
  const packet = run([card([
    anchor(origin + '/explore', 'Explore'),
    anchor(origin + '/details?film=42', '', { poster: true, alt: 'Poster 42' })
  ])]);
  assert.equal(packet.films[0].filmPageUrl, origin + '/details?film=42');
  assert.equal(packet.films[0].thumbnailUrl, origin + '/poster.jpg');
});

test('cross-origin and media/download links never count as candidate film pages', () => {
  const packet = run([card([
    anchor('https://other.example/watch?id=1', 'External'),
    anchor(origin + '/download.mp4', 'Stream'),
    anchor(origin + '/watch?id=1&v=2', 'Film')
  ], 'Film')]);
  assert.equal(packet.films.length, 1);
  assert.equal(packet.films[0].filmPageUrl, origin + '/watch?id=1&v=2');
});

test('extract preview and paginated crawl use identical safe identity resolution', () => {
  const sample = [card([
    anchor(origin + '/tag/scifi', 'Sci-fi'),
    anchor(origin + '/film?title=2', 'Film B', { semantic: true })
  ], 'Film B')];
  assert.equal(run(sample, {}, 'extract_batch').films[0].filmPageUrl,
    run(sample).films[0].filmPageUrl);
});
