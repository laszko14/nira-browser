'use strict';

/*
 * P4-A1: sanitized structural pages representing mixed search grids, ads,
 * recommendations and unsafe links. Real production recognition.js is loaded
 * into a VM. These fixtures are NOT a claim of real-site/device acceptance.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const recognitionSource = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/recognition.js'), 'utf8');
const origin = 'https://films.example';

function inspectPage({route='/search?q=aurora',items=[]}={}) {
  const page = origin + route;
  let clicks = 0;
  const cards = items.map((item, index) => {
    const region = item.region || 'search-results';
    const cardClass = item.cardClass || 'film-card';
    const title = 'Aurora Film ' + (index + 1);
    const parent = {
      id: '', className: region, classList: [region], parentElement: null,
      getAttribute() { return null; }
    };
    const heading = {textContent: title, getAttribute() { return null; }};
    const anchor = {
      className: '', textContent: title,
      getAttribute(key) {
        return key === 'href' ? item.href || '/movie/' + (index + 1) : null;
      },
      hasAttribute(key) { return key === 'download' && item.download === true; },
      querySelector() { return heading; },
      click() { clicks++; }
    };
    return {
      tagName: 'ARTICLE', id: '', className: cardClass,
      classList: [cardClass], parentElement: parent,
      getAttribute() { return null; },
      closest() { return item.hidden === true ? {tagName:'ASIDE'} : null; },
      querySelectorAll(selector) { return selector === 'a[href]' ? [anchor] : []; },
      querySelector(selector) {
        if (selector.includes('h1,h2,h3,h4')) return heading;
        if (selector.includes('img,picture,video')) return {tagName: 'IMG'};
        return null;
      }
    };
  });
  const document = {
    baseURI: page,
    querySelectorAll(selector) {
      if (selector === 'article,div,section,main,form,iframe,*' ||
          selector === 'article,li,figure,div,section' || selector === 'article')
        return cards;
      const match = selector.match(/(?:^|\s)article\.([a-z][\w-]*)$/i);
      if (!match) return [];
      return cards.filter(card => card.classList.includes(match[1]) &&
        (!selector.includes(' ') ||
          selector.startsWith('.' + card.parentElement.className + ' ')));
    }
  };
  const win = {};
  win.top = win;
  vm.runInNewContext(recognitionSource, {
    window: win, document, URL, location: new URL(page), console
  });
  return {
    report: win.__zavroRecognition.scan(document),
    clicked: () => clicks
  };
}

test('P4-A1 mixed search page recognizes only real film cards, not ads or recommendations', () => {
  const page = inspectPage({items: [
    {}, {}, {},
    {cardClass:'sponsored-card', region:'search-results'},
    {cardClass:'sponsored-card', region:'search-results'},
    {cardClass:'recommendation-card', region:'related-videos'},
    {cardClass:'recommendation-card', region:'related-videos'},
    {href:'/category/drama'},
    {href:'https://other.example/movie/9'}
  ]});
  assert.equal(page.report.reason, 'CANDIDATE_FOUND');
  assert.equal(page.report.selector, '.search-results article.film-card');
  assert.equal(page.report.matchedCards, 3);
  assert.ok(page.report.confidence >= 85);
  assert.equal(page.clicked(), 0, 'recognition must never interact with the page');
});

test('P4-A1 homepage recommendation and trending shelves are not search results', () => {
  const page = inspectPage({route:'/',items: [
    {region:'recommended-videos'}, {region:'recommended-videos'},
    {region:'recommended-videos'}, {region:'trending-movies'},
    {region:'trending-movies'}
  ]});
  assert.equal(page.report.selector, null);
  assert.equal(page.report.matchedCards, 0);
  assert.equal(page.report.reason, 'CARDS_NOT_FOUND');
});

test('P4-A1 two equally plausible film grids fail closed as ambiguous', () => {
  const page = inspectPage({items: [
    {cardClass:'movie-card'}, {cardClass:'movie-card'},
    {cardClass:'episode-card'}, {cardClass:'episode-card'}
  ]});
  assert.equal(page.report.selector, null);
  assert.equal(page.report.reason, 'AMBIGUOUS_CARDS');
  assert.ok(page.report.confidence >= 85);
});

test('P4-A1 malicious, external, category and media URLs cannot inflate a valid film group', () => {
  const page = inspectPage({items: [
    {}, {},
    {href:'javascript:alert(1)'},
    {href:'https://outside.example/movie/3'},
    {href:'/category/action'},
    {href:'/video/clip.mp4'},
    {href:'/movie/7', download:true}
  ]});
  assert.equal(page.report.reason, 'CANDIDATE_FOUND');
  assert.equal(page.report.matchedCards, 2);
  assert.equal(page.report.selector, '.search-results article.film-card');
  assert.equal(page.clicked(), 0);
});

test('P4-A1 hidden and navigation-region film-shaped items are not accepted', () => {
  const page = inspectPage({items: [
    {hidden:true}, {hidden:true},
    {region:'sidebar-movies'}, {region:'sidebar-movies'},
    {region:'advertisements'}
  ]});
  assert.equal(page.report.selector, null);
  assert.equal(page.report.reason, 'CARDS_NOT_FOUND');
  assert.equal(page.clicked(), 0);
});


test('P4-A1 film-like cards with only blocked URLs cannot be accepted as results', () => {
  const page = inspectPage({items: [
    {href:'/login'}, {href:'/checkout'},
    {href:'https://person:secret@films.example/movie/4'},
    {href:'/video/clip.webm'}, {href:'/tags/comedy'},
    {href:'data:text/html,not-a-film'}
  ]});
  assert.equal(page.report.reason, 'CARDS_NOT_FOUND');
  assert.equal(page.report.selector, null);
  assert.equal(page.report.matchedCards, 0);
  assert.equal(page.clicked(), 0);
});
