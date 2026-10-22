"use strict";

(() => {
  // Only the extension's background can mint root/frame correlation keys.
  // No video playback, network probe, script execution or persistent URL storage.
  const FRAME = typeof window.top !== "undefined" && window !== window.top;
  const MAX_ITEMS = 48, MAX_SEEN = 128, MAX_SCRIPTS = 12, MAX_SCRIPT_CHARS = 12000;
  let activeUrl = location.href, context = null, registering = null;
  let scheduled = false, scans = 0;
  const seen = new Map(), groups = new WeakMap();
  let nextGroup = 0;

  function resetNavigation() {
    if (activeUrl === location.href) return;
    activeUrl = location.href;
    context = null;
    registering = null;
    seen.clear();
    scans = 0;
  }
  function ensureDocument() {
    resetNavigation();
    if (context) return Promise.resolve(context);
    if (registering) return registering;
    const navigation = location.href;
    const request = FRAME ?
      { schemaVersion: 1, event: "frame_started", frameUrl: navigation } :
      { schemaVersion: 1, event: "document_started", pageUrl: navigation };
    const promise = Promise.resolve().then(() => browser.runtime.sendMessage(request))
      .then(reply => {
        if (!reply || navigation !== location.href ||
            !Number.isSafeInteger(reply.documentKey) || reply.documentKey <= 0) return null;
        if (FRAME) {
          if (!Number.isSafeInteger(reply.frameKey) || reply.frameKey <= 0 ||
              reply.frameUrl !== navigation || typeof reply.pageUrl !== "string") return null;
          return { documentKey: reply.documentKey, pageUrl: reply.pageUrl,
            frameUrl: navigation, frameKey: reply.frameKey };
        }
        return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", {
          schemaVersion: 1, event: "media_document",
          pageUrl: navigation, documentKey: reply.documentKey
        })).then(accepted => accepted === true && location.href === navigation ?
          { documentKey: reply.documentKey, pageUrl: navigation } : null);
      }).catch(() => null);
    registering = promise.then(result => {
      registering = null;
      if (result && navigation === location.href) context = result;
      return result;
    });
    return registering;
  }

  // Network messages only ever enter the top document with its current key.
  browser.runtime.onMessage.addListener((message, sender) => {
    if (FRAME || !sender || sender.id !== browser.runtime.id || !message ||
        message.schemaVersion !== 1 || message.event !== "media_candidate" ||
        message.source !== "network" || typeof message.url !== "string" ||
        typeof message.isPrivate !== "boolean" ||
        !Number.isSafeInteger(message.documentKey) ||
        typeof message.pageUrl !== "string" ||
        ![7, 8].includes(Object.keys(message).length) ||
        message.pageUrl !== location.href) return Promise.resolve(false);
    return ensureDocument().then(root => {
      if (!root || root.documentKey !== message.documentKey ||
          root.pageUrl !== message.pageUrl) return false;
      return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", message))
        .then(value => value === true).catch(() => false);
    });
  });

  function absolute(value) {
    if (typeof value !== "string" || value.length > 8192 ||
        /[\u0000-\u001f\u007f]/.test(value)) return null;
    try {
      const parsed = new URL(value, location.href);
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
  function submit(value, source = "page", type = null, group = null) {
    const url = absolute(value);
    if (!url || seen.size >= MAX_SEEN) return;
    const mediaPath = new URL(url).pathname.toLowerCase();
    const knownMime = typeof type === "string" &&
      /^(?:video\/(?:mp4|webm|ogg|quicktime)|application\/(?:vnd\.apple\.mpegurl|x-mpegurl|dash\+xml)|audio\/(?:x-)?mpegurl)(?:\s*;|$)/i.test(type);
    if (!knownMime && !/\.(?:mp4|m4v|webm|ogv|ogg|mov|m3u8|mpd)$/.test(mediaPath)) return;
    const key = JSON.stringify([activeUrl, url, source, type || "", group || ""]);
    if (seen.has(key)) return;
    seen.set(key, true);
    const navigation = location.href;
    const message = { schemaVersion: 1, event: "media_candidate", source, url };
    if (type && typeof type === "string" && type.length <= 128) message.contentType = type.trim();
    if (group) message.groupId = group;
    ensureDocument().then(root => {
      if (!root || navigation !== location.href) { seen.delete(key); return; }
      return Promise.resolve(browser.runtime.sendMessage(message)).then(validated => {
        if (!validated || validated.event !== "media_candidate" ||
            validated.url !== url || validated.source !== source ||
            validated.contentType !== message.contentType ||
            navigation !== location.href) {
          context = null; seen.delete(key); return;
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
        return browser.runtime.sendNativeMessage("nira.media.detector", bound)
          .then(ok => { if (ok !== true) seen.delete(key); });
      });
    }).catch(() => { seen.delete(key); });
  }

  function inspect(video) {
    if (!(video instanceof HTMLVideoElement) || !video.isConnected || video.ended) return;
    const group = groupFor(video);
    const playing = video.paused === false;
    const preferred = video.currentSrc || video.src;
    let mime = null;
    if (typeof video.querySelectorAll === "function") {
      for (const item of video.querySelectorAll("source")) {
        if (item.src === preferred && item.type) { mime = item.type; break; }
      }
    }
    if (preferred) submit(preferred, playing ? "dom" : "page", mime, group);
    if (video.src && video.src !== preferred) submit(video.src, "page", null, group);
    // Each declared source matters, even before currentSrc becomes available.
    if (typeof video.querySelectorAll === "function") {
      let n = 0;
      for (const item of video.querySelectorAll("source")) {
        if (++n > 16) break;
        if (item.src && item.src !== preferred)
          submit(item.src, "page", item.type || null, group);
      }
    }
  }

  function scanCollection(selector, max, callback) {
    if (!document.querySelectorAll) return;
    let count = 0;
    for (const element of document.querySelectorAll(selector)) {
      if (++count > max) break;
      callback(element);
    }
  }
  function attribute(element, name) {
    return typeof element.getAttribute === "function" ? element.getAttribute(name) : null;
  }
  function scanDeclarations() {
    scanCollection("a[href],link[href]", 48, element => {
      const href = attribute(element, "href") || element.href;
      if (href) submit(href);
    });
    scanCollection("[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-src],[data-plyr-source]", 48, element => {
      for (const name of ["data-video-url", "data-video-src", "data-hls",
        "data-mpd", "data-src", "data-plyr-source"]) {
        const value = attribute(element, name);
        if (value) submit(value, "page", null, groupFor(element));
      }
    });
    scanCollection('meta[property^="og:video"],meta[name="twitter:player:stream"]', 16, element => {
      const prop = (attribute(element, "property") || attribute(element, "name") || "").toLowerCase();
      if (["og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream"].includes(prop))
        submit(attribute(element, "content"));
    });
  }
  function scanStructuredData() {
    scanCollection('script[type="application/ld+json"]', MAX_SCRIPTS, element => {
      const raw = element.textContent;
      if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) return;
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
          if (typeof node.contentUrl === "string") submit(node.contentUrl);
          const encodings = Array.isArray(node.encoding) ? node.encoding.slice(0, 8) : [node.encoding];
          for (const entry of encodings)
            if (entry && typeof entry.contentUrl === "string") submit(entry.contentUrl);
        }
        for (const field of ["@graph", "video", "mainEntity", "itemListElement"])
          if (node[field]) walk(node[field], depth + 1);
      }
      walk(data, 0);
    });
  }
  function scanPlayerConfigs() {
    // Only bounded, literal, player-identifiable configuration; never eval or
    // scan all arbitrary JavaScript for generic URL-shaped strings.
    scanCollection("script", MAX_SCRIPTS, element => {
      const raw = element.textContent, type = (attribute(element, "type") || "").toLowerCase();
      const id = (attribute(element, "id") || "").toLowerCase();
      if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) return;
      const known = /jwplayer\s*\(|videojs\s*\(|new\s+Plyr\s*\(|new\s+Hls\s*\(|hls\.loadSource\s*\(/i.test(raw) ||
        /jwplayer|videojs|plyr|hlsjs|hls-config/.test(id);
      if (!known) return;
      if (type === "application/json") {
        try {
          const data = JSON.parse(raw);
          if (data && typeof data === "object") {
            if (typeof data.file === "string") submit(data.file);
            if (typeof data.src === "string") submit(data.src);
            const items = Array.isArray(data.sources) ? data.sources.slice(0, 16) : [];
            for (const item of items) {
              if (item && typeof item === "object")
                submit(item.file || item.src, "page", item.type || null);
            }
          }
        } catch (_) { /* Ignore invalid static config. */ }
      }
      // Literal-only fields in known player setup and Hls.loadSource; reject
      // dynamic expressions instead of executing any site's JS.
      const literal = /(?:\bfile|\bsrc|\bhls\.loadSource)\s*(?:\(|:|=)\s*["'](https?:\/\/[^"'\s<>]{1,2048})["']/gi;
      let match, n = 0;
      while ((match = literal.exec(raw)) && ++n <= 16) submit(match[1]);
    });
  }
  function scan() {
    resetNavigation();
    const start = Date.now();
    let count = 0;
    for (const video of document.querySelectorAll("video")) {
      if (++count > MAX_ITEMS || Date.now() - start > 30) break;
      inspect(video);
    }
    if (Date.now() - start <= 30 && scans++ < 80) {
      scanDeclarations();
      scanStructuredData();
      scanPlayerConfigs();
    }
  }
  function scheduleScan() {
    if (scheduled) return;
    scheduled = true;
    setTimeout(() => { scheduled = false; scan(); }, 240);
  }
  function onMediaEvent(event) {
    if (!event.isTrusted || !(event.target instanceof HTMLVideoElement)) return;
    if (event.type === "emptied") seen.clear();
    inspect(event.target);
  }
  for (const event of ["play", "playing", "loadedmetadata", "emptied"]) {
    document.addEventListener(event, onMediaEvent, { capture: true, passive: true });
  }
  const observer = new MutationObserver(scheduleScan);
  observer.observe(document, { childList: true, subtree: true, attributes: true,
    attributeFilter: ["src", "type", "content", "href", "data-src", "data-video-url",
      "data-video-src", "data-hls", "data-mpd", "data-plyr-source"] });
  document.addEventListener("DOMContentLoaded", scheduleScan, { once: true });
  document.addEventListener("visibilitychange", scheduleScan, { passive: true });
  for (const event of ["pageshow", "popstate", "hashchange"])
    window.addEventListener(event, scheduleScan, { passive: true });
  ensureDocument();
  scan();
})();