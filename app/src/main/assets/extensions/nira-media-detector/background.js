"use strict";

// Stage 3.2: passive Gecko WebExtension response inspection. No body access, blocking,
// cookie/authorization capture, URL logging, persistent storage or additional fetches.
const MAX_URL_BYTES = 8192;
const MAX_MIME_BYTES = 128;
const MAX_SEEN = 128;
const RECENT_MS = 20000;
const seen = new Map();
const routedRate = new Map();
const MAX_ROUTED_PER_5S = 24;
const byteLength = value => new TextEncoder().encode(value).length;
const safeUrl = value => {
  if (typeof value !== "string" || byteLength(value) > MAX_URL_BYTES ||
      /[\s\u0000-\u001f\u007f]/u.test(value)) return false;
  try {
    const url = new URL(value);
    return (url.protocol === "https:" || url.protocol === "http:") &&
      !!url.hostname && !url.username && !url.password;
  } catch (_) { return false; }
};
const safeMime = value => typeof value === "string" && byteLength(value) <= MAX_MIME_BYTES &&
  /^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+(?:\s*;[\x20-\x7e]*)?$/i.test(value);
const pathOf = value => { try { return new URL(value).pathname.toLowerCase(); } catch (_) { return ""; } };
const segmentPath = path => {
  const name = path.split("/").pop() || "";
  return /\.(?:ts|m4s|m4a|cmfv|cmfa)(?:$)/i.test(name) ||
    /^(?:init|initialization|segment|seg|chunk|frag)[_.-]?[a-z0-9_-]*\.(?:mp4|webm)$/i.test(name);
};
function classify(url, contentType) {
  if (!safeUrl(url)) return null;
  const path = pathOf(url);
  if (segmentPath(path)) return "segment";
  const ext = path.split("/").pop().split(".").pop();
  const mime = contentType ? contentType.split(";")[0].trim().toLowerCase() : "";
  // MIME wins except for known segment paths. Explicit conflicting nonmedia MIME
  // must not turn an HTML error page into a video.
  if (mime === "application/vnd.apple.mpegurl" || mime === "application/x-mpegurl" ||
      mime === "audio/mpegurl" || mime === "audio/x-mpegurl") return "HLS";
  if (mime === "application/dash+xml") return "DASH";
  if (mime === "video/mp4") return "MP4";
  if (mime === "video/webm") return "WEBM";
  if (mime === "video/ogg" || mime === "application/ogg") return "OGG";
  if (mime === "video/quicktime") return "MOV";
  if (mime && mime !== "application/octet-stream" && mime !== "binary/octet-stream") return null;
  if (ext === "m3u8") return "HLS";
  if (ext === "mpd") return "DASH";
  if (ext === "mp4" || ext === "m4v") return "MP4";
  if (ext === "webm") return "WEBM";
  if (ext === "ogv" || ext === "ogg") return "OGG";
  if (ext === "mov") return "MOV";
  return null;
}
function validDom(message, sender) {
  if (!sender || sender.id !== browser.runtime.id || !sender.tab || sender.frameId !== 0 ||
      !message || typeof message !== "object" || Array.isArray(message)) return false;
  const keys = Object.keys(message).sort().join(",");
  if (message.schemaVersion !== 1 || message.event !== "media_candidate" ||
      message.source !== "dom" ||
      !(keys === "event,schemaVersion,source,url" ||
        keys === "contentType,event,schemaVersion,source,url") ||
      !safeUrl(message.url) || byteLength(JSON.stringify(message)) > 16384) return false;
  return !("contentType" in message) || safeMime(message.contentType);
}
browser.runtime.onMessage.addListener((message, sender) =>
  Promise.resolve(validDom(message, sender) ? { ...message } : null));

let probeSent = false;
// Native background callback has NO EngineSession. Send only anonymous diagnostics.
const anonymous = (event, isPrivate, count) => {
  if (typeof isPrivate !== "boolean") return;
  const message = { schemaVersion: 1, event, isPrivate };
  if (count !== undefined) message.count = count;
  try { Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", message))
    .catch(() => {}); } catch (_) {}
};
const segmentBuckets = { regular: 0, private: 0 };
let segmentFlushPending = false;
function flushSegments() {
  segmentFlushPending = false;
  for (const [key, isPrivate] of [["regular", false], ["private", true]]) {
    const count = segmentBuckets[key];
    segmentBuckets[key] = 0;
    if (count) anonymous("network_segments", isPrivate, count);
  }
}
function skipSegment(isPrivate) {
  if (typeof isPrivate !== "boolean") return;
  const bucket = isPrivate ? "private" : "regular";
  segmentBuckets[bucket] = Math.min(100000, segmentBuckets[bucket] + 1);
  if (!segmentFlushPending) {
    segmentFlushPending = true;
    setTimeout(flushSegments, 2000);
  }
}
function onHeaders(details) {
  if (!details || typeof details !== "object") return;
  if (["image", "imageset", "ping", "beacon", "script", "stylesheet", "font", "csp_report"].includes(details.type)) return;
  if (!probeSent && typeof details.url === "string" &&
      details.url.startsWith("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4")) {
    probeSent = true;
    try { Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector",
      { schemaVersion: 1, event: "network_probe" })).catch(() => {}); } catch (_) {}
  }
  if (typeof details.statusCode === "number" && (details.statusCode < 200 || details.statusCode >= 300)) return;
  if (!safeUrl(details.url) || typeof details.incognito !== "boolean") return;
  let contentType = null;
  if (Array.isArray(details.responseHeaders)) {
    const header = details.responseHeaders.find(h => h && typeof h.name === "string" &&
      h.name.toLowerCase() === "content-type");
    if (header && safeMime(header.value)) contentType = header.value;
    else if (header) return; // malformed Content-Type is not a trustworthy hint
  }
  const format = classify(details.url, contentType);
  if (format === "segment") { skipSegment(details.incognito); return; }
  if (!format) return;
  const tabId = Number.isInteger(details.tabId) && details.tabId >= 0 ? details.tabId : -1;
  const key = tabId + ":" + String(details.incognito) + ":" + details.url + ":" + format;
  const now = Date.now();
  // Bound cross-tab native message rate independently of memory deduplication.
  const rateKey = tabId + ":" + String(details.incognito);
  const slot = routedRate.get(rateKey);
  if (!slot || now - slot.since >= 5000) routedRate.set(rateKey, { since: now, count: 1 });
  else if (slot.count >= MAX_ROUTED_PER_5S) return;
  else slot.count++;
  while (routedRate.size > 64) routedRate.delete(routedRate.keys().next().value);
  if (seen.has(key) && now - seen.get(key) < RECENT_MS) return;
  seen.delete(key);
  seen.set(key, now);
  while (seen.size > MAX_SEEN) seen.delete(seen.keys().next().value);
  const message = {
    schemaVersion: 1, event: "media_candidate", source: "network", url: details.url,
    isPrivate: details.incognito
  };
  if (contentType) message.contentType = contentType;
  // tabs.sendMessage routes only to a specified Gecko tab / frame. The content
  // script's native message is still independently bound to the real EngineSession.
  // If unavailable or not acknowledged: never guess a tab from active UI state.
  if (tabId < 0 || !browser.tabs || typeof browser.tabs.sendMessage !== "function") {
    anonymous("network_unassigned", details.incognito);
    return;
  }
  try {
    Promise.resolve(browser.tabs.sendMessage(tabId, message, { frameId: 0 }))
      .then(accepted => { if (accepted !== true) anonymous("network_unassigned", details.incognito); })
      .catch(() => anonymous("network_unassigned", details.incognito));
  } catch (_) { anonymous("network_unassigned", details.incognito); }
}
if (browser.webRequest && browser.webRequest.onHeadersReceived) {
  // Response headers available only with responseHeaders. Never use blocking.
  browser.webRequest.onHeadersReceived.addListener(onHeaders,
    { urls: ["http://*/*", "https://*/*"] }, ["responseHeaders"]);
}
