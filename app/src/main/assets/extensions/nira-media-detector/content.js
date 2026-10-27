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
  let overflowRoot = false;
  // Bounded slices, not a permanent cutoff at the first N nodes.
  const SLICE_MS = 8, MAX_JOBS = 64, MAX_ROOTS = 32;
  const seen = new Map(), groups = new WeakMap();
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
    seen.clear(); offers = 0; playObserved = false; stats.clear();
    if (statsTimer !== null) clearTimeout(statsTimer);
    statsTimer = null;
    jobs.length = 0; pendingRoots.clear(); pendingRoots.add(document); scriptVersions = new WeakSet();
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
        seen.clear(); jobs.length = 0; pendingRoots.clear(); scriptVersions = new WeakSet();
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
        ("sizeBytes" in message && (!Number.isSafeInteger(message.sizeBytes) ||
          message.sizeBytes <= 0 || message.sizeBytes > 1000000000000)) ||
        ("groupId" in message && (typeof message.groupId !== "string" ||
          !/^[a-z0-9_-]{1,80}$/i.test(message.groupId))) ||
        ("quality" in message && (typeof message.quality !== "string" ||
          !/^\d{2,4}p$/.test(message.quality))) ||
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
          "groupId", "quality", "bitrate", "codecs", "provenance",
          "verification", "httpStatus", "requestSequence",
          "frameUrl", "frameKey"].includes(key)) ||
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
    const rawPoster = video.poster || attribute(video, "poster") ||
      attribute(video, "data-poster");
    if (rawPoster) {
      const own = absolute(rawPoster);
      if (own) return own;
    }
    const container = video.closest && video.closest("article,figure,[class*='video'],[class*='player']");
    const image = container && container.querySelector && container.querySelector("img");
    const imageSource = image &&
      (image.currentSrc || image.src || attribute(image, "data-src"));
    return imageSource ? absolute(imageSource) : null;
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
        note("queue_limit"); resolve(false); return;
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
      if (entry.epoch !== navigationEpoch || now - entry.created > 12500) { entry.resolve(false); continue; }
      if (nativeWindowCount >= 64) { nativeQueue.unshift(entry); scheduleNative(5000 - (now - nativeWindowAt)); return; }
      nativeActive++; nativeWindowCount++;
      Promise.resolve().then(() => browser.runtime.sendNativeMessage("nira.media.detector", entry.payload))
        .then(ok => {
          if (ok === true && alive && entry.epoch === navigationEpoch) entry.resolve(true);
          else if (alive && entry.epoch === navigationEpoch && !entry.attempts && nativeQueue.length < 128) {
            entry.attempts++; entry.ready = Date.now() + 5200; nativeQueue.push(entry);
          } else entry.resolve(false);
        }).catch(() => entry.resolve(false)).finally(() => { nativeActive--; drainNative(); });
      now = Date.now();
    }
  }

  function submit(value, source = "page", type = null, group = null, title = null,
    titleKind = null, duration = null, poster = null) {
    const url = absolute(value);
    if (!url) return;
    const mediaPath = new URL(url).pathname.toLowerCase();
    if (/\.(?:ts|m4s|m4a|cmfv|cmfa)$/.test(mediaPath) ||
        /(?:^|\/)(?:init|initialization|segment|seg|chunk|frag)[_.-]?[a-z0-9_-]*\.(?:mp4|webm)$/.test(mediaPath)) return;
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
    if (seen.has(key)) { note("duplicate"); return; }
    offers++;
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
    if (posterUrl) message.posterUrl = posterUrl;
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
            !alive || epoch !== navigationEpoch || navigation !== location.href) {
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
        return enqueueNative(bound, epoch)
          .then(ok => {
            if (ok !== true) { seen.delete(key); note("native_rejected"); }
            else { note(afterPlay ? "after_play" : "before_play"); if (FRAME) note("iframe"); }
          });
      });
    }).catch(() => { seen.delete(key); });
  }

  function inspect(video) {
    if (!(video instanceof HTMLVideoElement) || !video.isConnected || video.ended) return;
    const group = groupFor(video);
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
        const declared = item.src || attribute(item, "data-src");
        if (declared && declared !== preferred)
          submit(declared, "page", item.type || null, group,
            title, title ? "video" : null, seconds, poster);
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
      if (href) submit(href, "page", null, null,
        safeTitle(element.textContent), "link");
    });
    scanCollection("[data-video-url],[data-video-src],[data-hls],[data-mpd],[data-src],[data-plyr-source],[data-file],[data-stream-url],[data-playback-url],[data-manifest-url],[data-source-url],[data-mp4],[data-m3u8],[data-stream]", element => {
      for (const name of ["data-video-url", "data-video-src", "data-hls",
        "data-mpd", "data-src", "data-plyr-source", "data-file", "data-stream-url",
        "data-playback-url", "data-manifest-url", "data-source-url", "data-mp4",
        "data-m3u8", "data-stream"]) {
        const value = attribute(element, name);
        if (value) submit(value, "page", attribute(element, "type") ||
          (name === "data-hls" || name === "data-m3u8" ? "application/vnd.apple.mpegurl" :
            name === "data-mpd" ? "application/dash+xml" : null), groupFor(element));
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
  function scanStructuredData() {
    scanCollection('script[type="application/ld+json"]', element => {
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
    let remainingNodes = 512;
    function walk(node, depth, parentTitle = null, parentGroup = null, parentPoster = null) {
      if (depth > 8 || --remainingNodes < 0 || !node || typeof node !== "object") return;
      if (Array.isArray(node)) {
        for (const child of node.slice(0, 48)) walk(child, depth + 1, parentTitle, parentGroup, parentPoster);
        return;
      }
      const title = safeTitle(node.title || node.name || node.label) || parentTitle;
      const group = parentGroup || (["sources", "renditions", "variants"].some(key =>
        Array.isArray(node[key])) ? groupFor(node) : null);
      const poster = typeof node.poster === "string" ? node.poster :
        (typeof node.thumbnailUrl === "string" ? node.thumbnailUrl : parentPoster);
      const mime = typeof node.type === "string" ? node.type :
        (typeof node.mimeType === "string" ? node.mimeType : null);
      for (const key of ["file", "src", "contentUrl", "videoUrl", "streamUrl",
        "playbackUrl", "hlsUrl", "dashUrl", "manifestUrl", "mediaUrl",
        "playlistUrl", "urlVideo", "masterUrl", "video_src"]) {
        if (typeof node[key] === "string")
          submit(node[key], "page", mime, group, title, title ? "video" : null, null, poster);
      }
      // A source's url is accepted only if it looks like a media resource;
      // submit() still validates the final URL + format.
      if (typeof node.url === "string" &&
          (mime || /\.(?:mp4|m4v|webm|m3u8|mpd|mov)(?:[?#]|$)/i.test(node.url)))
        submit(node.url, "page", mime, group, title, title ? "video" : null, null, poster);
      // JSON-only own values: framework wrappers vary, source field names do not.
      // Budget is per script/config so an unrelated earlier script cannot starve it.
      for (const key of Object.keys(node).slice(0, 64)) {
        if (node[key] && typeof node[key] === "object") walk(node[key], depth + 1, title,
          ["sources", "source", "renditions", "variants"].includes(key) ? group : null,
          ["sources", "source", "renditions", "variants"].includes(key) ? poster : null);
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
      // In known player setup code read *literal strings only*. Never run
      // arbitrary site code or fetch a manifest merely for detection.
      if (!/(?:jwplayer|videojs|plyr|hls\.loadSource|dashjs|shaka|clappr|artplayer|fluidPlayer|new\s+Hls)/i.test(raw) &&
          !/(?:jwplayer|videojs|plyr|hls|shaka|player)/i.test(id)) return;
      const literals = /(?:["']?\b(?:file|src|source|manifest)["']?\s*[:=]|\b(?:loadSource|load|attachSource)\s*\()\s*["']([^"'\s<>]{1,2048})["']/gi;
      let match, n = 0;
      while ((match = literals.exec(raw)) && ++n <= 24)
        submit(match[1].replace(/\\\//g, "/"));
      // dash.js initialize(videoElement, literalManifest, autoplay). Read only
      // the literal second argument; never invoke a site's player or code.
      const dashLiteral = /\binitialize\s*\(\s*[A-Za-z_$][\w.$]{0,79}\s*,\s*["']([^"'\s<>]{1,2048})["']/g;
      if (/dashjs/i.test(raw)) while ((match = dashLiteral.exec(raw)) && ++n <= 24)
        submit(match[1].replace(/\\\//g, "/"));
    });
  }
  function enqueueRoot(root) {
    if (!root || !alive) return;
    if (pendingRoots.size >= MAX_ROOTS) { overflowRoot = true; note("queue_limit"); return; }
    pendingRoots.add(root);
  }
  function collect(root) {
    scanRoot = root;
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
    if (jobs.length || pendingRoots.size || overflowRoot) {
      note("scan_yield"); scanTimer = setTimeout(pump, 32);
    } else if (!offers) note("no_candidate");
  }
  function scan() {
    resetNavigation();
    enqueueRoot(document);
    if (scanTimer === null) pump();
  }
  function scheduleScan(records) {
    // Observe the changed subtree, including source/script parent changes.
    if (Array.isArray(records) && records.length) {
      for (const record of records) {
        const target = record.target;
        const changed = target && (target.nodeType === 3 ? target.parentElement : target);
        const changedScript = changed && changed.closest && changed.closest("script");
        if (changedScript) scriptVersions.delete(changedScript);
        if (record.type === "attributes" || record.type === "characterData") {
          const element = target.nodeType === 3 ? target.parentElement : target;
          enqueueRoot(element && element.closest && element.closest("video,script") || element);
        }
        for (const node of record.addedNodes || [])
          enqueueRoot(node.nodeType === 3 ? node.parentElement : node);
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
  for (const event of ["play", "playing", "loadedmetadata", "emptied"]) {
    document.addEventListener(event, onMediaEvent, { capture: true, passive: true });
  }
  const observer = new MutationObserver(scheduleScan);
  const observerOptions = { childList: true, subtree: true, attributes: true, characterData: true,
    attributeFilter: ["src", "srcdoc", "type", "content", "href", "title", "aria-label",
      "data-title", "data-video-title", "data-src", "data-video-url", "data-video-src",
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
      alive = true; seen.clear(); observeRoot(document); scheduleScan();
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
