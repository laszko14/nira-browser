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
// Stage 3.3: per-document routing. The document key is minted by this
// privileged extension, never taken from site-controlled identifiers.
const documents = new Map();
const requests = new Map();
const frames = new Map();
const MAX_FRAMES = 256;
let nextFrameKey = 0;
const MAX_DOCUMENTS = 128;
const MAX_REQUESTS = 512;
let nextDocumentKey = 0;
const isExtensionSender = sender => !!sender && sender.id === browser.runtime.id &&
  sender.tab && Number.isInteger(sender.tab.id) && sender.tab.id >= 0 &&
  Number.isInteger(sender.frameId) && sender.frameId >= 0;
const isMainSender = sender => isExtensionSender(sender) && sender.frameId === 0;
const frameId = sender => sender.tab.id + ":" + sender.frameId;
const validGroup = value => typeof value === "string" && /^[a-z0-9_-]{1,64}$/i.test(value);

function startDocument(message, sender) {
  if (!isMainSender(sender) || !message || message.schemaVersion !== 1 ||
      message.event !== "document_started" ||
      Object.keys(message).sort().join(",") !== "event,pageUrl,schemaVersion" ||
      !safeUrl(message.pageUrl)) return null;
  // sender.url comes from Gecko and is not supplied by the page message.
  if (typeof sender.url === "string" && sender.url !== message.pageUrl) return null;
  const documentKey = ++nextDocumentKey;
  documents.delete(sender.tab.id);
  for (const [id, frame] of frames) if (frame.tabId === sender.tab.id) frames.delete(id);
  documents.set(sender.tab.id, { documentKey, pageUrl: message.pageUrl });
  while (documents.size > MAX_DOCUMENTS) documents.delete(documents.keys().next().value);
  return { documentKey };
}
function startFrame(message, sender) {
  if (!isExtensionSender(sender) || sender.frameId === 0 || !message ||
      message.schemaVersion !== 1 || message.event !== "frame_started" ||
      Object.keys(message).sort().join(",") !== "event,frameUrl,schemaVersion" ||
      !safeUrl(message.frameUrl) || sender.url !== message.frameUrl) return null;
  const root = documents.get(sender.tab.id);
  if (!root) return null; // Never assign a subframe to the selected/active tab.
  const frame = { tabId: sender.tab.id, frameId: sender.frameId, frameUrl: message.frameUrl,
    pageUrl: root.pageUrl, documentKey: root.documentKey, frameKey: ++nextFrameKey };
  frames.set(frameId(sender), frame);
  while (frames.size > MAX_FRAMES) frames.delete(frames.keys().next().value);
  return { pageUrl: root.pageUrl, documentKey: root.documentKey,
    frameUrl: frame.frameUrl, frameKey: frame.frameKey };
}
function validDom(message, sender) {
  if (!isExtensionSender(sender) || !message || typeof message !== "object" ||
      Array.isArray(message)) return false;
  const keys = Object.keys(message).sort().join(",");
  if (message.schemaVersion !== 1 || message.event !== "media_candidate" ||
      !["dom", "page"].includes(message.source) ||
      !safeUrl(message.url) || byteLength(JSON.stringify(message)) > 16384) return false;
  const permitted = ["event", "schemaVersion", "source", "url"];
  if ("contentType" in message) permitted.push("contentType");
  if ("groupId" in message) permitted.push("groupId");
  if (keys !== permitted.sort().join(",") ||
      ("contentType" in message && !safeMime(message.contentType)) ||
      ("groupId" in message && !validGroup(message.groupId))) return false;
  if (sender.frameId === 0) return true;
  const frame = frames.get(frameId(sender));
  const root = documents.get(sender.tab.id);
  return !!frame && !!root && sender.url === frame.frameUrl &&
    frame.pageUrl === root.pageUrl && frame.documentKey === root.documentKey;

}
browser.runtime.onMessage.addListener((message, sender) => {
  if (message && message.event === "document_started") return Promise.resolve(startDocument(message, sender));
  if (message && message.event === "frame_started") return Promise.resolve(startFrame(message, sender));
  if (!validDom(message, sender)) return Promise.resolve(null);
  const reply = { ...message };
  if ("groupId" in reply) reply.groupId = sender.frameId === 0 ?
    "t" + documents.get(sender.tab.id)?.documentKey + "-" + reply.groupId :
    "f" + frames.get(frameId(sender)).frameKey + "-" + reply.groupId;
  if (sender.frameId > 0) {
    const frame = frames.get(frameId(sender));
    reply.frameKey = frame.frameKey;
    reply.frameUrl = frame.frameUrl;
    reply.documentKey = frame.documentKey;
    reply.pageUrl = frame.pageUrl;
  }
  return Promise.resolve(reply);
});
if (browser.tabs && browser.tabs.onRemoved && typeof browser.tabs.onRemoved.addListener === "function") {
  browser.tabs.onRemoved.addListener(id => {
    documents.delete(id);
    for (const [key, value] of frames) if (value.tabId === id) frames.delete(key);
  });
}

// onBeforeRequest is passive metadata capture only. No request bodies, URLs,
// headers or cookies are retained here. A delayed response from a previous
// document carries the old key and is rejected by the new content script.
function requestKey(details) {
  if (!details || !Number.isInteger(details.tabId) || details.tabId < 0 ||
      !["string", "number"].includes(typeof details.requestId)) return null;
  return details.tabId + ":" + String(details.requestId);
}
function onBefore(details) {
  const key = requestKey(details);
  if (!key) return;
  const doc = documents.get(details.tabId);
  if (!doc) return;
  let frame = null;
  if (Number.isInteger(details.frameId) && details.frameId > 0) {
    frame = frames.get(details.tabId + ":" + details.frameId);
    if (!frame || frame.documentKey !== doc.documentKey ||
        (typeof details.documentUrl === "string" && details.documentUrl !== frame.frameUrl))
      return;
  } else if (typeof details.documentUrl === "string" && details.documentUrl !== doc.pageUrl) return;
  requests.delete(key);
  requests.set(key, { documentKey: doc.documentKey,
    frameKey: frame ? frame.frameKey : null, frameUrl: frame ? frame.frameUrl : null,
    frameId: frame ? frame.frameId : 0 });
  while (requests.size > MAX_REQUESTS) requests.delete(requests.keys().next().value);
}
if (browser.webRequest && browser.webRequest.onBeforeRequest) {
  browser.webRequest.onBeforeRequest.addListener(onBefore,
    { urls: ["http://*/*", "https://*/*"] });
}

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
  const key = requestKey(details);
  const requestDocumentKey = key ? requests.get(key) : null;
  if (key) requests.delete(key);
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
    else if (header) return;
  }
  const format = classify(details.url, contentType);
  if (format === "segment") { skipSegment(details.incognito); return; }
  if (!format) return;
  const tabId = Number.isInteger(details.tabId) && details.tabId >= 0 ? details.tabId : -1;
  const doc = documents.get(tabId);
  // No guessing: request must have started in a known document, and
  // response initiator must still match that document's page URL.
  const requestFrame = requestDocumentKey && requestDocumentKey.frameKey !== null ?
    frames.get(tabId + ":" + requestDocumentKey.frameId) : null;
  if (!doc || !requestDocumentKey || doc.documentKey !== requestDocumentKey.documentKey ||
      (requestDocumentKey.frameKey !== null &&
        (!requestFrame || requestFrame.frameKey !== requestDocumentKey.frameKey ||
          requestFrame.frameUrl !== requestDocumentKey.frameUrl)) ||
      (typeof details.documentUrl === "string" &&
        details.documentUrl !== (requestFrame ? requestFrame.frameUrl : doc.pageUrl))) {
    anonymous("network_unassigned", details.incognito);
    return;
  }
  const now = Date.now();
  const rateKey = tabId + ":" + String(details.incognito);
  const slot = routedRate.get(rateKey);
  if (!slot || now - slot.since >= 5000) routedRate.set(rateKey, { since: now, count: 1 });
  else if (slot.count >= MAX_ROUTED_PER_5S) return;
  else slot.count++;
  while (routedRate.size > 64) routedRate.delete(routedRate.keys().next().value);
  const dedupKey = tabId + ":" + doc.documentKey + ":" + String(details.incognito) +
    ":" + details.url + ":" + format;
  if (seen.has(dedupKey) && now - seen.get(dedupKey) < RECENT_MS) return;
  seen.delete(dedupKey);
  seen.set(dedupKey, now);
  while (seen.size > MAX_SEEN) seen.delete(seen.keys().next().value);
  const message = {
    schemaVersion: 1, event: "media_candidate", source: "network", url: details.url,
    isPrivate: details.incognito, documentKey: doc.documentKey, pageUrl: doc.pageUrl
  };
  if (contentType) message.contentType = contentType;
  if (!browser.tabs || typeof browser.tabs.sendMessage !== "function") {
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
  browser.webRequest.onHeadersReceived.addListener(onHeaders,
    { urls: ["http://*/*", "https://*/*"] }, ["responseHeaders"]);
}
