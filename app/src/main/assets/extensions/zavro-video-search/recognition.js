/*
 * Zavro P1 structural recognition — read only, bounded, no page actions.
 * This object lives in the WebExtension's isolated content-script world.
 * It does not expose a native bridge or transmit raw DOM or query text.
 */
(function () {
  "use strict";
  if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
  const MAX_NODES = 260, MAX_LINKS = 12, MAX_CONTEXTS = 12;
  // Bound DOM visits as well as scored candidates on large dynamic pages.
  const MAX_DOM_VISITS = 2400;
  // A replaced same-origin iframe document invalidates its prior locators.
  const observedFrames = new WeakMap();
  const BAD_PATH = /(?:^|\/)(?:categor(?:y|ies)|genres?|tags?|topics?|channels?|authors?|playlists?|collections?|account|login|signin|register|checkout|ads?)(?:\/|$)/i;
  const MEDIA_FILE = /\.(?:mp4|webm|m3u8|mpd|mp3|m4a|ts)(?:$)/i;
  const BAD_HINT = /(?:^|[\s_-])(?:advert(?:isement)?|sponsored|promo|banner|navigation|menu|breadcrumb|sidebar|category|tag|login|checkout)(?:$|[\s_-])/i;
  const CARD_HINT = /(?:video|film|movie|episode|search|result|card|tile|post|listing|thumb)/i;
  const TOKEN = /^[a-zA-Z_][a-zA-Z0-9_-]{1,42}$/;
  // Avoid auto-learning generated build hashes and transient numeric CSS.
  const STABLE_CLASS = c => TOKEN.test(c) &&
    !/(?:[a-f0-9]{9,}|(?:^|[-_])[0-9]{5,}(?:$|[-_]))/i.test(c);
  const nodeHint = node => [node?.id, typeof node?.className === "string" ? node.className : "",
    node?.getAttribute?.("role"), node?.getAttribute?.("data-testid"),
    node?.getAttribute?.("aria-label"), node?.getAttribute?.("data-type")]
    .filter(x => typeof x === "string").map(x => x.slice(0, 90)).join(" ");
  function href(anchor, base) {
    const raw = anchor?.getAttribute?.("href");
    if (!raw || raw.length > 1800 || /^(?:javascript|data|blob|file|mailto|magnet):/i.test(raw))
      return null;
    try {
      const u = new URL(raw, base);
      const owner = new URL(base);
      if (!/^https?:$/.test(u.protocol) || u.origin !== owner.origin ||
          u.username || u.password || u.href === base || u.href.length > 2048 ||
          BAD_PATH.test(u.pathname) || MEDIA_FILE.test(u.pathname) ||
          anchor.hasAttribute?.("download")) return null;
      return u.href;
    } catch (_) { return null; }
  }
  function textOf(node) {
    const t = node?.getAttribute?.("aria-label") || node?.getAttribute?.("title") ||
      node?.getAttribute?.("content") || node?.textContent;
    return typeof t === "string" && t.trim().length >= 2 && t.trim().length <= 300 ? t.trim() : null;
  }
  function hintSearch(root) {
    try {
      const path = new URL(location.href);
      if (/(?:search|results|szukaj|wyniki|find)/i.test(path.pathname)) return true;
      return ["q", "s", "query", "search", "keyword", "term"].some(k => path.searchParams.has(k));
    } catch (_) { return false; }
  }
  // Scoped selectors are versioned, bounded locators, never executable CSS.
  // They contain only stable host CSS paths and an inner card selector.
  const LOCATOR = /^zcs1:[A-Za-z0-9_-]{6,152}$/;
  const SIMPLE_CSS = /^[a-zA-Z0-9#_.\[\]="' >:*+-]{1,160}$/;
  function safeCss(value) {
    return typeof value === "string" && SIMPLE_CSS.test(value) &&
      !/:has\(|:not\(|:nth|javascript|\*/i.test(value);
  }
  function stableHost(root, node) {
    if (!node?.tagName || !root?.querySelectorAll) return null;
    const tag = node.tagName.toLowerCase();
    const choices = [];
    if (TOKEN.test(node.id || "") && STABLE_CLASS(node.id)) choices.push("#" + node.id);
    const classes = Array.from(node.classList || []).filter(STABLE_CLASS).slice(0, 4);
    for (const c of classes) choices.push(tag + "." + c);
    for (let i = 0; i < classes.length; i++) {
      for (let j = i + 1; j < classes.length; j++)
        choices.push(tag + "." + classes[i] + "." + classes[j]);
    }
    for (const selector of choices) {
      if (!safeCss(selector)) continue;
      try {
        const matches = root.querySelectorAll(selector);
        if (matches.length === 1 && matches[0] === node) return selector;
      } catch (_) { /* malformed DOM or unstable host */ }
    }
    return null;
  }
  function encodeLocator(steps, leaf) {
    if (!steps || !steps.length || !safeCss(leaf) ||
        typeof btoa !== "function") return null;
    const raw = [...steps, leaf].join("~");
    if (raw.length > 112) return null;
    try {
      const locator = "zcs1:" + btoa(raw).replace(/\+/g, "-")
        .replace(/\//g, "_").replace(/=+$/, "");
      return LOCATOR.test(locator) ? locator : null;
    } catch (_) { return null; }
  }
  function resolve(locator) {
    if (!LOCATOR.test(locator || "") || typeof atob !== "function") return null;
    try {
      const payload = locator.slice(5).replace(/-/g, "+").replace(/_/g, "/");
      const parts = atob(payload);
      const items = parts.split("~");
      if (items.length < 3 || items.length > 7 || items.length % 2 !== 1 ||
          items.join("~") !== parts) return null;
      let root = document;
      for (let i = 0; i < items.length - 1; i += 2) {
        if (!safeCss(items[i + 1])) return null;
        const hosts = root.querySelectorAll(items[i + 1]);
        if (hosts.length !== 1) return null;
        const host = hosts[0];
        if (host.isConnected === false) return null;
        if (items[i] === "S") {
          if (!host.shadowRoot || host.shadowRoot.mode !== "open") return null;
          root = host.shadowRoot;
        } else if (items[i] === "F") {
          if (host.tagName !== "IFRAME") return null;
          const frame = host.contentDocument;
          if (!frame || frame.location?.origin !== location.origin ||
              (observedFrames.has(host) && observedFrames.get(host) !== frame)) return null;
          observedFrames.set(host, frame);
          root = frame;
        } else return null;
      }
      if (!safeCss(items[items.length - 1])) return null;
      return root.querySelectorAll(items[items.length - 1]);
    } catch (_) { return null; } // Includes cross-origin and detached-frame access.
  }
  function findContexts(documentRoot, observation) {
    const main = {root:documentRoot, surface:"MAIN", steps:[]};
    const contexts = [main], queue = [main];
    let seen = 0;
    while (queue.length && contexts.length < MAX_CONTEXTS && seen < MAX_NODES) {
      const ctx = queue.shift(), root = ctx.root;
      let nodes = [];
      try {
        const owner = root.ownerDocument || documentRoot;
        const walker = owner.createTreeWalker?.(root, 1);
        if (walker) {
          let node;
          while (nodes.length < MAX_NODES - seen && (node = walker.nextNode()))
            nodes.push(node);
        } else {
          nodes = Array.from(root.querySelectorAll?.("article,div,section,main,form,iframe,*") || [])
            .slice(0, MAX_NODES - seen);
        }
      } catch (_) { continue; }
      for (const node of nodes) {
        seen++;
        if (contexts.length >= MAX_CONTEXTS) break;
        if (node.shadowRoot?.mode === "open") {
          const host = stableHost(root, node);
          const next = {root:node.shadowRoot, surface:"SHADOW",
            steps:ctx.steps && host ? [...ctx.steps, "S", host] : null};
          contexts.push(next);
          queue.push(next);
        }
        if (node.tagName === "IFRAME" && contexts.length < MAX_CONTEXTS) {
          try {
            const frame = node.contentDocument;
            if (frame && frame.location?.origin === location.origin &&
                node.isConnected !== false) {
              // A replaced iframe keeps its CSS host but not its document.
              // The saved locator must remain stale, not silently rebind.
              if (observedFrames.has(node) && observedFrames.get(node) !== frame) {
                if (observation) observation.frameReplaced = true;
                continue;
              }
              observedFrames.set(node, frame);
              const host = stableHost(root, node);
              const next = {root:frame, surface:"FRAME",
                steps:ctx.steps && host ? [...ctx.steps, "F", host] : null};
              contexts.push(next);
              queue.push(next);
            }
          } catch (_) { /* A cross-origin frame is never inspected. */ }
        }
      }
    }
    return contexts;
  }

  // The existing bounded context graph serves results, inputs and controls.
  function roots() {
    return findContexts(document).filter(ctx => ctx.steps !== null)
      .map(ctx => ctx.root);
  }
  function inputs() {
    const found = [];
    let visited = 0;
    for (const root of roots()) {
      if (found.length >= 80 || visited >= MAX_DOM_VISITS) break;
      try {
        const owner = root.ownerDocument || root;
        const walker = owner.createTreeWalker?.(root, 1);
        if (walker) {
          let node;
          while (found.length < 80 && visited < MAX_DOM_VISITS &&
                 (node = walker.nextNode())) {
            visited++;
            if (node.tagName === "INPUT") found.push(node);
          }
        } else {
          const candidates = root.querySelectorAll?.("input") || [];
          for (let i = 0; i < candidates.length && found.length < 80 &&
               visited < MAX_DOM_VISITS; i++) {
            visited++;
            found.push(candidates[i]);
          }
        }
      } catch (_) { /* Inaccessible or replaced context is never trusted. */ }
    }
    return found;
  }
  function scope(element, leaf) {
    if (!element || element.isConnected === false || !safeCss(leaf)) return null;
    for (const ctx of findContexts(document)) {
      if (!ctx.steps?.length) continue;
      try {
        const matches = ctx.root.querySelectorAll(leaf);
        if (matches.length !== 1 || matches[0] !== element) continue;
        const locator = encodeLocator(ctx.steps, leaf);
        const resolved = locator && resolve(locator);
        if (resolved?.length === 1 && resolved[0] === element) return locator;
      } catch (_) { /* Detached, unstable and ambiguous roots fail closed. */ }
    }
    return null;
  }
  // A sparse dynamic page may contain thousands of non-card nodes.
  // Share one bounded visit budget across all accessible root contexts.
  function candidates(root, selector, limit, budget) {
    if (budget <= 0 || limit <= 0) return {nodes:[], visits:0};
    const owner = root.ownerDocument || (typeof root.createTreeWalker === "function" ? root : null);
    const walker = owner?.createTreeWalker?.(root, 1);
    if (!walker) {
      // Synthetic fixture fallback; production uses bounded TreeWalker.
      const nodes = Array.from(root.querySelectorAll(selector)).slice(0, Math.min(limit, budget));
      return {nodes, visits:nodes.length};
    }
    const nodes = [];
    let visits = 0, node;
    while (visits < budget && nodes.length < limit &&
           (node = walker.nextNode())) {
      visits++;
      if (node.matches?.(selector)) nodes.push(node);
    }
    return {nodes, visits};
  }
  function selectorForGroup(root, example, cache) {
    if (!example?.tagName) return null;
    const tag = example.tagName.toLowerCase();
    const classes = Array.from(example.classList || []).filter(v => STABLE_CLASS(v) &&
      !/(?:^|[-_])\d+$/.test(v)).slice(0, 5);
    const candidates = classes.map(c => tag + "." + c);
    const parent = example.parentElement;
    for (const c of Array.from(parent?.classList || []).filter(STABLE_CLASS).slice(0, 4)) {
      if (CARD_HINT.test(c)) {
        for (const item of classes) candidates.unshift("." + c + " " + tag + "." + item);
      }
    }
    if (["article", "li", "figure"].includes(tag)) candidates.push(tag);
    for (const candidate of candidates) {
      if (candidate.length > 160 || /:has\(|:not\(/.test(candidate)) continue;
      try {
        if (!cache.has(candidate)) {
          const matches = root.querySelectorAll(candidate);
          cache.set(candidate, matches.length <= 320 ? Array.from(matches) : []);
        }
        if (cache.get(candidate).includes(example)) return candidate;
      } catch (_) { /* invalid selector */ }
    }
    return null;
  }
  function scoreCard(node, base, inResults) {
    if (!node || !node.querySelectorAll || BAD_HINT.test(nodeHint(node)) ||
        node.closest?.("nav,header,footer,aside,[role=navigation],[aria-hidden=true],[hidden],[inert],.ad,.advertisement,.sponsored,[data-ad]"))
      return null;
    const links = Array.from(node.querySelectorAll("a[href]")).slice(0, MAX_LINKS);
    let winner = null, ties = 0;
    const heading = textOf(node.querySelector?.("h1,h2,h3,h4,[itemprop=name],.video-title,.movie-title,.film-title,.title"));
    const image = node.querySelector?.("img,picture,video,[itemprop=thumbnailUrl]");
    const duration = node.querySelector?.("time[datetime],[data-duration],.duration,.runtime");
    for (const a of links) {
      const url = href(a, base);
      if (!url || BAD_HINT.test(nodeHint(a))) continue;
      const label = textOf(a);
      const titleRelation = !!heading && !!label && (heading.toLowerCase() === label.toLowerCase() ||
        label.toLowerCase().includes(heading.toLowerCase()));
      const semantic = !!a.querySelector?.("h1,h2,h3,h4,[itemprop=name],.title,img");
      const path = /(?:^|\/)(?:watch|movie|film|video|episode|title|play)(?:\/|$)/i.test(new URL(url).pathname);
      const points = 25 + (titleRelation ? 20 : heading && semantic ? 14 : 0) +
        (image ? 15 : 0) + (duration ? 5 : 0) + (path ? 10 : 0) +
        (inResults ? 10 : 0);
      if (!winner || points > winner.points) { winner = {url,points}; ties = 0; }
      else if (points === winner.points && url !== winner.url) ties++;
    }
    if (!winner || ties > 0 || (!heading && !image)) return null;
    return {points: winner.points, fingerprint: node.tagName + "/" +
      Array.from(node.classList || []).filter(c => TOKEN.test(c)).slice(0, 2).join("."),
      parent: node.parentElement, node};
  }
  function scan(root, opts) {
    const page = opts?.pageUrl || location.href;
    const inResults = opts?.resultsContext === true || hintSearch(root);
    let visited = 0, domVisits = 0, checkedRoots = 0, quotaLimited = false;
    let best = null, ambiguous = false;
    const observation = {frameReplaced:false};
    const rootContexts = findContexts(root, observation);
    for (const ctx of rootContexts) {
      checkedRoots++;
      let nodes;
      try {
        // Reserve capacity for later accessible Shadow/frame roots, rather
        // than letting a dense MAIN document starve them of all observations.
        const left = MAX_DOM_VISITS - domVisits;
        const rootCount = rootContexts.length - checkedRoots + 1;
        const quota = Math.ceil(left / rootCount);
        const batch = candidates(ctx.root, "article,li,figure,div,section",
          MAX_NODES - visited, quota);
        nodes = batch.nodes;
        domVisits += batch.visits;
        if (quota < left && batch.visits >= quota) quotaLimited = true;
      } catch (_) { continue; }
      const groups = new Map(), selectors = new Map();
      for (const node of nodes) {
        visited++;
        const card = scoreCard(node, ctx.root.baseURI || ctx.root.ownerDocument?.baseURI || page, inResults);
        if (!card) continue;
        const selector = selectorForGroup(ctx.root, node, selectors);
        if (!selector) continue;
        const key = selector + "|" + ctx.surface + "|" +
          (card.parent ? nodeHint(card.parent).slice(0, 48) : "");
        const existing = groups.get(key) || {selector, surface:ctx.surface,
          cards:0, points:0, parent:card.parent, fingerprints:new Set()};
        existing.cards++;
        existing.points += card.points;
        existing.fingerprints.add(card.fingerprint);
        groups.set(key, existing);
      }
      for (const candidate of groups.values()) {
        const repetition = candidate.cards >= 2 ? 15 : 0;
        const structure = candidate.fingerprints.size === 1 ? 10 : 0;
        const confidence = Math.min(100, Math.round(candidate.points /
          candidate.cards + repetition + structure));
        if (candidate.cards === 1 && confidence < 85) continue;
        if (candidate.cards >= 2 && confidence < 75) continue;
        const result = {selector:ctx.surface === "MAIN" ? candidate.selector :
             encodeLocator(ctx.steps, candidate.selector), surface:candidate.surface,
          confidence, matchedCards:candidate.cards,
          evidence:["FILM_PAGE_LINK","CARD_LOCAL_METADATA",
            ...(repetition ? ["REPEATED_STRUCTURE"] : []),
            ...(inResults ? ["RESULTS_CONTEXT"] : [])]};
        if (!best || result.confidence > best.confidence + 14) {
          best=result; ambiguous=false;
        } else if (Math.abs(result.confidence-best.confidence) < 15 &&
                   result.selector !== best.selector) ambiguous=true;
      }
      if (visited >= MAX_NODES || domVisits >= MAX_DOM_VISITS) break;
    }
    // Native rejects found selectors with confidence below 85. Weak
    // evidence must be diagnostic, never an optimistic found response.
    const exhausted = quotaLimited || visited >= MAX_NODES ||
      domVisits >= MAX_DOM_VISITS;
    const accepted = best && !ambiguous && !exhausted &&
      best.selector && best.confidence >= 85;
    const reason = ambiguous ? "AMBIGUOUS_CARDS" :
      exhausted ? "SCAN_LIMIT" :
      accepted ? "CANDIDATE_FOUND" :
      best?.confidence < 85 && best?.confidence > 0 ? "LOW_CONFIDENCE" :
      best?.surface === "SHADOW" ? "SHADOW_RESULTS" :
      best?.surface === "FRAME" ? "FRAME_RESULTS" :
      observation.frameReplaced ? "FRAME_REPLACED" : "CARDS_NOT_FOUND";
    return {selector: accepted ? best.selector : null,
      confidence: best?.confidence || 0, reason,
      surface:best?.surface || (observation.frameReplaced ? "FRAME" : "MAIN"),
      matchedCards:best?.matchedCards || 0,
      evidence:best?.evidence || [], scannedElements:visited,
      truncated:exhausted,
      contextsChecked:checkedRoots};
  }
  function controlHint(control) {
    let svg = null;
    try { svg = control?.querySelector?.("svg title,svg[aria-label],svg use,svg [data-icon]"); }
    catch (_) { /* no SVG */ }
    const referenced = (control?.getAttribute?.("aria-labelledby") || "")
      .split(/\s+/).filter(Boolean).slice(0, 3).map(id => {
        if (!TOKEN.test(id)) return "";
        return control.ownerDocument?.getElementById?.(id)?.textContent || "";
      });
    const iconName = [svg?.getAttribute?.("href"), svg?.getAttribute?.("xlink:href"),
      svg?.getAttribute?.("data-icon")].find(x =>
        typeof x === "string" && /^#?[a-zA-Z][a-zA-Z0-9_-]{1,48}$/.test(x));
    return [control?.innerText, control?.value, control?.id,
      control?.getAttribute?.("aria-label"), control?.getAttribute?.("title"),
      control?.getAttribute?.("data-action"), control?.getAttribute?.("data-testid"),
      ...referenced, textOf(svg), iconName].filter(x => typeof x === "string")
      .join(" ").slice(0, 280);
  }
  Object.defineProperty(window, "__zavroRecognition", {
    value: Object.freeze({scan,controlHint,resolve,roots,inputs,scope}), configurable: false,
    enumerable: false, writable: false
  });
})();
