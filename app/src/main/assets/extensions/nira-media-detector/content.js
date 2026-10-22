"use strict";

(() => {
  // Only the extension's background can mint root/frame correlation keys.
  // No video playback, network probe, script execution or persistent URL storage.
  const FRAME = typeof window.top !== "undefined" && window !== window.top;
  const MAX_ITEMS = 64, MAX_SEEN = 128, MAX_SCRIPTS = 32, MAX_SCRIPT_CHARS = 20000;
  let activeUrl = location.href, context = null, registering = null;
  let scheduled = false, lastDeepScan = 0;
  const seen = new Map(), groups = new WeakMap();
  let nextGroup = 0;

  function resetNavigation() {
    if (activeUrl === location.href) return;
    activeUrl = location.href;
    context = null;
    registering = null;
    seen.clear();
    lastDeepScan = 0;
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
        ![7, 8, 9].includes(Object.keys(message).length) ||
        ("sizeBytes" in message && (!Number.isSafeInteger(message.sizeBytes) ||
          message.sizeBytes <= 0 || message.sizeBytes > 1000000000000)) ||
        message.pageUrl !== location.href) return Promise.resolve(false);
    return ensureDocument().then(root => {
      if (!root || root.documentKey !== message.documentKey ||
          root.pageUrl !== message.pageUrl) return false;
      // Page title is a hint, never proof of ownership of a network URL.
      const title = pageTitle();
      const payload = title ? { ...message, title, titleKind: "page" } : message;
      return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", payload))
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
  function safeTitle(value) {
    if (typeof value !== "string") return null;
    const clean = value.replace(/[\u0000-\u001f\u007f]/g, " ")
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
  function videoTitle(video) {
    for (const name of ["data-video-title", "data-title", "aria-label", "title"]) {
      const value = safeTitle(attribute(video, name));
      if (value) return value;
    }
    if (typeof video.closest === "function") {
      const parent = video.closest("article,section,figure,[data-video-id],.video-container");
      const heading = parent && parent.querySelector &&
        parent.querySelector("h1,h2,h3,[itemprop='name']");
      const value = safeTitle(heading && heading.textContent);
      if (value) return value;
    }
    return null;
  }
  function posterFor(video) {
    if (!video) return null;
    const own = absolute(video.poster || attribute(video, "poster") ||
      attribute(video, "data-poster") || "");
    if (own) return own;
    const container = video.closest && video.closest("article,figure,[class*='video'],[class*='player']");
    const image = container && container.querySelector && container.querySelector("img");
    return image ? absolute(image.currentSrc || image.src || attribute(image, "data-src") || "") : null;
  }
  function durationOf(video) {
    const value = video && video.duration;
    return Number.isFinite(value) && value > 0 && value <= 604800 ?
      Math.ceil(value) : null;
  }
  function submit(value, source = "page", type = null, group = null, title = null,
    titleKind = null, duration = null, poster = null) {
    const url = absolute(value);
    if (!url || seen.size >= MAX_SEEN) return;
    const mediaPath = new URL(url).pathname.toLowerCase();
    const knownMime = typeof type === "string" &&
      /^(?:video\/(?:mp4|webm|ogg|quicktime)|application\/(?:vnd\.apple\.mpegurl|x-mpegurl|dash\+xml)|audio\/(?:x-)?mpegurl)(?:\s*;|$)/i.test(type);
    if (!knownMime && !/\.(?:mp4|m4v|webm|ogv|ogg|mov|m3u8|mpd)$/.test(mediaPath)) return;
    const named = safeTitle(title) || pageTitle();
    const kind = named ? (safeTitle(title) ? (titleKind || "video") : "page") : null;
    const seconds = Number.isSafeInteger(duration) && duration > 0 && duration <= 604800 ?
      duration : null;
    const posterUrl = absolute(poster);
    // A later loadedmetadata event may reveal duration/title for the SAME URL.
    const key = JSON.stringify([activeUrl, url, source, type || "", group || "",
      named || "", kind || "", seconds, posterUrl || ""]);
    if (seen.has(key)) return;
    seen.set(key, true);
    const navigation = location.href;
    const message = { schemaVersion: 1, event: "media_candidate", source, url };
    if (type && typeof type === "string" && type.length <= 128) message.contentType = type.trim();
    if (group) message.groupId = group;
    if (named) {
      message.title = named;
      message.titleKind = kind;
    }
    if (seconds !== null) message.durationSeconds = seconds;
    if (posterUrl) message.posterUrl = posterUrl;
    ensureDocument().then(root => {
      if (!root || navigation !== location.href) { seen.delete(key); return; }
      return Promise.resolve(browser.runtime.sendMessage(message)).then(validated => {
        if (!validated || validated.event !== "media_candidate" ||
            validated.url !== url || validated.source !== source ||
            validated.contentType !== message.contentType ||
            validated.title !== message.title ||
            validated.titleKind !== message.titleKind ||
            validated.durationSeconds !== message.durationSeconds ||
            validated.posterUrl !== message.posterUrl ||
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
    const title = videoTitle(video);
    const seconds = durationOf(video);
    const poster = posterFor(video);
    if (preferred) submit(preferred, playing ? "dom" : "page", mime, group,
      title, title ? "video" : null, seconds, poster);
    if (video.src && video.src !== preferred)
      submit(video.src, "page", null, group, title, title ? "video" : null, seconds, poster);
    // Each declared source matters, even before currentSrc becomes available.
    if (typeof video.querySelectorAll === "function") {
      let n = 0;
      for (const item of video.querySelectorAll("source")) {
        if (++n > 16) break;
        if (item.src && item.src !== preferred)
          submit(item.src, "page", item.type || null, group,
            title, title ? "video" : null, seconds, poster);
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
      if (href) submit(href, "page", null, null,
        safeTitle(element.textContent), "link");
    });
    scanCollection("[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-src],[data-plyr-source],[data-file],[data-stream-url]", 80, element => {
      for (const name of ["data-video-url", "data-video-src", "data-hls",
        "data-mpd", "data-src", "data-plyr-source", "data-file", "data-stream-url"]) {
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
          const name = safeTitle(node.name);
          if (typeof node.contentUrl === "string")
            submit(node.contentUrl, "page", null, null, name, "video", null,
              typeof node.thumbnailUrl === "string" ? node.thumbnailUrl : null);
          const encodings = Array.isArray(node.encoding) ? node.encoding.slice(0, 8) : [node.encoding];
          for (const entry of encodings)
            if (entry && typeof entry.contentUrl === "string")
              submit(entry.contentUrl, "page", null, null, name, "video", null,
                typeof node.thumbnailUrl === "string" ? node.thumbnailUrl : null);
        }
        for (const field of ["@graph", "video", "mainEntity", "itemListElement"])
          if (node[field]) walk(node[field], depth + 1);
      }
      walk(data, 0);
    });
  }
  function scanPlayerConfigs() {
    // Universal, bounded passive discovery: declarative configs from common
    // players and framework hydration state. Never evaluate site JavaScript.
    let remainingNodes = 192;
    function walk(node, depth, parentTitle = null) {
      if (depth > 6 || --remainingNodes < 0 || !node || typeof node !== "object") return;
      if (Array.isArray(node)) {
        for (const child of node.slice(0, 24)) walk(child, depth + 1, parentTitle);
        return;
      }
      const title = safeTitle(node.title || node.name || node.label) || parentTitle;
      const poster = typeof node.poster === "string" ? node.poster :
        (typeof node.thumbnailUrl === "string" ? node.thumbnailUrl : null);
      const mime = typeof node.type === "string" ? node.type :
        (typeof node.mimeType === "string" ? node.mimeType : null);
      for (const key of ["file", "src", "contentUrl", "videoUrl", "streamUrl",
        "playbackUrl", "hlsUrl", "dashUrl", "manifestUrl"]) {
        if (typeof node[key] === "string")
          submit(node[key], "page", mime, null, title, title ? "video" : null, null, poster);
      }
      // A source's url is accepted only if it looks like a media resource;
      // submit() still validates the final URL + format.
      if (typeof node.url === "string" &&
          (mime || /\.(?:mp4|m4v|webm|m3u8|mpd|mov)(?:[?#]|$)/i.test(node.url)))
        submit(node.url, "page", mime, null, title, title ? "video" : null, null, poster);
      for (const key of ["sources", "source", "playlist", "tracks", "videos",
        "video", "media", "files", "items", "streams", "props", "pageProps",
        "player", "config", "data", "assets"]) {
        if (node[key]) walk(node[key], depth + 1, title);
      }
    }
    scanCollection("video[data-setup],video[data-config],[data-player-config],[data-setup]", 48, element => {
      for (const name of ["data-setup", "data-config", "data-player-config"]) {
        const raw = attribute(element, name);
        if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) continue;
        try { walk(JSON.parse(raw), 0, videoTitle(element)); } catch (_) {}
      }
    });
    scanCollection("script", MAX_SCRIPTS, element => {
      const raw = element.textContent;
      const type = (attribute(element, "type") || "").toLowerCase();
      const id = (attribute(element, "id") || "").toLowerCase();
      if (typeof raw !== "string" || raw.length > MAX_SCRIPT_CHARS) return;
      if (type === "application/json" ||
          /^(?:__next_data__|__nuxt_data__|__data__|.*(?:player|video|media).*config.*)$/.test(id)) {
        try { walk(JSON.parse(raw), 0); } catch (_) {}
      }
      // In known player setup code read *literal strings only*. Never run
      // arbitrary site code or fetch a manifest merely for detection.
      if (!/(?:jwplayer|videojs|plyr|hls\.loadSource|dashjs|shaka|clappr|artplayer|fluidPlayer|new\s+Hls)/i.test(raw) &&
          !/(?:jwplayer|videojs|plyr|hls|shaka|player)/i.test(id)) return;
      const literals = /(?:\bfile|\bsrc|\bsource|\bmanifest|\bhls\.loadSource)\s*(?:\(|:|=)\s*["'](https?:\/\/[^"'\s<>]{1,2048})["']/gi;
      let match, n = 0;
      while ((match = literals.exec(raw)) && ++n <= 24)
        submit(match[1].replace(/\\\//g, "/"));
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
    // Revisit static metadata as SPA/player nodes change, without exhausting
    // a one-time 80-scan budget on long-lived pages.
    if (Date.now() - start <= 30 && start - lastDeepScan >= 900) {
      lastDeepScan = start;
      scanDeclarations();
      scanStructuredData();
      scanPlayerConfigs();
    }
  }
  function scheduleScan() {
    // DOM/SPA/player mutations invalidate the deep-scan throttle immediately.
    // Keep debounce to avoid repeated work on rapidly changing pages, while
    // ensuring newly injected JSON-LD and player configs are not skipped.
    lastDeepScan = 0;
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
    attributeFilter: ["src", "type", "content", "href", "title", "aria-label",
      "data-title", "data-video-title", "data-src", "data-video-url",
      "data-video-src", "data-hls", "data-mpd", "data-plyr-source",
       "data-file", "data-stream-url", "data-setup", "data-player-config"] });
  document.addEventListener("DOMContentLoaded", scheduleScan, { once: true });
  document.addEventListener("visibilitychange", scheduleScan, { passive: true });
  for (const event of ["pageshow", "popstate", "hashchange"])
    window.addEventListener(event, scheduleScan, { passive: true });
  ensureDocument();
  scan();
})();