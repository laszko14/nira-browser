"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const script = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/full-titles.js"), "utf8");

test("full titles: sibling caption outside thumbnail wrapper is expanded", () => {
  class Element {
    constructor(tag, cls = "", title = "", attrs = {}) {
      this.tag = tag; this.className = cls; this.id = "";
      this.textContent = title; this.attrs = attrs; this.parentElement = null;
      this.children = []; this.isConnected = true; this.isContentEditable = false;
      this.classes = new Set(); this.classList = { add: c => this.classes.add(c) };
    }
    add(child) { child.parentElement = this; this.children.push(child); return child; }
    getAttribute(key) { return this.attrs[key] || null; }
    contains(other) { return this === other || this.children.some(c => c.contains(other)); }
    closest(selector) {
      if (selector === "header,nav,footer" ||
          selector === "button,nav,footer,header") return null;
      let node = this;
      if (selector === "a[href]") {
        while (node) {
          if (node.tag === "a" && node.attrs.href) return node;
          node = node.parentElement;
        }
      }
      return null;
    }
    querySelectorAll(selector) {
      const descendants = [];
      const visit = n => { for (const child of n.children) { descendants.push(child); visit(child); } };
      visit(this);
      if (selector === "img,video[poster]" || selector === "img,video") {
        return descendants.filter(e => e.tag === "img");
      }
      if (selector === "a[href]") {
        return descendants.filter(e => e.tag === "a" && e.attrs.href);
      }
      if (selector.includes("[data-video-title]")) {
        return descendants.filter(e => e.className.includes("title"));
      }
      return [];
    }
    querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  }
  const outer = new Element("main", "listing");
  const card = outer.add(new Element("div", "video-card"));
  const thumb = card.add(new Element("div", "video-thumb"));
  const a = thumb.add(new Element("a", "", "", { href: "/watch/movie/123" }));
  const image = a.add(new Element("img"));
  const caption = card.add(new Element("div", "video-title",
    "Mama Fiona - Bouncy Hou...", {
      title: "Mama Fiona - Bouncy House Special Full Title",
    }));
  const timers = [];
  const styles = [];
  const document = {
    documentElement: { appendChild: node => styles.push(node) },
    head: { appendChild: node => styles.push(node) },
    getElementById: id => styles.find(node => node.id === id) || null,
    createElement: tag => new Element(tag),
    querySelectorAll: selector => selector === "img,video[poster]" ? [image] : [],
    addEventListener() {},
  };
  const window = { addEventListener() {} };
  class MutationObserver { observe() {} }
  vm.runInNewContext(script, {
    document, window, MutationObserver,
    setTimeout: fn => { timers.push(fn); },
    getComputedStyle: () => ({ textOverflow: "ellipsis", whiteSpace: "nowrap",
      webkitLineClamp: "none", overflow: "hidden" }),
  });
  while (timers.length) timers.shift()();
  assert.equal(caption.textContent, "Mama Fiona - Bouncy House Special Full Title");
  assert.equal(caption.classes.has("nira-full-video-title"), true);
  assert.equal(styles.length, 1);
  assert.match(styles[0].textContent, /display: block !important/);
});
