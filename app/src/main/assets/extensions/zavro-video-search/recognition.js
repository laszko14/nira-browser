/*
 * Zavro P1 structural recognition — read only, bounded, no page actions.
 * This object lives in the WebExtension's isolated content-script world.
 * It does not expose a native bridge or transmit raw DOM or query text.
 */
(function () {
  "use strict";
  if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
  const MAX_NODES = 260, MAX_LINKS = 12, MAX_CONTEXTS = 12;
  const BAD_PATH = /(?:^|\/)(?:categor(?:y|ies)|genres?|tags?|topics?|channels?|authors?|playlists?|collections?|account|login|signin|register|checkout|ads?)(?:\/|$)/i;
  const MEDIA_FILE = /\.(?:mp4|webm|m3u8|mpd|mp3|m4a|ts)(?:$)/i;
  const BAD_HINT = /(?:^|[\s_-])(?:advert(?:isement)?|sponsored|promo|banner|navigation|menu|breadcrumb|sidebar|category|tag|login|checkout)(?:$|[\s_-])/i;
  const CARD_HINT = /(?:video|film|movie|episode|search|result|card|tile|post|listing|thumb)/i;
  const TOKEN = /^[a-zA-Z_][a-zA-Z0-9_-]{1,42}$/;
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
  function findContexts(documentRoot) {
    const contexts = [{ root: documentRoot, surface: "MAIN" }];
    const queue = [documentRoot];
    let seen = 0;
    while (queue.length && contexts.length < MAX_CONTEXTS && seen < MAX_NODES) {
      const root = queue.shift();
      let nodes;
      try { nodes = root.querySelectorAll?.("article,div,section,main,form,iframe,*") || []; }
      catch (_) { continue; }
      for (const node of Array.from(nodes).slice(0, MAX_NODES - seen)) {
        seen++;
        if (contexts.length >= MAX_CONTEXTS) break;
        if (node.shadowRoot && node.shadowRoot.mode === "open") {
          contexts.push({root: node.shadowRoot, surface: "SHADOW"});
          queue.push(node.shadowRoot);
        }
        if (node.tagName === "IFRAME") {
          try {
            const frame = node.contentDocument;
            if (frame && frame.location?.origin === location.origin) {
              contexts.push({root: frame, surface: "FRAME"});
              queue.push(frame);
            }
          } catch (_) { /* cross-origin frames are never probed further */ }
        }
      }
    }
    return contexts;
  }
  function selectorForGroup(root, example) {
    if (!example?.tagName) return null;
    const tag = example.tagName.toLowerCase();
    const classes = Array.from(example.classList || []).filter(v => TOKEN.test(v) &&
      !/(?:^|[-_])\d+$/.test(v)).slice(0, 5);
    const candidates = classes.map(c => tag + "." + c);
    const parent = example.parentElement;
    for (const c of Array.from(parent?.classList || []).filter(x => TOKEN.test(x)).slice(0, 4)) {
      if (CARD_HINT.test(c)) {
        for (const item of classes) candidates.unshift("." + c + " " + tag + "." + item);
      }
    }
    if (["article", "li", "figure"].includes(tag)) candidates.push(tag);
    for (const candidate of candidates) {
      if (candidate.length > 160 || /:has\(|:not\(/.test(candidate)) continue;
      try {
        const matches = root.querySelectorAll(candidate);
        if (matches.length >= 1 && matches.length <= 320 &&
            Array.from(matches).includes(example)) return candidate;
      } catch (_) { /* invalid selector */ }
    }
    return null;
  }
  function scoreCard(node, base, inResults) {
    if (!node || !node.querySelectorAll || BAD_HINT.test(nodeHint(node)) ||
        node.closest?.("nav,header,footer,aside,[role=navigation],[aria-hidden=true],[hidden],[inert]"))
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
    let visited = 0, checkedRoots = 0, best = null, ambiguous = false;
    const rootContexts = findContexts(root);
    for (const ctx of rootContexts) {
      checkedRoots++;
      let nodes;
      try { nodes = ctx.root.querySelectorAll("article,li,figure,div,section"); }
      catch (_) { continue; }
      const groups = new Map();
      for (const node of Array.from(nodes).slice(0, MAX_NODES - visited)) {
        visited++;
        const card = scoreCard(node, page, inResults);
        if (!card) continue;
        const selector = selectorForGroup(ctx.root, node);
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
        const result = {selector:candidate.selector, surface:candidate.surface,
          confidence, matchedCards:candidate.cards,
          evidence:["FILM_PAGE_LINK","CARD_LOCAL_METADATA",
            ...(repetition ? ["REPEATED_STRUCTURE"] : []),
            ...(inResults ? ["RESULTS_CONTEXT"] : [])]};
        if (!best || result.confidence > best.confidence + 14) {
          best=result; ambiguous=false;
        } else if (Math.abs(result.confidence-best.confidence) < 15 &&
                   result.selector !== best.selector) ambiguous=true;
      }
      if (visited >= MAX_NODES) break;
    }
    return {selector: best && !ambiguous && best.surface === "MAIN" ? best.selector : null,
      confidence: best?.confidence || 0,
      reason: ambiguous ? "AMBIGUOUS_CARDS" : best?.surface === "SHADOW" ? "SHADOW_RESULTS" :
        best?.surface === "FRAME" ? "FRAME_RESULTS" : best ? "CANDIDATE_FOUND" : "CARDS_NOT_FOUND",
      surface:best?.surface || "MAIN", matchedCards:best?.matchedCards || 0,
      evidence:best?.evidence || [], scannedElements:visited,
      truncated:visited >= MAX_NODES, contextsChecked:checkedRoots};
  }
  function controlHint(control) {
    let svg = null;
    try { svg = control?.querySelector?.("svg title,svg[aria-label],use"); }
    catch (_) { /* no SVG */ }
    const label = [control?.innerText, control?.value, control?.id,
      control?.getAttribute?.("aria-label"), control?.getAttribute?.("title"),
      control?.getAttribute?.("data-action"), control?.getAttribute?.("data-testid"),
      textOf(svg)].filter(x => typeof x === "string").join(" ").slice(0, 280);
    return label;
  }
  Object.defineProperty(window, "__zavroRecognition", {
    value: Object.freeze({scan,controlHint}), configurable: false,
    enumerable: false, writable: false
  });
})();
