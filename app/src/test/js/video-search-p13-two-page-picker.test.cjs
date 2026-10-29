'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {TextEncoder} = require('node:util');

const script = fs.readFileSync(path.join(__dirname,
  '../../main/assets/extensions/zavro-video-search/content.js'), 'utf8');

function page(pageUrl, epoch, hasResultCard) {
  const sent = [], listeners = new Map(), timers = [];
  let onPortMessage;
  const port = {
    onMessage: {addListener(fn) { onPortMessage = fn; }},
    onDisconnect: {addListener() {}},
    postMessage(packet) { sent.push(packet); }
  };
  const anchor = {
    tagName: 'A', id: '', className: '', parentElement: null,
    querySelector() { return null; },
    closest() { return null; }
  };
  const movie = {
    tagName: 'ARTICLE', id: '', className: 'movie-card',
    classList: ['movie-card'], parentElement: null,
    querySelector(selector) {return selector === 'a[href]' ? anchor : null;}
  };
  anchor.parentElement = movie;
  const body = {
    children: [],
    appendChild(child) {this.children.push(child);},
    remove() {this.removed = true;}
  };
  const document = {
    documentElement: body,
    createElement(tagName) {
      return {
        tagName: tagName.toUpperCase(), children: [],
        style: {}, textContent: '', setAttribute() {},
        addEventListener(name, fn) {this['on_' + name] = fn;},
        appendChild(child) { this.children.push(child); },
        remove() { this.removed = true; }
      };
    },
    querySelectorAll(selector) {
      if (selector === 'article.movie-card' && hasResultCard) return [movie];
      return [];
    },
    querySelector() {return null;},
    addEventListener(name, fn) {listeners.set(name, fn);},
    removeEventListener(name, fn) {
      if(listeners.get(name) === fn) listeners.delete(name);
    }
  };
  const win = {addEventListener() {}};
  win.top = win;
  vm.runInNewContext(script, {
    window: win, document, location: new URL(pageUrl),
    browser: {runtime: {connectNative() {return port;}}},
    crypto: {getRandomValues(bytes) {bytes.fill(epoch); return bytes;}},
    CSS: {escape(s) {return s;}}, clearTimeout() {},
    setTimeout(fn) {timers.push(fn);},
    URL, Uint8Array, TextEncoder
  });
  const hello = sent[0];
  assert.equal(hello.event, 'hello');
  onPortMessage({schemaVersion:1, event:'bound', documentId:hello.documentId});
  let n=0;
  function pick(kind, documentId=hello.documentId) {
    const before=sent.length;
    onPortMessage({schemaVersion:1, event:'pick', documentId,
      requestId:(++n).toString(16).padStart(32,'0'),pickKind:kind});
    return sent.slice(before);
  }
  function clickOnCard() {
    let callback=listeners.get('click');
    assert.equal(typeof callback, 'function');
    callback({target:anchor,isTrusted:true,preventDefault(){},stopImmediatePropagation(){}});
    return sent[sent.length-1];
  }
  function clickAction(label) {
    const banner=body.children.find(node => node.children?.length >= 2);
    const controls=banner.children[1];
    const button=controls.children.find(child=>child.textContent===label);
    assert.ok(button, 'action '+label);
    button.on_click({preventDefault(){},stopPropagation(){}});
    return sent.at(-1);
  }
  return {hello,pick,clickOnCard,clickAction,sent,body,listeners};
}

test('results-only document can select a card with no input or submit button', () => {
  const homepage = page('https://films.example/home', 1, false);
  const results = page('https://films.example/results?q=film', 2, true);
  assert.notEqual(homepage.hello.documentId, results.hello.documentId);
  assert.equal(results.pick('cards').length,0);
  results.clickOnCard();
  const packet=results.clickAction('Zatwierdź');
  assert.equal(packet.event,'picked');
  assert.equal(packet.pickKind,'cards');
  assert.equal(packet.selector,'article.movie-card');
  assert.equal(packet.documentId,results.hello.documentId);
});

test('a stale picker event from the homepage is not accepted on the result page', () => {
  const home=page('https://films.example/',1,false);
  const result=page('https://films.example/results',2,true);
  const returned=result.pick('cards',home.hello.documentId);
  assert.equal(returned.length,0);
  assert.equal(result.listeners.has('click'),false);
  result.pick('cards');
  result.clickOnCard();
  assert.equal(result.clickAction('Zatwierdź').event,'picked');
});

test('cancelling on the results document does not write any card selector', () => {
  const result=page('https://films.example/results',3,true);
  result.pick('cards');
  const cancelled=result.clickAction('Anuluj');
  assert.equal(cancelled.event,'pick_cancelled');
  assert.equal(cancelled.pickKind,'cards');
  assert.equal(result.sent.filter(x=>x.event==='picked').length,0);
});

test('user can skip a cards picker when the homepage has no results', () => {
  const home=page('https://films.example/',4,false);
  home.pick('cards');
  const skipped=home.clickAction('Pomiń');
  assert.equal(skipped.event,'pick_skipped');
  assert.equal(skipped.pickKind,'cards');
  assert.equal(home.sent.filter(x=>x.event==='picked').length,0);
});
