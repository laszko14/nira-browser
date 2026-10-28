"use strict";

(() => {
  // Isolated-world guard: fallback blank injection can race declarative injection.
  if (globalThis.__niraDiscoveryV1164) return;
  Object.defineProperty(globalThis, "__niraDiscoveryV1164", { value: true });
  // Only the extension's background can mint root/frame correlation keys.
  // No video playback, network probe, script execution or persistent URL storage.
  const FRAME = typeof window.top !== "undefined" && window !== window.top;
  const MAX_ITEMS = 128, MAX_SEEN = 256, MAX_SCRIPT_CHARS = 48000;
  let activeUrl = location.href, context = null, registering = null, navigationEpoch = 0;
  let scheduled = false;
  let scanRoot = document, scanTimer = null, alive = true;
  let statsTimer = null, offers = 0, playObserved = false;
  const stats = new Map();
  const jobs = [], pendingRoots = new Set();
  let observedRoots = new WeakSet();
  let scriptVersions = new WeakSet();
  let structuredVersions = new WeakSet();
  let overflowRoot = false;
  // Bounded slices, not a permanent cutoff at the first N nodes.
  const SLICE_MS = 8, MAX_JOBS = 64, MAX_ROOTS = 32;
  const seen = new Map(), groups = new WeakMap();
  let structuredGroups = new WeakMap();
  let nextGroup = 0;
  const opaqueFrameVersions = new WeakMap();
  function inspectFrame(element) {
    if (FRAME || element.tagName !== "IFRAME") return;
    const version = element.getAttribute("src") + "\0" + element.getAttribute("srcdoc");
    if (opaqueFrameVersions.get(element) === version) return;
    opaqueFrameVersions.set(element, version);
    ensureDocument().then(root => {
      if (root && alive) return browser.runtime.sendMessage({ schemaVersion: 1,
        event: "opaque_frames_changed", documentKey: root.documentKey });
    }).catch(() => {});
  }

  function resetNavigation() {
    if (activeUrl === location.href) return;
    activeUrl = location.href;
    navigationEpoch++;
    cancelNativeQueue();
    context = null;
    registering = null;
    seen.clear(); structuredGroups = new WeakMap(); offers = 0; playObserved = false; stats.clear();
    if (statsTimer !== null) clearTimeout(statsTimer);
    statsTimer = null;
    jobs.length = 0; pendingRoots.clear(); pendingRoots.add(document);
    scriptVersions = new WeakSet(); structuredVersions = new WeakSet();
  }
  function ensureDocument() {
    resetNavigation();
    if (context) return Promise.resolve(context);
    if (registering) return registering;
    const navigation = location.href, epoch = navigationEpoch;
    const request = FRAME ?
      { schemaVersion: 1, event: "frame_started", frameUrl: navigation } :
      { schemaVersion: 1, event: "document_started", pageUrl: navigation };
    const promise = Promise.resolve().then(() => browser.runtime.sendMessage(request))
      .then(reply => {
        if (!alive || epoch !== navigationEpoch || !reply || navigation !== location.href ||
            !Number.isSafeInteger(reply.documentKey) || reply.documentKey <= 0) return null;
        if (FRAME) {
          if (!Number.isSafeInteger(reply.frameKey) || reply.frameKey <= 0 ||
              !Number.isSafeInteger(reply.frameId) || reply.frameId <= 0 ||
              reply.frameUrl !== navigation || typeof reply.pageUrl !== "string") return null;
          return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", {
            schemaVersion: 1, event: "media_frame", pageUrl: reply.pageUrl,
            documentKey: reply.documentKey, frameUrl: navigation, frameId: reply.frameId,
            frameKey: reply.frameKey, active: true,
          })).then(async accepted => {
          if (accepted !== true || epoch !== navigationEpoch || !alive) return null;
          const ready = await browser.runtime.sendMessage({ schemaVersion: 1, event: "frame_ready", frameKey: reply.frameKey });
          if (ready !== true || epoch !== navigationEpoch || !alive) return null;
          note("frame_registered");
          if (navigation === "about:blank") note("iframe_about_blank");
          if (navigation === "about:srcdoc") note("iframe_srcdoc");
          return { documentKey: reply.documentKey, pageUrl: reply.pageUrl,
            frameUrl: navigation, frameKey: reply.frameKey, frameId: reply.frameId };
          });
        }
        return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", {
          schemaVersion: 1, event: "media_document",
          pageUrl: navigation, documentKey: reply.documentKey, frameTracking: true
        })).then(async accepted => {
          if (accepted !== true || !alive || epoch !== navigationEpoch || location.href !== navigation) return null;
          const ready = await browser.runtime.sendMessage({ schemaVersion: 1, event: "document_ready", documentKey: reply.documentKey });
          return ready === true && epoch === navigationEpoch ? { documentKey: reply.documentKey, pageUrl: navigation } : null;
        });
      }).catch(() => null);
    const pending = promise.then(result => {
      if (registering === pending) registering = null;
      if (!alive || epoch !== navigationEpoch || navigation !== location.href) return null;
      if (result) context = result;
      return result;
    });
    registering = pending;
    return pending;
  }

  function note(code, count = 1) {
    if (!alive || count < 1) return;
    stats.set(code, Math.min(100000, (stats.get(code) || 0) + count));
    if (statsTimer !== null) return;
    const navigation = location.href;
    statsTimer = setTimeout(() => {
      statsTimer = null;
      const batch = [...stats]; stats.clear();
      if (!alive || navigation !== location.href) return;
      ensureDocument().then(root => {
        if (!root || !alive || navigation !== location.href) return;
        for (const [code, count] of batch)
          Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", {
            schemaVersion: 1, event: "discovery_stats", documentKey: root.documentKey,
            code, count,
          })).catch(() => {});
      });
    }, 1000);
  }

  // Network iframe identity is minted by the extension, not supplied by the page.
  function absoluteFrameUrl(value) {
    if (value === "about:blank" || value === "about:srcdoc") return true;
    try { const parsed = new URL(value); return ["http:", "https:"].includes(parsed.protocol) &&
      !!parsed.hostname && !parsed.username && !parsed.password; } catch (_) { return false; }
  }

  // Network messages only ever enter the top document with its current key.
  browser.runtime.onMessage.addListener((message, sender) => {
    if (!FRAME && sender?.id === browser.runtime.id && message?.schemaVersion === 1 &&
        message.event === "discovery_frame_retired" && Number.isSafeInteger(message.documentKey) &&
        Number.isSafeInteger(message.frameId) && message.frameId > 0 &&
        Number.isSafeInteger(message.frameKey) && message.frameKey > 0 && absoluteFrameUrl(message.frameUrl) &&
        Object.keys(message).sort().join(",") === "documentKey,event,frameId,frameKey,frameUrl,schemaVersion") {
      return ensureDocument().then(root => {
        if (!root || root.documentKey !== message.documentKey) return false;
        return browser.runtime.sendNativeMessage("nira.media.detector", { schemaVersion: 1,
          event: "media_frame", pageUrl: root.pageUrl, documentKey: root.documentKey,
          frameUrl: message.frameUrl, frameId: message.frameId, frameKey: message.frameKey, active: false });
      }).catch(() => false);
    }
    if (sender?.id === browser.runtime.id && message?.schemaVersion === 1 &&
        message.event === "discovery_rescan" && ["root_ready", "history", "parent_ready"].includes(message.kind) &&
        Object.keys(message).sort().join(",") === "event,kind,schemaVersion") {
      if (message.kind === "history" || FRAME) {
        if (message.kind === "history") note("spa_navigation");
        navigationEpoch++; cancelNativeQueue(); context = null; registering = null;
        seen.clear(); jobs.length = 0; pendingRoots.clear();
        scriptVersions = new WeakSet(); structuredVersions = new WeakSet();
        scheduleScan();
      }
      return Promise.resolve(true);
    }
    if (FRAME || !sender || sender.id !== browser.runtime.id || !message ||
        message.schemaVersion !== 1 || message.event !== "media_candidate" ||
        message.source !== "network" || typeof message.url !== "string" ||
        typeof message.isPrivate !== "boolean" ||
        !Number.isSafeInteger(message.documentKey) ||
        typeof message.pageUrl !== "string" ||
        ("isLive" in message && typeof message.isLive !== "boolean") ||
        ("durationSeconds" in message && (!Number.isSafeInteger(message.durationSeconds) ||
          message.durationSeconds < 1 || message.durationSeconds > 604800)) ||
        ("isLive" in message && message.isLive && "durationSeconds" in message) ||
        ("sizeBytes" in message && (!Number.isSafeInteger(message.sizeBytes) ||
          message.sizeBytes <= 0 || message.sizeBytes > 1000000000000)) ||
        ("groupId" in message && (typeof message.groupId !== "string" ||
          !/^[a-z0-9_-]{1,80}$/i.test(message.groupId))) ||
        ("quality" in message && (typeof message.quality !== "string" ||
          !/^\d{2,4}p$/.test(message.quality))) ||
        ("width" in message && (!Number.isSafeInteger(message.width) ||
          message.width < 1 || message.width > 16384)) ||
        ("height" in message && (!Number.isSafeInteger(message.height) ||
          message.height < 1 || message.height > 8640)) ||
        ("frameRate" in message && (typeof message.frameRate !== "number" ||
          !Number.isFinite(message.frameRate) || message.frameRate <= 0 || message.frameRate > 240)) ||
        ("variantCount" in message && (!Number.isSafeInteger(message.variantCount) ||
          message.variantCount < 1 || message.variantCount > 32)) ||
        ("bitrateKind" in message && !["average","peak","declared"].includes(message.bitrateKind)) ||
        ("bitrate" in message && (!Number.isSafeInteger(message.bitrate) ||
          message.bitrate <= 0 || message.bitrate > 1000000000)) ||
        ("codecs" in message && (typeof message.codecs !== "string" ||
          message.codecs.length > 160 || /[\u0000-\u001f\u007f]/.test(message.codecs))) ||
        ("provenance" in message && !["observed", "derived"].includes(message.provenance)) ||
        ("verification" in message && !["manifest_observed", "manifest_verified",
          "stream_verified", "expired", "auth_error", "unplayable"].includes(message.verification)) ||
        ("httpStatus" in message && (!Number.isSafeInteger(message.httpStatus) ||
          message.httpStatus < 100 || message.httpStatus > 599)) ||
        ("requestSequence" in message && (!Number.isSafeInteger(message.requestSequence) ||
          message.requestSequence < 1)) ||
        (("frameKey" in message) !== ("frameUrl" in message)) ||
        ("frameKey" in message && (!Number.isSafeInteger(message.frameKey) ||
          message.frameKey < 1 || typeof message.frameUrl !== "string" ||
          !absoluteFrameUrl(message.frameUrl))) ||
        Object.keys(message).some(key => !["schemaVersion", "event", "source", "url",
          "isPrivate", "documentKey", "pageUrl", "contentType", "sizeBytes",
          "durationSeconds", "isLive",
          "groupId", "quality", "bitrate", "codecs", "width", "height",
          "frameRate", "variantCount", "bitrateKind", "provenance",
          "verification", "httpStatus", "requestSequence",
          "frameUrl", "frameKey"].includes(key)) ||
        message.pageUrl !== location.href) return Promise.resolve(false);
    return ensureDocument().then(root => {
      if (!root || root.documentKey !== message.documentKey ||
          root.pageUrl !== message.pageUrl) return false;
      // Network-only URLs have no verified owner/title in the DOM.
      // Preserve the exact candidate without copying document.title to it.
      return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", message))
        .then(value => value === true).catch(() => false);
    });
  });

  function absolute(value) {
    if (typeof value !== "string" || value.length > 8192 ||
        /[\u0000-\u001f\u007f]/.test(value)) return null;
    try {
      const parsed = new URL(value, document.baseURI || location.href);
      return (parsed.protocol === "http:" || parsed.protocol === "https:") &&
        parsed.hostname && !parsed.username && !parsed.password &&
        parsed.href.length <= 8192 ? parsed.href : null;
    } catch (_) { return null; }
  }
  function groupFor(element) {
    if (!element || typeof element !== "object") return null;
    if (!groups.has(element)) groups.set(element, "g" + (++nextGroup));
    return groups.get(element);
  }
  // Decode presentation text only; never decode, modify or cache signed media URLs.
  function safeTitle(value) {
    if (typeof value !== "string") return null;
    let text = value;
    if (/%[0-9a-f]{2}/i.test(text)) {
      try { text = decodeURIComponent(text); } catch (_) { /* malformed source title */ }
    }
    const named = { amp: "&", quot: '"', apos: "'", lt: "<", gt: ">",
      nbsp: " ", ndash: "\u2013", mdash: "\u2014", hellip: "\u2026" };
    text = text.replace(/&(#(?:x[0-9a-f]{1,6}|[0-9]{1,7})|amp|quot|apos|lt|gt|nbsp|ndash|mdash|hellip);/gi,
      (raw, entity) => {
        if (entity[0] !== "#") return named[entity.toLowerCase()] || raw;
        const hex = entity[1]?.toLowerCase() === "x";
        const code = Number.parseInt(entity.slice(hex ? 2 : 1), hex ? 16 : 10);
        return Number.isInteger(code) && code > 0 && code <= 0x10ffff &&
          !(code >= 0xd800 && code <= 0xdfff) ? String.fromCodePoint(code) : raw;
      });
    const clean = text.replace(/[\u0000-\u001f\u007f]/g, " ")
      .replace(/\s+/g, " ").trim();
    return clean && clean.length <= 300 &&
      (typeof TextEncoder === "undefined" || new TextEncoder().encode(clean).length <= 900) ? clean : null;
  }
  function pageTitle() {
    let title = null;
    if (document.querySelector) {
      const og = document.querySelector('meta[property="og:title"],meta[name="twitter:title"]');
      title = og && attribute(og, "content");
    }
    return safeTitle(title) || safeTitle(document.title);
  }
  // A button caption ("Play", "HD", ...) does not identify a movie.
  // This does not discard real page titles; it applies only to per-media hints.
  function mediaTitleHint(value) {
    const title = safeTitle(value);
    return title && !/^(?:play|watch|watch now|odtwórz|oglądaj|zobacz|video|film|player|hd|sd|download|pobierz)$/i.test(title)
      ? title : null;
  }
  function explicitVideoTitle(element) {
    for (const name of ["data-video-title", "data-title", "aria-label", "title"]) {
      const value = mediaTitleHint(attribute(element, name));
      if (value) return value;
    }
    // aria-labelledby can point outside the player. Accept only a short label
    // inside its own single-player card, never a heading from another tile.
    const ids = attribute(element, "aria-labelledby");
    if (!ids || ids.length > 160 || !document.getElementById ||
        !element.closest) return null;
    const card = element.closest(
      "figure,article,li,[data-video-id],[data-media-id],.video-card,.video-item,.video-tile,.video-entry,.media-card,.media-item,.video-container,.playlist-item");
    if (!card || !card.querySelectorAll || !card.contains) return null;
    const players = card.querySelectorAll("video,iframe");
    if (players.length !== 1 || (players[0] !== element &&
        !(players[0].contains && players[0].contains(element)))) return null;
    for (const id of ids.trim().split(/\s+/).slice(0, 3)) {
      if (!/^[A-Za-z][\w:.-]{0,79}$/.test(id)) continue;
      const labelled = document.getElementById(id);
      if (!labelled || labelled === element || !card.contains(labelled)) continue;
      const title = mediaTitleHint(labelled.textContent);
      if (title) return title;
    }
    return null;
  }
  // Keep the search local to a verified single-media owner. CSS presentation
  // captions (e.g. .video-title) are often siblings of the player, not h1-h4.
  // A list of cards can share the same page heading: never search outside card.
  function cardTitle(card) {
    const direct = mediaTitleHint(attribute(card, "data-video-title") ||
      attribute(card, "data-title"));
    if (direct) return direct;
    const targets = card.querySelectorAll(
      'figcaption,[itemprop="name"],[data-video-title],[data-title],.video-title,' +
      '.video-name,.media-title,.media-name,.card-title,.video-caption,' +
      'h1,h2,h3,h4');
    // Work boundedly, and fail closed on distinct same-owner captions.
    // Never treat a heading that wraps the actual player as its media title.
    const found = new Set();
    for (let i = 0; i < Math.min(targets.length, 16); i++) {
      const element = targets[i];
      if (element.querySelector && element.querySelector("video,iframe")) continue;
      const text = mediaTitleHint(attribute(element, "data-video-title") ||
        attribute(element, "data-title") || attribute(element, "title") ||
        element.textContent);
      if (text) found.add(text);
      if (found.size > 1) return null;
    }
    return found.size === 1 ? found.values().next().value : null;
  }
  // A valid local media owner is independent of whether its title is known.
  // This lets an untitled video retain its own poster without borrowing one
  // from an ancestor gallery, a sibling player, or a cross-origin iframe.
  const MEDIA_CARDS = "figure,article,li,[data-video-id],[data-media-id]," +
    ".video-card,.video-item,.video-tile,.video-entry,.media-card,.media-item," +
    ".video-container,.playlist-item";
  function mediaCardOwner(card, element, posterOnly = false) {
    if (!card || !card.querySelectorAll) return false;
    const players = card.querySelectorAll("video,iframe");
    if (players.length > 1) return false;
    if (players.length === 1) {
      const player = players[0];
      return posterOnly || element === card || element === player ||
        typeof player.contains === "function" && player.contains(element) ||
        typeof element.contains === "function" && element.contains(player);
    }
    const declarations = card.querySelectorAll(
      "[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-player-config],a[href]");
    const owners = Array.from(declarations).filter(node =>
      node.matches && (node.matches(
        "[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-player-config]") ||
        /\.(?:mp4|m4v|webm|m3u8|mpd)(?:[?#]|$)/i.test(attribute(node, "href") || "")));
    if (owners.length !== 1) return false;
    return posterOnly || owners[0] === element ||
      typeof owners[0].contains === "function" && owners[0].contains(element);
  }
  function ownedMediaCard(element, posterOnly = false) {
    if (!element || !element.parentElement) return null;
    for (let card = element, depth = 0; card && depth < 6; card = card.parentElement, depth++) {
      if (!card.matches || !card.matches(MEDIA_CARDS)) continue;
      if (mediaCardOwner(card, element, posterOnly)) return card;
    }
    return null;
  }
  // Individual title matching still requires an unambiguous local caption.
  function scopedMediaCard(element) {
    if (!element || !element.parentElement) return null;
    for (let card = element, depth = 0; card && depth < 6; card = card.parentElement, depth++) {
      if (!card.matches || !card.matches(MEDIA_CARDS) ||
          !mediaCardOwner(card, element)) continue;
      const title = cardTitle(card);
      if (title) return { title, card };
    }
    return null;
  }
  function videoTitle(element) {
    return explicitVideoTitle(element) || scopedMediaCard(element)?.title || null;
  }
  // Never use arbitrary external images as posters. The fallback requires
  // exactly one image in the verified media card; otherwise explicit markings.
  function posterImageUrl(img) {
    if (!img) return null;
    for (const name of ["data-poster", "data-thumbnail", "data-thumb",
      "data-original", "data-lazy-src", "data-src"]) {
      const value = absolute(attribute(img, name));
      if (value) return value;
    }
    // currentSrc resolves ordinary srcset; lazy srcset can exist pre-load.
    const current = absolute(img.currentSrc);
    if (current) return current;
    const set = attribute(img, "data-srcset") || attribute(img, "srcset");
    if (typeof set === "string" && set.length <= 2048) {
      for (const candidate of set.split(",").slice(0, 8)) {
        const token = candidate.trim().split(/\s+/)[0];
        // A data URI contains commas; its trailing bytes must never become
        // a fabricated relative image URL. Only accept plausible image paths.
        if (!token || /^(?:data|blob|javascript):/i.test(token) ||
            !(/[\/]/.test(token) || /\.(?:png|jpe?g|webp|avif|gif)(?:[?#]|$)/i.test(token))) continue;
        const value = absolute(token);
        if (value) return value;
      }
    }
    return absolute(img.src);
  }
  function singleCardPoster(card) {
    if (!card || typeof card.querySelectorAll !== "function") return null;
    const images = card.querySelectorAll("img");
    // A nested recommendation card belongs to itself, not to the outer player.
    const owned = Array.from(images).filter(img =>
      !img.closest || img.closest(MEDIA_CARDS) === card);
    if (owned.length !== 1) return null;
    return posterImageUrl(owned[0]);
  }
  function posterForCard(card) {
    if (!card) return null;
    // Explicit poster/thumbnail data on the one-media card outranks unrelated
    // site images and is usable even before the image element lazy-loads.
    for (const name of ["data-poster", "data-thumbnail", "data-thumb"]) {
      const value = absolute(attribute(card, name));
      if (value) return value;
    }
    const explicit = card.querySelectorAll(
      'img[itemprop="thumbnailUrl"],img[data-poster],img[data-thumbnail],' +
      'img[data-thumb],img.video-poster,img.video-thumbnail,img.poster,img.thumbnail');
    if (explicit.length > 16) return null;
    const urls = new Set();
    for (const img of explicit) {
      if (img.closest && img.closest(MEDIA_CARDS) !== card) continue;
      const url = posterImageUrl(img);
      if (url) urls.add(url);
      if (urls.size > 1) return null; // Multiple unrelated thumbnails.
    }
    if (urls.size === 1) return urls.values().next().value;
    return explicit.length ? null : singleCardPoster(card);
  }
  function posterFor(video) {
    if (!video) return null;
    const rawPoster = video.poster || attribute(video, "poster") ||
      attribute(video, "data-poster");
    const own = absolute(rawPoster);
    if (own) return own;
    // Title-independent: an untitled video still has a verified local owner.
    return posterForCard(ownedMediaCard(video));
  }
  function durationOf(video) {
    const value = video && video.duration;
    return Number.isFinite(value) && value > 0 && value <= 604800 ?
      Math.ceil(value) : null;
  }
  // Native calls are asynchronous and share the session's receiver budget.
  // Keep IPC bounded, retain a rejected candidate for one later quota window,
  // and never migrate queued work across a document/frame registration epoch.
  const nativeQueue = [];
  let nativeActive = 0, nativeTimer = null, nativeWindowAt = 0, nativeWindowCount = 0;
  function scheduleNative(ms) {
    if (nativeTimer !== null || !alive) return;
    nativeTimer = setTimeout(() => { nativeTimer = null; drainNative(); }, Math.max(1, ms));
  }
  function cancelNativeQueue() {
    if (nativeTimer !== null) clearTimeout(nativeTimer);
    nativeTimer = null;
    for (const entry of nativeQueue.splice(0)) entry.resolve(false);
  }
  function enqueueNative(payload, epoch) {
    return new Promise(resolve => {
      if (!alive || epoch !== navigationEpoch || nativeQueue.length >= 128) {
        note("queue_limit");
        note(nativeQueue.length >= 128 ? "queue_native_capacity" : "queue_native_stale");
        if (payload.title) note("queue_title_candidate_dropped");
        resolve(false); return;
      }
      nativeQueue.push({ payload, epoch, resolve, created: Date.now(), ready: 0, attempts: 0 });
      drainNative();
    });
  }
  function drainNative() {
    if (!alive) return;
    let now = Date.now();
    if (now - nativeWindowAt >= 5000) { nativeWindowAt = now; nativeWindowCount = 0; }
    while (nativeActive < 4 && nativeQueue.length) {
      const index = nativeQueue.findIndex(entry => entry.ready <= now);
      if (index < 0) { scheduleNative(Math.min(...nativeQueue.map(entry => entry.ready)) - now); return; }
      const entry = nativeQueue.splice(index, 1)[0];
      if (entry.epoch !== navigationEpoch || now - entry.created > 12500) {
        note("queue_native_stale");
        if (entry.payload.title) note("queue_title_candidate_dropped");
        entry.resolve(false); continue;
      }
      if (nativeWindowCount >= 64) { nativeQueue.unshift(entry); scheduleNative(5000 - (now - nativeWindowAt)); return; }
      nativeActive++; nativeWindowCount++;
      Promise.resolve().then(() => browser.runtime.sendNativeMessage("nira.media.detector", entry.payload))
        .then(ok => {
          if (ok === true && alive && entry.epoch === navigationEpoch) entry.resolve(true);
          else if (alive && entry.epoch === navigationEpoch && !entry.attempts && nativeQueue.length < 128) {
            entry.attempts++; entry.ready = Date.now() + 5200; nativeQueue.push(entry);
            note("queue_native_retry");
          } else {
            note("queue_native_reject");
            if (entry.payload.title) note("queue_title_candidate_dropped");
            entry.resolve(false);
          }
        }).catch(() => {
          note("queue_native_reject");
          if (entry.payload.title) note("queue_title_candidate_dropped");
          entry.resolve(false);
        }).finally(() => { nativeActive--; drainNative(); });
      now = Date.now();
    }
  }

  function submit(value, source = "page", type = null, group = null, title = null,
    titleKind = null, duration = null, poster = null, dimensions = null,
    posterKind = null) {
    const url = absolute(value);
    if (!url) return;
    const mediaPath = new URL(url).pathname.toLowerCase();
    if (/\.(?:ts|m4s|m4a|cmfv|cmfa)$/.test(mediaPath) ||
        /(?:^|\/)(?:init|initialization|segment|seg|chunk|frag)[_.-]?[a-z0-9_-]*\.(?:mp4|webm)$/.test(mediaPath)) return;
    const knownMime = typeof type === "string" &&
      /^(?:video\/(?:mp4|webm|ogg|quicktime)|application\/(?:vnd\.apple\.mpegurl|x-mpegurl|dash\+xml)|audio\/(?:x-)?mpegurl)(?:\s*;|$)/i.test(type);
    if (!knownMime && !/\.(?:mp4|m4v|webm|ogv|ogg|mov|m3u8|mpd)$/.test(mediaPath)) return;
    // Page-wide titles cannot identify one stream among several videos.
    const named = safeTitle(title);
    const kind = named ? (titleKind || "video") : null;
    const seconds = Number.isSafeInteger(duration) && duration > 0 && duration <= 604800 ?
      duration : null;
    const posterUrl = absolute(poster);
    // A later loadedmetadata event may reveal duration/title for the SAME URL.
    const key = JSON.stringify([activeUrl, url, source, type || "", group || "",
      named || "", kind || "", seconds, posterUrl || "", posterKind || "",
      dimensions?.width || null, dimensions?.height || null]);
    if (seen.has(key)) { note("duplicate"); return; }
    offers++;
    if (!named) { note("title_unknown"); note("title_no_label"); }
    else if (kind === "structured") note("title_structured");
    else note("title_individual");
    seen.set(key, true);
    // Bounded LRU: long-lived SPAs must not silently stop detecting media.
    while (seen.size > MAX_SEEN) seen.delete(seen.keys().next().value);
    const navigation = location.href;
    const afterPlay = playObserved, epoch = navigationEpoch;
    const message = { schemaVersion: 1, event: "media_candidate", source, url };
    if (type && typeof type === "string" && type.length <= 128) message.contentType = type.trim();
    if (group) message.groupId = group;
    if (named) {
      message.title = named;
      message.titleKind = kind;
    }
    if (seconds !== null) message.durationSeconds = seconds;
    if (posterUrl) {
      message.posterUrl = posterUrl;
      if (["element", "card", "player", "structured"].includes(posterKind))
        message.posterKind = posterKind;
    }
    if (Number.isSafeInteger(dimensions?.width) && dimensions.width > 0 && dimensions.width <= 16384)
      message.width = dimensions.width;
    if (Number.isSafeInteger(dimensions?.height) && dimensions.height > 0 && dimensions.height <= 8640)
      message.height = dimensions.height;
    ensureDocument().then(root => {
      if (!alive || epoch !== navigationEpoch || !root || navigation !== location.href) { seen.delete(key); return; }
      return Promise.resolve(browser.runtime.sendMessage(message)).then(validated => {
        if (!validated || validated.event !== "media_candidate" ||
            validated.url !== url || validated.source !== source ||
            validated.contentType !== message.contentType ||
            validated.title !== message.title ||
            validated.titleKind !== message.titleKind ||
            validated.durationSeconds !== message.durationSeconds ||
            validated.posterUrl !== message.posterUrl ||
            validated.posterKind !== message.posterKind ||
            validated.width !== message.width ||
            validated.height !== message.height ||
            !alive || epoch !== navigationEpoch || navigation !== location.href) {
          note("title_ipc_rejected"); context = null; seen.delete(key); return;
        }
        const bound = { ...validated, documentKey: root.documentKey,
          pageUrl: root.pageUrl };
        if (FRAME) {
          if (validated.frameUrl !== root.frameUrl ||
              validated.frameKey !== root.frameKey ||
              validated.pageUrl !== root.pageUrl ||
              validated.documentKey !== root.documentKey) {
            seen.delete(key); return;
          }
        }
        return enqueueNative(bound, epoch)
          .then(ok => {
            if (ok !== true) { seen.delete(key); note("native_rejected"); if (named) note("title_native_rejected"); }
            else { note(afterPlay ? "after_play" : "before_play"); if (FRAME) note("iframe"); }
          });
      });
    }).catch(() => { seen.delete(key); });
  }

  function inspect(video) {
    if (!(video instanceof HTMLVideoElement) || !video.isConnected || video.ended) return;
    const group = groupFor(scopedMediaCard(video)?.card || video);
    const playing = video.paused === false;
    const preferred = video.currentSrc || video.src;
    if (!preferred) note("no_source");
    let mime = attribute(video, "type");
    if (typeof video.querySelectorAll === "function") {
      for (const item of video.querySelectorAll("source")) {
        if ((item.src || attribute(item, "data-src")) === preferred && item.type) { mime = item.type; break; }
      }
    }
    const title = videoTitle(video);
    const kind = explicitVideoTitle(video) ? "player" : "video";
    const seconds = durationOf(video);
    const poster = posterFor(video);
    const posterKind = absolute(video.poster || attribute(video, "poster") ||
      attribute(video, "data-poster")) ? "element" : "card";
    if (preferred) submit(preferred, playing ? "dom" : "page", mime, group,
      title, title ? kind : null, seconds, poster,
      { width: video.videoWidth, height: video.videoHeight }, posterKind);
    if (video.src && video.src !== preferred)
      submit(video.src, "page", null, group, title, title ? kind : null, seconds,
        poster, null, posterKind);
    // Each declared source matters, even before currentSrc becomes available.
    if (typeof video.querySelectorAll === "function") {
      let n = 0;
      for (const item of video.querySelectorAll("source")) {
        if (++n > 16) break;
        const declared = item.src || attribute(item, "data-src");
        if (declared && declared !== preferred)
          submit(declared, "page", item.type || null, group,
            title, title ? kind : null, seconds, poster, null, posterKind);
      }
    }
  }

  function scanCollection(selector, callback) {
    if (!scanRoot.querySelectorAll) return;
    const elements = scanRoot.querySelectorAll(selector);
    // A changed element itself is not included in querySelectorAll().
    if (scanRoot.matches && scanRoot.matches(selector)) callback(scanRoot);
    if (elements.length) jobs.push({ elements, index: 0, callback });
  }
  function attribute(element, name) {
    return typeof element.getAttribute === "function" ? element.getAttribute(name) : null;
  }
  function scanDeclarations() {
    scanCollection("a[href],link[href]", element => {
      const href = attribute(element, "href") || element.href;
      // Links without a direct media extension cannot pass submit() with no
      // MIME hint. Avoid walking cards and allocating groups for navigation.
      if (!href || !/\.(?:mp4|m4v|webm|ogv|ogg|mov|m3u8|mpd)(?:[?#]|$)/i.test(href)) return;
      const scoped = scopedMediaCard(element);
      const posterCard = ownedMediaCard(element);
      const directTitle = mediaTitleHint(element.textContent);
      const title = directTitle || scoped?.title || null;
      submit(href, "page", null, groupFor(scoped?.card || element),
        title, title ? (directTitle ? "link" : "video") : null,
        null, posterForCard(posterCard), null, "card");
    });
    scanCollection("[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-src],[data-plyr-source],[data-file],[data-stream-url],[data-playback-url],[data-manifest-url],[data-source-url],[data-mp4],[data-m3u8],[data-stream]", element => {
      const scoped = scopedMediaCard(element);
      const posterCard = ownedMediaCard(element);
      const named = explicitVideoTitle(element) || scoped?.title || null;
      for (const name of ["data-video-url", "data-video-src", "data-hls",
        "data-mpd", "data-src", "data-plyr-source", "data-file", "data-stream-url",
        "data-playback-url", "data-manifest-url", "data-source-url", "data-mp4",
        "data-m3u8", "data-stream"]) {
        const value = attribute(element, name);
        if (value) submit(value, "page", attribute(element, "type") ||
          (name === "data-hls" || name === "data-m3u8" ? "application/vnd.apple.mpegurl" :
            name === "data-mpd" ? "application/dash+xml" : null), groupFor(scoped?.card || element),
          named, named ? (explicitVideoTitle(element) ? "player" : "video") : null,
          null, absolute(attribute(element, "data-poster") || attribute(element, "data-thumbnail")) ||
            posterForCard(posterCard), null, "card");
      }
    });
    scanCollection('meta[property^="og:video"],meta[name="twitter:player:stream"]', element => {
      const prop = (attribute(element, "property") || attribute(element, "name") || "").toLowerCase();
      if (["og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream"].includes(prop))
        submit(attribute(element, "content"), "page", (() => {
          const selector = prop.startsWith("og:") ? 'meta[property="og:video:type"]' :
            'meta[name="twitter:player:stream:content_type"]';
          return document.querySelector && attribute(document.querySelector(selector) || {}, "content");
        })());
    });
  }
  // JSON-LD/player config thumbnail: one unambiguous URL or a single
  // ImageObject. Multiple distinct options are not a verified poster choice.
  function mediaThumbnail(value) {
    let options = Array.isArray(value) ? value.slice(0, 8) : [value];
    if (Array.isArray(value) && value.length > 8) return null;
    const distinct = new Set();
    for (const item of options) {
      const raw = typeof item === "string" ? item :
        item && typeof item === "object" && !Array.isArray(item) &&
        (item["@type"] === "ImageObject" || !item["@type"]) ?
          (typeof item.url === "string" ? item.url : item.contentUrl) : null;
      const url = absolute(raw);
      if (url) distinct.add(url);
      if (distinct.size > 1) return null;
    }
    return distinct.size === 1 ? distinct.values().next().value : null;
  }
  // Weakly tied to the script element and exact listed media URLs, not a
  // title, index or host. Repeated scans cannot create a new variant group.
  function structuredGroup(script, urls) {
    if (urls.length < 2) return null;
    let cache = structuredGroups.get(script);
    if (!cache) { cache = new Map(); structuredGroups.set(script, cache); }
    const identity = JSON.stringify([...urls].sort());
    if (cache.has(identity)) return cache.get(identity);
    const id = groupFor({});
    cache.set(identity, id);
    while (cache.size > 32) cache.delete(cache.keys().next().value);
    return id;
  }
  function scanStructuredData() {
    scanCollection('script[type="application/ld+json"]', element => {
      const raw = element.textContent;
      if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) return;
      if (structuredVersions.has(element)) return;
      structuredVersions.add(element);
      let data;
      try { data = JSON.parse(raw); } catch (_) { return; }
      let nodes = 0;
      function walk(node, depth) {
        if (++nodes > 64 || depth > 5 || !node || typeof node !== "object") return;
        if (Array.isArray(node)) {
          for (const value of node.slice(0, 16)) walk(value, depth + 1);
          return;
        }
        const kind = node["@type"];
        if (kind === "VideoObject" || Array.isArray(kind) && kind.includes("VideoObject")) {
          const name = mediaTitleHint(node.name);
          const sources = [];
          if (typeof node.contentUrl === "string") sources.push(node.contentUrl);
          const encodings = Array.isArray(node.encoding) ? node.encoding.slice(0, 8) : [node.encoding];
          for (const entry of encodings)
            if (entry && typeof entry.contentUrl === "string") sources.push(entry.contentUrl);
          // Only direct, valid media URLs own this VideoObject title. embedUrl
          // is a document/player location, never assumed to be a stream URL.
          const urls = [...new Set(sources.map(absolute).filter(Boolean))].slice(0, 9);
          if (name && urls.length === 0) note("title_no_direct_source");
          const group = structuredGroup(element, urls);
          const poster = mediaThumbnail(node.thumbnailUrl) ||
            mediaThumbnail(node.thumbnail) || mediaThumbnail(node.image);
          for (const url of urls) submit(url, "page", null, group,
            name, name ? "structured" : null, null, poster, null, "structured");
        }
        for (const field of ["@graph", "video", "mainEntity", "itemListElement"])
          if (node[field]) walk(node[field], depth + 1);
      }
      walk(data, 0);
    });
  }
  // Parse only declarative JS data; never evaluate functions, expressions, or
  // arbitrary page code. This restricted subset accepts literal object keys,
  // quoted strings and JSON scalar values used by common JW Player setups.
  function parseStaticPlayerLiteral(source) {
    if (typeof source !== "string" || source.length > 24000) return null;
    let output = "", index = 0, tokens = 0;
    while (index < source.length && ++tokens <= 4096) {
      const c = source[index];
      if (/\s/.test(c)) { output += c; index++; continue; }
      if (c === ",") {
        // Skip trailing commas only outside quoted strings. Never alter a
        // signed URL or a title containing literal ",}" / ",]".
        const next = source.slice(index + 1).trimStart()[0];
        if (next !== "}" && next !== "]") output += c;
        index++;
        continue;
      }
      if ("{}[]:".includes(c)) { output += c; index++; continue; }
      if (c === '"' || c === "'") {
        const quote = c;
        let decoded = "";
        let complete = false;
        index++;
        while (index < source.length) {
          const ch = source[index++];
          if (ch === quote) { complete = true; break; }
          if (ch === "\n" || ch === "\r") return null;
          if (ch !== "\\") { decoded += ch; continue; }
          if (index >= source.length) return null;
          const escaped = source[index++];
          if (escaped === "u" || escaped === "x") {
            const length = escaped === "u" ? 4 : 2;
            const digits = source.slice(index, index + length);
            if (!new RegExp("^[0-9a-fA-F]{" + length + "}$").test(digits)) return null;
            decoded += String.fromCharCode(parseInt(digits, 16));
            index += length;
          } else if ("nrtbfv".includes(escaped)) {
            decoded += ({ n:"\n", r:"\r", t:"\t", b:"\b", f:"\f", v:"\v" })[escaped];
          } else if ("\\/'\"".includes(escaped)) decoded += escaped;
          else return null;
        }
        if (!complete) return null;
        output += JSON.stringify(decoded);
        continue;
      }
      const remaining = source.slice(index);
      const identifier = /^[A-Za-z_$][\w$]*/.exec(remaining);
      if (identifier) {
        const name = identifier[0];
        const after = source.slice(index + name.length).trimStart();
        if (after.startsWith(":")) output += JSON.stringify(name);
        else if (name === "true" || name === "false" || name === "null") output += name;
        else return null;
        index += name.length;
        continue;
      }
      const number = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/.exec(remaining);
      if (number) { output += number[0]; index += number[0].length; continue; }
      return null; // callbacks, variables, spread, template strings, etc.
    }
    if (tokens > 4096) return null;
    try { return JSON.parse(output); }
    catch (_) { return null; }
  }
  function setupLiteralAt(source, position) {
    const initial = source[position];
    if (initial !== "{" && initial !== "[") return null;
    const stack = [];
    let quote = null, escaped = false;
    for (let i = position; i < source.length && i - position < 24000; i++) {
      const c = source[i];
      if (quote) {
        if (escaped) escaped = false;
        else if (c === "\\") escaped = true;
        else if (c === quote) quote = null;
        continue;
      }
      if (c === '"' || c === "'") { quote = c; continue; }
      if (c === "{" || c === "[") {
        if (stack.length >= 12) return null;
        stack.push(c);
      } else if (c === "}" || c === "]") {
        if (stack.pop() !== (c === "}" ? "{" : "[")) return null;
        if (!stack.length) return source.slice(position, i + 1);
      }
    }
    return null;
  }
  function scanPlayerConfigs() {
    // Universal bounded passive discovery, including independent playlist
    // items and alternatives for ONE item. The current title is a local hint,
    // not a document-wide property and never a media stream identity key.
    let remainingNodes = 512;
    function walk(node, depth, parentTitle = null, parentGroup = null, parentPoster = null) {
      if (depth > 8 || --remainingNodes < 0 || !node || typeof node !== "object") return;
      if (Array.isArray(node)) {
        for (const child of node.slice(0, 48)) {
          if (typeof child === "string" && parentGroup)
            submit(child, "page", null, parentGroup,
              parentTitle, parentTitle ? "video" : null, null, parentPoster,
              null, "player");
          else walk(child, depth + 1, parentTitle, parentGroup, parentPoster);
        }
        return;
      }
      // "label" is normally resolution/audio language, not a film title.
      // 720p/4K/Auto are rendition labels, even when stored as "title".
      const candidateTitle = mediaTitleHint(node.title) || mediaTitleHint(node.name);
      const ownTitle = candidateTitle &&
        !/^(?:auto|original|source|hd|sd|uhd|fhd|4k|8k|\d{3,4}p|\d+(?:[.,]\d+)?\s*(?:mbps|kbps))$/i.test(candidateTitle)
        ? candidateTitle : null;
      // Without a verified item title, a sources[] array is not proof that
      // differently named child sources are variants of a single film.
      const independent = !!(ownTitle && parentGroup && ownTitle !== parentTitle);
      const title = ownTitle || parentTitle;
      const group = (!independent && parentGroup) ||
        (["sources", "renditions", "variants", "encodings"].some(key =>
          Array.isArray(node[key])) ? groupFor(node) : null);
      const poster = mediaThumbnail(node.poster) ||
        mediaThumbnail(node.thumbnailUrl) || mediaThumbnail(node.image) ||
        (!independent ? parentPoster : null);
      const mime = typeof node.type === "string" ? node.type :
        (typeof node.mimeType === "string" ? node.mimeType : null);
      for (const key of ["file", "src", "source", "contentUrl", "videoUrl", "streamUrl",
        "playbackUrl", "hlsUrl", "dashUrl", "manifestUrl", "mediaUrl",
        "playlistUrl", "urlVideo", "masterUrl", "video_src"]) {
        if (typeof node[key] === "string")
          submit(node[key], "page", mime, group, title, title ? "video" : null,
            null, poster, null, "player");
      }
      if (typeof node.url === "string" &&
          (mime || /\.(?:mp4|m4v|webm|m3u8|mpd|mov)(?:[?#]|$)/i.test(node.url)))
        submit(node.url, "page", mime, group, title, title ? "video" : null,
          null, poster, null, "player");
      // Only alternative streams inherit their owner's title and group.
      // A playlist item must establish its own title independently.
      for (const key of Object.keys(node).slice(0, 64)) {
        if (!node[key] || typeof node[key] !== "object") continue;
        const sameMovie = ["sources", "source", "renditions", "variants", "encodings"].includes(key);
        walk(node[key], depth + 1, sameMovie ? title : null,
          sameMovie ? group : null, sameMovie ? poster : null);
      }
    }
    scanCollection("video[data-setup],video[data-config],[data-player-config],[data-setup]", element => {
      for (const name of ["data-setup", "data-config", "data-player-config"]) {
        const raw = attribute(element, name);
        if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) continue;
        try { remainingNodes = 512; walk(JSON.parse(raw), 0, videoTitle(element)); } catch (_) {}
      }
    });
    scanCollection("script", element => {
      const raw = element.textContent;
      const type = (attribute(element, "type") || "").toLowerCase();
      const id = (attribute(element, "id") || "").toLowerCase();
      if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) return;
      if (scriptVersions.has(element)) return;
      scriptVersions.add(element);
      if (type === "application/json" ||
          /^(?:__next_data__|__nuxt_data__|__data__|.*(?:player|video|media).*config.*)$/.test(id)) {
        try { remainingNodes = 512; const previousOffers = offers; walk(JSON.parse(raw), 0);
          if (offers > previousOffers) note("player_config_detected"); } catch (_) {}
      }
      // Read literal setup arguments from known player APIs only. Accept
      // static object/array data; dynamic JS falls back to anonymous URLs.
      const setup = /\b(?:jwplayer\s*\([^)\n]{0,128}\)\s*\.\s*setup|videojs\s*\([^)\n]{0,128}\)\s*\.\s*src)\s*\(\s*/gi;
      let entry, configs = 0;
      while ((entry = setup.exec(raw)) && ++configs <= 4) {
        const literal = setupLiteralAt(raw, setup.lastIndex);
        const config = literal && parseStaticPlayerLiteral(literal);
        if (config) {
          remainingNodes = 512;
          const previousOffers = offers;
          walk(config, 0);
          if (offers > previousOffers) note("player_config_detected");
        }
      }
      // Passive URL fallback: never associate adjacent JS variables or
      // unrelated playlist captions with literal sources by proximity.
      if (!/(?:jwplayer|videojs|plyr|hls\.loadSource|dashjs|shaka|clappr|artplayer|fluidPlayer|new\s+Hls)/i.test(raw) &&
          !/(?:jwplayer|videojs|plyr|hls|shaka|player)/i.test(id)) return;
      const literals = /(?:["']?\b(?:file|src|source|manifest)["']?\s*[:=]|\b(?:loadSource|load|attachSource)\s*\()\s*["']([^"'\s<>]{1,2048})["']/gi;
      let match, n = 0;
      while ((match = literals.exec(raw)) && ++n <= 24)
        submit(match[1].replace(/\\\//g, "/"));
      const dashLiteral = /\binitialize\s*\(\s*[A-Za-z_$][\w.$]{0,79}\s*,\s*["']([^"'\s<>]{1,2048})["']/g;
      if (/dashjs/i.test(raw)) while ((match = dashLiteral.exec(raw)) && ++n <= 24)
        submit(match[1].replace(/\\\//g, "/"));
    });
  }
  function enqueueRoot(root) {
    if (!root || !alive || root.isConnected === false) return;
    // A burst can contain thousands of callbacks for a single parent. Before
    // enforcing capacity, coalesce already-covered roots rather than reporting
    // false overflows or repeating identical DOM scans.
    if (pendingRoots.has(root)) return;
    // One scheduled full rescan already guarantees eventual coverage after a
    // real overflow. Further enqueue attempts cannot add useful work.
    if (overflowRoot) return;
    for (const queued of pendingRoots) {
      if (queued && typeof queued.contains === "function" && queued.contains(root)) return;
    }
    // A newly queued ancestor supersedes descendants, but not unrelated
    // shadow roots (Node.contains handles shadow boundaries correctly).
    if (typeof root.contains === "function") {
      for (const queued of pendingRoots)
        if (root.contains(queued)) pendingRoots.delete(queued);
    }
    if (pendingRoots.size >= MAX_ROOTS) {
      overflowRoot = true;
      note("queue_limit"); note("queue_dom_roots");
      return;
    }
    pendingRoots.add(root);
  }
  function collect(root) {
    scanRoot = root;
    note("scan_root");
    inspectFrame(root);
    scanCollection("video", inspect);
    scanDeclarations(); scanStructuredData(); scanPlayerConfigs();
    if (document.createTreeWalker) {
      const walker = document.createTreeWalker(root, 1);
      jobs.push({ walker, callback(element) {
        inspectFrame(element);
        if (element.shadowRoot) {
          observeRoot(element.shadowRoot);
          enqueueRoot(element.shadowRoot);
        }
      } });
      if (root.shadowRoot) { observeRoot(root.shadowRoot); enqueueRoot(root.shadowRoot); }
    }
  }
  function pump() {
    scanTimer = null;
    if (!alive) return;
    resetNavigation();
    const start = Date.now();
    let processed = 0;
    while (processed < MAX_ITEMS && Date.now() - start < SLICE_MS) {
      if (!jobs.length) {
        const next = pendingRoots.values().next();
        if (next.done) {
          if (overflowRoot) { overflowRoot = false; enqueueRoot(document); continue; }
          break;
        }
        pendingRoots.delete(next.value);
        collect(next.value);
      }
      const job = jobs[0];
      if (!job) continue;
      const element = job.walker ? job.walker.nextNode() : job.elements[job.index++];
      if (!element) { jobs.shift(); continue; }
      if (element.isConnected !== false) job.callback(element);
      processed++;
    }
    note("scan_elements", processed);
    if (processed > 0) note("scan_slice_ms", Math.max(1, Math.min(10000, Date.now() - start)));
    if (jobs.length || pendingRoots.size || overflowRoot) {
      note("scan_yield"); scanTimer = setTimeout(pump, 32);
    } else if (!offers) note("no_candidate");
  }
  function scan() {
    resetNavigation();
    enqueueRoot(document);
    if (scanTimer === null) pump();
  }
  // A changed caption does not contain its sibling video. Reinspect only its
  // single-media card so late DOM titles can update the existing source.
  function titleMutationRoot(element) {
    const heading = element && element.closest &&
      element.closest('figcaption,[itemprop="name"],[data-video-title],[data-title],' +
        '.video-title,.video-name,.media-title,.media-name,.card-title,' +
        '.video-caption,h1,h2,h3,h4');
    if (!heading) return null;
    const card = heading.closest(
      "figure,article,li,[data-video-id],.video-card,.video-item,.media-card,.media-item,.video-container");
    if (!card || !card.querySelectorAll) return null;
    const players = card.querySelectorAll("video,iframe");
    if (players.length === 1) return card;
    if (players.length > 1) return null;
    const declarations = card.querySelectorAll(
      "[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-player-config],a[href]");
    let owners = 0;
    for (const item of declarations) {
      if (item.matches && (item.matches(
          "[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-player-config]") ||
          /\.(?:mp4|m4v|webm|m3u8|mpd)(?:[?#]|$)/i.test(attribute(item, "href") || ""))) {
        if (++owners > 1) return null;
      }
    }
    return owners === 1 ? card : null;
  }
  function posterMutationRoot(element) {
    const target = element && element.closest &&
      element.closest("img,picture,[data-poster],[data-thumbnail],[data-thumb]");
    if (!target) return null;
    return ownedMediaCard(target, true);
  }
  function scheduleScan(records) {
    // Observe the changed subtree, including source/script parent changes.
    if (Array.isArray(records) && records.length) {
      for (const record of records) {
        const target = record.target;
        const changed = target && (target.nodeType === 3 ? target.parentElement : target);
        const changedScript = changed && changed.closest && changed.closest("script");
        if (changedScript) { scriptVersions.delete(changedScript); structuredVersions.delete(changedScript); }
        const titleCard = titleMutationRoot(changed);
        if (titleCard) enqueueRoot(titleCard);
        const posterCard = posterMutationRoot(changed);
        if (posterCard) enqueueRoot(posterCard);
        if (record.type === "attributes" || record.type === "characterData") {
          const element = target.nodeType === 3 ? target.parentElement : target;
          enqueueRoot(element && element.closest && element.closest("video,script") || element);
        }
        for (const node of record.addedNodes || []) {
          const added = node.nodeType === 3 ? node.parentElement : node;
          const card = titleMutationRoot(added);
          if (card) enqueueRoot(card);
          const posterCard = posterMutationRoot(added);
          if (posterCard) enqueueRoot(posterCard);
          enqueueRoot(added);
        }
      }
      if (jobs.length > MAX_JOBS) { jobs.length = 0; overflowRoot = true; }
    } else enqueueRoot(document);
    if (scheduled || !alive) return;
    scheduled = true;
    setTimeout(() => { scheduled = false; if (scanTimer === null) pump(); }, 120);
  }
  function onMediaEvent(event) {
    if (!event.isTrusted || !(event.target instanceof HTMLVideoElement)) return;
    if (event.type === "play" || event.type === "playing") playObserved = true;
    if (event.type === "emptied") seen.clear();
    inspect(event.target);
  }
  for (const event of ["play", "playing", "loadedmetadata", "durationchange", "resize", "emptied"]) {
    document.addEventListener(event, onMediaEvent, { capture: true, passive: true });
  }
  const observer = new MutationObserver(scheduleScan);
  const observerOptions = { childList: true, subtree: true, attributes: true, characterData: true,
    attributeFilter: ["src", "srcdoc", "type", "content", "href", "title", "aria-label",
      "data-title", "data-video-title", "data-poster", "data-thumbnail", "data-thumb",
      "data-original", "data-lazy-src", "data-srcset", "srcset", "poster",
      "data-src", "data-video-url", "data-video-src",
      "data-hls", "data-mpd", "data-plyr-source", "data-playback-url", "data-manifest-url",
      "data-source-url", "data-mp4", "data-m3u8", "data-stream", "data-config",
      "data-file", "data-stream-url", "data-setup", "data-player-config"] };
  function observeRoot(root) {
    if (observedRoots.has(root)) return;
    observedRoots.add(root);
    observer.observe(root, observerOptions);
  }
  observeRoot(document);
  document.addEventListener("DOMContentLoaded", scheduleScan, { once: true });
  document.addEventListener("visibilitychange", scheduleScan, { passive: true });
  for (const event of ["pageshow", "popstate", "hashchange"])
    window.addEventListener(event, () => {
      alive = true; seen.clear(); structuredVersions = new WeakSet();
      observeRoot(document); scheduleScan();
    }, { passive: true });
  window.addEventListener("pagehide", () => {
    if (FRAME && context) Promise.resolve(browser.runtime.sendMessage({
      schemaVersion: 1, event: "frame_ended", frameKey: context.frameKey,
    })).catch(() => {});
    context = null; navigationEpoch++; registering = null;
    alive = false; cancelNativeQueue(); observer.disconnect(); observedRoots = new WeakSet();
    if (scanTimer !== null) clearTimeout(scanTimer);
    if (statsTimer !== null) clearTimeout(statsTimer);
    statsTimer = null; stats.clear();
    scanTimer = null; jobs.length = 0; pendingRoots.clear();
  }, { passive: true });
  ensureDocument();
  scan();
})();
