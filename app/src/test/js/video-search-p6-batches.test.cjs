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
const pageUrl = base + '/results?q=ok';

function card(index, heavy = false, hasAnchor = true) {
  const filmUrl = base + '/watch?id=' + index +
    (heavy ? '&payload=' + 'q'.repeat(900) : '');
  const posterUrl = base + '/poster?id=' + index +
    (heavy ? '&payload=' + 'p'.repeat(510) : '');
  const title = heavy ? 'Ź'.repeat(500) + '映像' : 'Film ' + index;
  const image = {
    currentSrc: null,
    getAttribute(key) { return key === 'data-src' ? posterUrl : null; }
  };
  const anchor = {
    textContent: title,
    matches() { return false; },
    getAttribute(key) { return key === 'href' ? filmUrl : null; },
    querySelector(key) { return key === 'img' ? image : null; }
  };
  return {
    filmUrl, posterUrl, title,
    querySelectorAll(key) {
      return key === 'a[href]' && hasAnchor ? [anchor] : [];
    }
  };
}

function start(cards) {
  let onCommand;
  const posted = [];
  const port = {
    onMessage: { addListener(callback) { onCommand = callback; } },
    onDisconnect: { addListener() {} },
    postMessage(message) { posted.push(message); }
  };
  const window = { addEventListener() {} };
  window.top = window;
  vm.runInNewContext(content, {
    window, document: {
      querySelectorAll(selector) { return selector === '.cards' ? cards : []; }
    },
    location: new URL(pageUrl),
    browser: { runtime: { connectNative() { return port; } } },
    crypto: { getRandomValues(bytes) { bytes.fill(1); return bytes; } },
    URL, Uint8Array, TextEncoder, setTimeout
  });
  assert.equal(posted[0].event, 'hello');
  const documentId = posted[0].documentId;
  onCommand({ schemaVersion: 1, event: 'bound', documentId });
  let number = 0;
  return function request(offset, event = 'extract_crawl') {
    const size = posted.length;
    onCommand({
      documentId, schemaVersion: 1, event,
      requestId: (++number).toString(16).padStart(32, '0'),
      resultsCardSelector: '.cards', startIndex: offset,
      linkSelector: null, titleSelector: null,
      thumbnailSelector: null, durationSelector: null
    });
    return posted.length > size ? posted[posted.length - 1] : null;
  };
}

function read(cards, target = cards.length) {
  const request = start(cards);
  let cursor = 0;
  const films = new Map();
  let batches = 0;
  while (cursor < Math.min(320, cards.length) && films.size < target) {
    if (++batches > 150) throw new Error('Crawl has not progressed');
    const packet = request(cursor);
    assert.ok(packet);
    assert.equal(packet.status, 'ok');
    assert.ok(packet.inspectedCards > 0 && packet.inspectedCards <= 16);
    assert.ok(packet.films.length <= 16);
    const bytes = packet.films.reduce((total, film) =>
      total + new TextEncoder().encode(JSON.stringify(film)).length, 0);
    assert.ok(bytes <= 6400, 'Metadata payload exceeds wire budget');
    for (const film of packet.films) {
      assert.ok(film.originalIndex >= cursor &&
        film.originalIndex < cursor + packet.inspectedCards);
      const original = cards[film.originalIndex];
      assert.equal(film.filmPageUrl, original.filmUrl);
      assert.equal(film.originalTitle, original.title);
      assert.equal(film.thumbnailUrl, original.posterUrl);
      films.set(film.filmPageUrl, film);
    }
    cursor += packet.inspectedCards;
  }
  return { films, cursor, batches };
}

test('crawl crosses the historical 80/96 offset rewind and reaches 200 videos', () => {
  const cards = Array.from({ length: 224 }, (_, i) => card(i));
  const result = read(cards, 200);
  assert.ok(result.films.size >= 200);
  assert.ok(result.cursor >= 200);
  assert.ok(result.films.has(cards[96].filmUrl));
});

test('16 long Unicode records require short contiguous packets without losing a film', () => {
  const cards = Array.from({ length: 16 }, (_, i) => card(i, true));
  const result = read(cards);
  assert.equal(result.films.size, 16);
  assert.equal(result.cursor, 16);
  assert.ok(result.batches > 1);
});

test('full title, poster and URL query survive packet splitting unmodified', () => {
  const source = card(8, true);
  const film = read([source]).films.get(source.filmUrl);
  assert.ok(film);
  assert.equal(film.originalTitle, source.title);
  assert.equal(film.filmPageUrl, source.filmUrl);
  assert.equal(film.thumbnailUrl, source.posterUrl);
});

test('absolute last card index 319 does not read card 0', () => {
  const cards = Array.from({ length: 320 }, (_, i) => card(i));
  const packet = start(cards)(319);
  assert.equal(packet.inspectedCards, 1);
  assert.equal(packet.films[0].originalIndex, 319);
});

test('last short window counts exactly two inspected cards', () => {
  const cards = Array.from({ length: 18 }, (_, i) => card(i));
  const packet = start(cards)(16);
  assert.equal(packet.inspectedCards, 2);
  assert.equal(packet.moreOnPage, false);
  assert.equal(packet.films[1].originalIndex, 17);
});

test('unlinked cards consume offsets without generating false films', () => {
  const packet = start([card(0, false, false), card(1), card(2, false, false)])(0);
  assert.equal(packet.inspectedCards, 3);
  assert.equal(packet.films.length, 1);
  assert.equal(packet.films[0].originalIndex, 1);
});

test('preview accepts non-aligned continuation after the first budget-limited packet', () => {
  const cards = Array.from({ length: 16 }, (_, i) => card(i, true));
  const request = start(cards);
  const first = request(0, 'extract_batch');
  assert.ok(first && first.inspectedCards > 0 && first.inspectedCards < 16);
  const second = request(first.inspectedCards, 'extract_batch');
  assert.ok(second);
  assert.ok(second.films.length > 0);
  assert.equal(second.films[0].originalIndex, first.inspectedCards);
});
