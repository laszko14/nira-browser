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
const MAX_MANIFEST_BYTES = 512 * 1024;
const MAX_MANIFEST_QUEUE = 8;
const MAX_MANIFEST_ACTIVE = 2;
const MANIFEST_TTL_MS = 120000;
const manifestCache = new Map();
const manifestInflight = new Map();
const manifestGroups = new Map();
const manifestLineage = new Map();
// Identity is ONLY for grouping; never mutate or rebuild the exact playback URL.
const volatileParams = new Set(["s", "e", "t", "token", "sig", "signature",
  "expires", "expiry", "exp", "hdnts", "hdntl", "policy", "key-pair-id",
  "x-amz-algorithm", "x-amz-credential", "x-amz-date", "x-amz-expires",
  "x-amz-signedheaders", "x-amz-signature", "x-amz-security-token"]);
function manifestIdentity(url) {
  try {
    const parsed = new URL(url);
    const stable = [...parsed.searchParams.entries()]
      .filter(([key]) => !volatileParams.has(key.toLowerCase()))
      .sort(([a, av], [b, bv]) => a.localeCompare(b) || av.localeCompare(bv));
    return parsed.origin + parsed.pathname + "?" + JSON.stringify(stable);
  } catch (_) { return url; }
}
function streamGroupKey(tabId, documentKey, isPrivate, frameKey, url) {
  return [tabId, documentKey, isPrivate, frameKey || 0, manifestIdentity(url)].join(":");
}
const manifestQueue = [];
let manifestActive = 0;
let nextManifestGroup = 0;
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
const validTitle = value => typeof value === "string" &&
  value.length > 0 && value.length <= 300 && byteLength(value) <= 900 &&
  !/[\u0000-\u001f\u007f]/.test(value);
const validSeconds = value => Number.isSafeInteger(value) && value > 0 && value <= 604800;
const MAX_MEDIA_BYTES = 1000000000000;
// Read existing response metadata only. Range total is NOT a body download.
function contentSize(details, format) {
  if (!["MP4", "WEBM", "OGG", "MOV"].includes(format) ||
      !Array.isArray(details.responseHeaders)) return null;
  const get = name => {
    const h = details.responseHeaders.find(h => h && typeof h.name === "string" &&
      h.name.toLowerCase() === name);
    return h && typeof h.value === "string" ? h.value.trim() : null;
  };
  const encoding = get("content-encoding");
  if (encoding && encoding.toLowerCase() !== "identity") return null;
  let value = null;
  if (details.statusCode === 200) value = get("content-length");
  else if (details.statusCode === 206) {
    const range = get("content-range");
    const match = range && /^bytes\s+\d+-\d+\/(\d+)$/i.exec(range);
    if (match) value = match[1];
  }
  if (!value || !/^\d{1,13}$/.test(value)) return null;
  const bytes = Number(value);
  return Number.isSafeInteger(bytes) && bytes > 0 && bytes <= MAX_MEDIA_BYTES ? bytes : null;
}

function startDocument(message, sender) {
  if (!isMainSender(sender) || !message || message.schemaVersion !== 1 ||
      message.event !== "document_started" ||
      Object.keys(message).sort().join(",") !== "event,pageUrl,schemaVersion" ||
      !safeUrl(message.pageUrl)) return null;
  // sender.url comes from Gecko and is not supplied by the page message.
  if (typeof sender.url === "string" && sender.url !== message.pageUrl) return null;
  const documentKey = ++nextDocumentKey;
  cancelManifestTasks(sender.tab.id);
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
  if ("title" in message) permitted.push("title");
  if ("titleKind" in message) permitted.push("titleKind");
  if ("durationSeconds" in message) permitted.push("durationSeconds");
  if ("posterUrl" in message) permitted.push("posterUrl");
  if (keys !== permitted.sort().join(",") ||
      ("contentType" in message && !safeMime(message.contentType)) ||
      ("groupId" in message && !validGroup(message.groupId)) ||
      (("title" in message) !== ("titleKind" in message)) ||
      ("title" in message && !validTitle(message.title)) ||
      ("titleKind" in message && !["video", "link", "page"].includes(message.titleKind)) ||
      ("durationSeconds" in message && !validSeconds(message.durationSeconds)) ||
      ("posterUrl" in message && !safeUrl(message.posterUrl))) return false;
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
    cancelManifestTasks(id);
    documents.delete(id);
    for (const [key, value] of frames) if (value.tabId === id) frames.delete(key);
  });
}

function manifestKey(task) {
  return task.tabId + ":" + task.documentKey + ":" + String(task.isPrivate) + ":" + task.url;
}
function cancelManifestTasks(tabId) {
  for (let index = manifestQueue.length - 1; index >= 0; index--)
    if (manifestQueue[index].tabId === tabId) manifestQueue.splice(index, 1);
  for (const [key, value] of manifestInflight)
    if (value.tabId === tabId) { value.controller?.abort(); manifestInflight.delete(key); }
  for (const key of manifestCache.keys())
    if (key.startsWith(tabId + ":")) manifestCache.delete(key);
  for (const key of manifestGroups.keys())
    if (key.startsWith(tabId + ":")) manifestGroups.delete(key);
  for (const key of manifestLineage.keys())
    if (key.startsWith(tabId + ":")) manifestLineage.delete(key);
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
  const sizeBytes = contentSize(details, format);
  const dedupKey = tabId + ":" + doc.documentKey + ":" + String(details.incognito) +
    ":" + details.url + ":" + format;
  const previously = seen.get(dedupKey);
  if (previously && now - previously.at < RECENT_MS &&
      (sizeBytes === null || previously.sizeBytes === sizeBytes)) return;
  seen.delete(dedupKey);
  seen.set(dedupKey, { at: now, sizeBytes });
  while (seen.size > MAX_SEEN) seen.delete(seen.keys().next().value);
  const message = {
    schemaVersion: 1, event: "media_candidate", source: "network", url: details.url,
    isPrivate: details.incognito, documentKey: doc.documentKey, pageUrl: doc.pageUrl,
    provenance: "observed"
  };
  if (requestFrame) {
    message.frameKey = requestFrame.frameKey;
    message.frameUrl = requestFrame.frameUrl;
  }
  if (format === "HLS" || format === "DASH") {
    const groupKey = streamGroupKey(tabId, doc.documentKey, details.incognito,
      requestFrame?.frameKey, details.url);
    if (!manifestGroups.has(groupKey)) {
      manifestGroups.set(groupKey, manifestLineage.get(groupKey) ||
        "manifest-" + doc.documentKey + "-" + (++nextManifestGroup));
    }
    message.groupId = manifestGroups.get(groupKey);
    while (manifestGroups.size > 128) manifestGroups.delete(manifestGroups.keys().next().value);
  }
  if (contentType) message.contentType = contentType;
  if (sizeBytes !== null) message.sizeBytes = sizeBytes;
  if (!browser.tabs || typeof browser.tabs.sendMessage !== "function") {
    anonymous("network_unassigned", details.incognito);
    return;
  }
  try {
    Promise.resolve(browser.tabs.sendMessage(tabId, message, { frameId: 0 }))
      .then(accepted => {
        if (accepted !== true) anonymous("network_unassigned", details.incognito);
        else if (format === "HLS" || format === "DASH") queueManifest({
          url: details.url, format, tabId, documentKey: doc.documentKey,
          pageUrl: doc.pageUrl, isPrivate: details.incognito, groupId: message.groupId,
          frameKey: requestFrame?.frameKey, frameUrl: requestFrame?.frameUrl,
        });
      })
      .catch(() => anonymous("network_unassigned", details.incognito));
  } catch (_) { anonymous("network_unassigned", details.incognito); }
}

function queueManifest(task) {
  if (typeof fetch !== "function" || !globalThis.NiraManifestParser) return;
  const now = Date.now();
  const key = manifestKey(task);
  const cached = manifestCache.get(key);
  if (cached && now - cached < MANIFEST_TTL_MS) return;
  if (manifestInflight.has(key) || manifestQueue.some(item => manifestKey(item) === key)) return;
  if (manifestQueue.length >= MAX_MANIFEST_QUEUE) return;
  manifestQueue.push(task);
  drainManifests();
}
function drainManifests() {
  while (manifestActive < MAX_MANIFEST_ACTIVE && manifestQueue.length) {
    const task = manifestQueue.shift();
    const current = documents.get(task.tabId);
    if (!current || current.documentKey !== task.documentKey || current.pageUrl !== task.pageUrl) continue;
    manifestActive++;
    inspectManifest(task).catch(() => {}).finally(() => {
      manifestActive--;
      drainManifests();
    });
  }
}
async function inspectManifest(task) {
  const controller = typeof AbortController === "function" ? new AbortController() : null;
  const key = manifestKey(task);
  manifestInflight.set(key, { tabId: task.tabId, controller });
  const timeout = setTimeout(() => { if (controller) controller.abort(); }, 4000);
  try {
    const response = await fetch(task.url, {
      method: "GET", credentials: "omit", redirect: "follow", cache: "no-store",
      referrer: task.pageUrl, signal: controller ? controller.signal : undefined,
    });
    if (!response || !response.ok) return;
    const finalUrl = safeUrl(response.url) ? response.url : task.url;
    const responseType = response.headers && response.headers.get("content-type");
    if (classify(finalUrl, responseType) !== task.format) return;
    const declared = response.headers && response.headers.get("content-length");
    if (declared && (!/^\d+$/.test(declared) || Number(declared) > MAX_MANIFEST_BYTES)) return;
    if (!response.body || typeof response.body.getReader !== "function" ||
        typeof TextDecoder !== "function") return;
    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8", { fatal: false });
    let bytes = 0, text = "";
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      if (!chunk.value || !Number.isSafeInteger(chunk.value.byteLength) ||
          chunk.value.byteLength < 0) { await reader.cancel(); return; }
      bytes += chunk.value.byteLength;
      if (bytes > MAX_MANIFEST_BYTES) { await reader.cancel(); return; }
      text += decoder.decode(chunk.value, { stream: true });
      if (text.length > MAX_MANIFEST_BYTES) { await reader.cancel(); return; }
    }
    text += decoder.decode();
    const parsed = task.format === "HLS" ?
      NiraManifestParser.parseHls(text, finalUrl, 8) :
      NiraManifestParser.parseDash(text, finalUrl, 8);
    const current = documents.get(task.tabId);
    if (!current || current.documentKey !== task.documentKey || current.pageUrl !== task.pageUrl) return;
    // DASH representations can refer to initialization/segment templates, not
    // playable MP4 files. Keep the observed MPD; never export fake direct videos.
    for (const variant of (task.format === "HLS" ? parsed.variants || [] : [])) {
      if (!safeUrl(variant.url)) continue;
      const lineageKey = streamGroupKey(task.tabId, task.documentKey, task.isPrivate,
        task.frameKey, variant.url);
      manifestLineage.set(lineageKey, task.groupId);
      while (manifestLineage.size > 128) manifestLineage.delete(manifestLineage.keys().next().value);
      const message = { schemaVersion: 1, event: "media_candidate", source: "network",
        provenance: "derived", url: variant.url, isPrivate: task.isPrivate,
        documentKey: task.documentKey, pageUrl: task.pageUrl,
        groupId: task.groupId, contentType: "application/vnd.apple.mpegurl" };
      if (task.frameKey && task.frameUrl) {
        message.frameKey = task.frameKey;
        message.frameUrl = task.frameUrl;
      }
      if (Number.isSafeInteger(variant.height) && variant.height > 0 && variant.height <= 8640)
        message.quality = variant.height + "p";
      if (Number.isSafeInteger(variant.bitrate) && variant.bitrate > 0 && variant.bitrate <= 1000000000)
        message.bitrate = variant.bitrate;
      if (typeof variant.codecs === "string" && variant.codecs.length <= 160)
        message.codecs = variant.codecs;
      await browser.tabs.sendMessage(task.tabId, message, { frameId: 0 });
    }
    manifestCache.set(key, Date.now());
    while (manifestCache.size > 32) manifestCache.delete(manifestCache.keys().next().value);
  } finally { clearTimeout(timeout); manifestInflight.delete(key); }
}
if (browser.webRequest && browser.webRequest.onHeadersReceived) {
  browser.webRequest.onHeadersReceived.addListener(onHeaders,
    { urls: ["http://*/*", "https://*/*"] }, ["responseHeaders"]);
}
