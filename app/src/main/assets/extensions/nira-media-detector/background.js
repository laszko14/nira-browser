"use strict";

// Stage 3.2: passive Gecko WebExtension response inspection. No body access, blocking,
// cookie/authorization capture, URL logging or persistent storage. Bounded manifest probes below.
const MAX_URL_BYTES = 8192;
const MAX_MIME_BYTES = 128;
const MAX_SEEN = 128;
const RECENT_MS = 20000;
const seen = new Map();
const routedRate = new Map();
const MAX_ROUTED_PER_5S = 96;
const MAX_MANIFEST_BYTES = 512 * 1024;
const MAX_MANIFEST_QUEUE = 8;
const MAX_MANIFEST_ACTIVE = 2;
const MANIFEST_TTL_MS = 120000;
const manifestCache = new Map();
const manifestInflight = new Map();
const manifestGroups = new Map();
const manifestLineage = new Map();
const manifestVariantRates = new Map();
// Never erase arbitrary manifest query parameters to infer media identity.
// For fully formed AWS SigV4, only signature timestamp, expiry and signature
// are treated as rotating authentication data. Credential, signed headers,
// security token and all custom query parameters remain significant.
// Original playback and fetch URLs are never reconstructed or mutated here.
function manifestIdentity(url) {
  try {
    const parsed = new URL(url);
    const rawQuery = parsed.search.slice(1);
    if (!rawQuery) return parsed.href;
    const parts = rawQuery.split("&");
    const fields = new Map();
    for (const part of parts) {
      const split = part.indexOf("=");
      if (split <= 0) return parsed.href;
      const name = part.slice(0, split).toLowerCase();
      if (name.includes("%")) return parsed.href; // ambiguous encoded names
      if (name.startsWith("x-amz-") && fields.has(name)) return parsed.href;
      if (name.startsWith("x-amz-")) fields.set(name, part.slice(split + 1));
    }
    if (fields.get("x-amz-algorithm") !== "AWS4-HMAC-SHA256" ||
        !fields.get("x-amz-credential") || !fields.get("x-amz-signedheaders") ||
        !fields.get("x-amz-signature")) return parsed.href;
    const rotating = new Set(["x-amz-date", "x-amz-expires", "x-amz-signature"]);
    const stable = parts.filter(part =>
      !rotating.has(part.slice(0, part.indexOf("=")).toLowerCase())).sort();
    return "sigv4:" + parsed.origin + parsed.pathname + "?" + JSON.stringify(stable);
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
let nextRequestSequence = 0;
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

// Gecko 153+ supplies these UUIDs in trusted sender/request metadata. Never
// accept an identity supplied in the page's message body or infer it from URL.
const geckoId = value => typeof value === "string" && /^[a-f0-9{}-]{16,64}$/i.test(value) ? value : null;
const opaqueFrame = value => value === "about:blank" || value === "about:srcdoc";
const sameRoute = (a, b) => {
  try { const x = new URL(a), y = new URL(b); x.hash = y.hash = ""; return x.href === y.href; }
  catch (_) { return a === b; }
};
const earlyRequests = new Map(), pendingFrameStarts = new Map();
const EARLY_LIMIT = 128, EARLY_TTL = 2500;
let earlyTimer = null;
function purgeEarly() {
  earlyTimer = null;
  const now = Date.now();
  for (const [key, entry] of earlyRequests)
    if (now - entry.at >= EARLY_TTL) {
      earlyRequests.delete(key); discovery("early_request_expired", entry.isPrivate);
    }
  for (const [key, entry] of pendingFrameStarts)
    if (now - entry.at >= EARLY_TTL) pendingFrameStarts.delete(key);
  if (earlyRequests.size || pendingFrameStarts.size) earlyTimer = setTimeout(purgeEarly, EARLY_TTL);
}
function clearEarly(tabId, frameId) {
  for (const [key, entry] of pendingFrameStarts)
    if (entry.tabId === tabId && (frameId === undefined || entry.frameId === frameId)) pendingFrameStarts.delete(key);
  for (const [key, entry] of earlyRequests)
    if (entry.tabId === tabId && (frameId === undefined || entry.frameId === frameId)) earlyRequests.delete(key);
}
function captureEarly(details) {
  const key = requestKey(details), id = geckoId(details.documentId);
  if (!key || !id || !Number.isInteger(details.frameId) || details.frameId < 0 ||
      !["string"].includes(typeof details.documentUrl) ||
      !(safeUrl(details.documentUrl) || opaqueFrame(details.documentUrl)) ||
      ["main_frame", "sub_frame", "speculative"].includes(details.type) ||
      typeof details.incognito !== "boolean") return false;
  if (earlyRequests.has(key)) return true; // redirect: retain original epoch/time
  if (earlyRequests.size >= EARLY_LIMIT) {
    const first = earlyRequests.keys().next().value;
    earlyRequests.delete(first); discovery("queue_limit", details.incognito); discovery("queue_early_evict", details.incognito);
  }
  earlyRequests.set(key, { tabId: details.tabId, frameId: details.frameId,
    geckoDocumentId: id, parentDocumentId: geckoId(details.parentDocumentId),
    documentUrl: details.documentUrl, cookieStoreId: details.cookieStoreId,
    isPrivate: details.incognito, at: Date.now(), response: null });
  discovery("early_request_seen", details.incognito);
  if (earlyTimer === null) earlyTimer = setTimeout(purgeEarly, EARLY_TTL);
  return true;
}
// documentUrl is the loading principal URI, not necessarily the current
// History API location (and an opaque child can inherit its parent's URI).
// A trusted Gecko UUID is authoritative. Legacy senders retain exact URL checks.
function ownsRequestDocument(owner, id, principalUrl) {
  if (owner.geckoDocumentId) return id === owner.geckoDocumentId;
  return typeof principalUrl !== "string" || principalUrl === (owner.frameUrl || owner.pageUrl);
}
function bindEarly(tabId, frameId) {
  const root = documents.get(tabId);
  const frame = frameId > 0 ? frames.get(tabId + ":" + frameId) : null;
  const owner = frame || root;
  if (!root?.ready || root.invalidated || !owner || frame && !frame.ready || !geckoId(owner.geckoDocumentId)) return;
  for (const [key, entry] of [...earlyRequests]) {
    if (entry.tabId !== tabId || entry.frameId !== frameId) continue;
    earlyRequests.delete(key);
    if (Date.now() - entry.at >= EARLY_TTL) { discovery("early_request_expired", entry.isPrivate); continue; }
    if (!ownsRequestDocument(owner, entry.geckoDocumentId, entry.documentUrl) ||
        (entry.parentDocumentId && frame && entry.parentDocumentId !== frame.parentDocumentId) ||
        (typeof root.isPrivate === "boolean" && entry.isPrivate !== root.isPrivate) ||
        (root.cookieStoreId && entry.cookieStoreId && entry.cookieStoreId !== root.cookieStoreId)) {
      discovery("early_request_ambiguous", entry.isPrivate);
      if (entry.geckoDocumentId !== owner.geckoDocumentId) discovery("early_uuid_mismatch", entry.isPrivate);
      if (entry.documentUrl !== (frame ? frame.frameUrl : root.pageUrl))
        discovery(entry.documentUrl === owner.senderUrl ? "early_url_context_match" : "early_url_mismatch", entry.isPrivate);
      if (entry.parentDocumentId && frame && entry.parentDocumentId !== frame.parentDocumentId)
        discovery("early_parent_mismatch", entry.isPrivate);
      if ((typeof root.isPrivate === "boolean" && entry.isPrivate !== root.isPrivate) ||
          (root.cookieStoreId && entry.cookieStoreId && entry.cookieStoreId !== root.cookieStoreId))
        discovery("early_partition_mismatch", entry.isPrivate);
      if (entry.at >= owner.registeredAt) discovery("early_after_registration", entry.isPrivate);
      continue;
    }
    requests.set(key, { documentKey: root.documentKey, geckoDocumentId: owner.geckoDocumentId,
      frameKey: frame?.frameKey || null, frameUrl: frame?.frameUrl || null,
      frameId, requestSequence: ++nextRequestSequence });
    while (requests.size > MAX_REQUESTS) requests.delete(requests.keys().next().value);
    discovery("early_request_bound", entry.isPrivate);
    if (entry.response) onHeaders(entry.response); // metadata only; never replay HTTP
  }
}
async function currentLocation(sender, url) {
  if (sender.url === url) return true;
  const id = geckoId(sender.documentId);
  if (!id || !browser.webNavigation?.getFrame) return false;
  try {
    const info = await browser.webNavigation.getFrame({ tabId: sender.tab.id, frameId: sender.frameId });
    return info?.documentId === id && info.url === url;
  } catch (_) { return false; }
}
// Gecko keeps sender.url at content-context creation time across History API
// changes. Only getFrame + the same trusted UUID may establish a new location.
const ownerSender = (owner, sender) => !!owner &&
  (!owner.geckoDocumentId || owner.geckoDocumentId === sender.documentId) &&
  sender.url === owner.senderUrl;
async function startDocument(message, sender) {
  if (!isMainSender(sender) || !message || message.schemaVersion !== 1 ||
      message.event !== "document_started" ||
      Object.keys(message).sort().join(",") !== "event,pageUrl,schemaVersion" ||
      !safeUrl(message.pageUrl)) return null;
  // sender.url comes from Gecko and is not supplied by the page message.
  const previous = documents.get(sender.tab.id);
  if (typeof sender.url === "string" && sender.url !== message.pageUrl &&
      (!await currentLocation(sender, message.pageUrl) || documents.get(sender.tab.id) !== previous)) return null;
  const id = geckoId(sender.documentId);
  if (id && previous?.geckoDocumentId === id && !previous.invalidated && sameRoute(previous.pageUrl, message.pageUrl)) {
    previous.pageUrl = message.pageUrl;
    for (const frame of frames.values()) if (frame.tabId === sender.tab.id) frame.pageUrl = message.pageUrl;
    return { documentKey: previous.documentKey };
  }
  const documentKey = ++nextDocumentKey;
  cancelManifestTasks(sender.tab.id);
  documents.delete(sender.tab.id);
  for (const [id, frame] of frames) if (frame.tabId === sender.tab.id) frames.delete(id);
  documents.set(sender.tab.id, { documentKey, pageUrl: message.pageUrl, geckoDocumentId: id,
    isPrivate: typeof sender.tab.incognito === "boolean" ? sender.tab.incognito : null,
    cookieStoreId: sender.tab.cookieStoreId, senderUrl: sender.url, opaqueInjected: new Set(), ready: !id, invalidated: false, registeredAt: Date.now() });
  while (documents.size > MAX_DOCUMENTS) documents.delete(documents.keys().next().value);
  return { documentKey };
}
async function startFrame(message, sender) {
  if (!isExtensionSender(sender) || sender.frameId === 0 || !message ||
      message.schemaVersion !== 1 || message.event !== "frame_started" ||
      Object.keys(message).sort().join(",") !== "event,frameUrl,schemaVersion" ||
      !(safeUrl(message.frameUrl) || opaqueFrame(message.frameUrl)) || !await currentLocation(sender, message.frameUrl)) return null;
  const root = documents.get(sender.tab.id);
  if (!root || !root.ready) return null; // Never infer the parent from active tab/URL.
  const id = geckoId(sender.documentId);
  let info = null;
  if (id && browser.webNavigation?.getFrame) {
    try { info = await browser.webNavigation.getFrame({ tabId: sender.tab.id, frameId: sender.frameId }); } catch (_) {}
    if (!info || info.documentId !== id || info.url !== message.frameUrl || documents.get(sender.tab.id) !== root) return null;
    const parent = info.parentFrameId === 0 ? root : frames.get(sender.tab.id + ":" + info.parentFrameId);
    if (!parent || info.parentDocumentId !== parent.geckoDocumentId) {
      if (info.parentFrameId > 0) {
        if (pendingFrameStarts.size >= 64) pendingFrameStarts.delete(pendingFrameStarts.keys().next().value);
        pendingFrameStarts.set(frameId(sender), { tabId: sender.tab.id, frameId: sender.frameId,
          parentFrameId: info.parentFrameId, parentDocumentId: info.parentDocumentId,
          documentKey: root.documentKey, at: Date.now() });
        if (earlyTimer === null) earlyTimer = setTimeout(purgeEarly, EARLY_TTL);
      }
      return null;
    }
  } else if (opaqueFrame(sender.url)) return null;
  const existing = frames.get(frameId(sender));
  if (id && existing?.geckoDocumentId === id && existing.documentKey === root.documentKey && existing.frameUrl === message.frameUrl) {
    bindEarly(sender.tab.id, sender.frameId);
    return { pageUrl: root.pageUrl, documentKey: root.documentKey, frameUrl: existing.frameUrl, frameKey: existing.frameKey, frameId: existing.frameId };
  }
  const frame = { tabId: sender.tab.id, frameId: sender.frameId, frameUrl: message.frameUrl,
    pageUrl: root.pageUrl, documentKey: root.documentKey, frameKey: ++nextFrameKey,
    senderUrl: sender.url, geckoDocumentId: id, parentDocumentId: info?.parentDocumentId || root.geckoDocumentId, ready: false, registeredAt: Date.now() };
  const previous = frames.get(frameId(sender));
  if (previous) { cancelFrameTasks(previous.tabId, previous.frameKey); retireNativeFrame(previous); }
  frames.set(frameId(sender), frame);
  for (const [key, entry] of [...pendingFrameStarts]) {
    if (entry.tabId !== frame.tabId || entry.parentFrameId !== frame.frameId) continue;
    if (Date.now() - entry.at < EARLY_TTL && entry.documentKey === root.documentKey && entry.parentDocumentId === frame.geckoDocumentId)
      Promise.resolve(browser.tabs?.sendMessage?.(frame.tabId, { schemaVersion: 1, event: "discovery_rescan", kind: "parent_ready" }, { frameId: entry.frameId })).catch(() => {});
    pendingFrameStarts.delete(key);
  }
  while (frames.size > MAX_FRAMES) {
    const key = frames.keys().next().value, old = frames.get(key);
    retireNativeFrame(old); cancelFrameTasks(old.tabId, old.frameKey); frames.delete(key);
  }
  return { pageUrl: root.pageUrl, documentKey: root.documentKey,
    frameUrl: frame.frameUrl, frameKey: frame.frameKey, frameId: frame.frameId };
}
// Firefox does not declaratively inject into an uncommitted initial blank
// document (bug 1415539). A bounded, isolated-world fallback uses privileged
// frame ancestry/UUIDs, never a page-supplied frame ID or script body.
function scheduleOpaqueFrames(tabId, root) {
  if (!root?.ready || !root.geckoDocumentId || !browser.webNavigation?.getAllFrames ||
      !browser.tabs?.executeScript || root.opaqueTimer) return;
  if (root.opaqueBusy) { root.opaqueAgain = true; return; }
  root.opaqueTimer = setTimeout(async () => {
    root.opaqueTimer = null; root.opaqueBusy = true;
    try {
      const list = await browser.webNavigation.getAllFrames({ tabId });
      if (documents.get(tabId) !== root || root.invalidated || !Array.isArray(list)) return;
      const known = new Map([[0, root.geckoDocumentId]]), bounded = list.slice(0, MAX_FRAMES);
      // Parent order is not guaranteed. At most 16 nesting levels/injections.
      let budget = 16;
      for (let depth = 0; depth < 16 && budget > 0; depth++) {
        let progress = false;
        for (const info of bounded) {
          const id = geckoId(info.documentId);
          if (!Number.isInteger(info.frameId) || info.frameId <= 0 || known.has(info.frameId) || !id ||
              known.get(info.parentFrameId) !== info.parentDocumentId ||
              !(safeUrl(info.url) || opaqueFrame(info.url))) continue;
          known.set(info.frameId, id); progress = true;
          if (!opaqueFrame(info.url) || root.opaqueInjected.has(id)) continue;
          if (root.opaqueInjected.size >= MAX_FRAMES || --budget < 0) break;
          // executeScript checks the live frame's origin/host permissions again.
          await browser.tabs.executeScript(tabId, { frameId: info.frameId, matchAboutBlank: true,
            runAt: "document_start", file: "content.js" });
          if (documents.get(tabId) !== root || root.invalidated) return;
          root.opaqueInjected.add(id);
        }
        if (!progress) break;
      }
      if (budget <= 0 && root.opaqueInjected.size < MAX_FRAMES) root.opaqueAgain = true;
    } catch (_) { /* inaccessible/destroyed frame: fail closed until next event */ }
    finally {
      root.opaqueBusy = false;
      if (root.opaqueAgain && documents.get(tabId) === root && !root.invalidated) {
        root.opaqueAgain = false; scheduleOpaqueFrames(tabId, root);
      }
    }
  }, 100);
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
  if ("posterKind" in message) permitted.push("posterKind");
  if ("width" in message) permitted.push("width");
  if ("height" in message) permitted.push("height");
  if (keys !== permitted.sort().join(",") ||
      ("contentType" in message && !safeMime(message.contentType)) ||
      ("groupId" in message && !validGroup(message.groupId)) ||
      (("title" in message) !== ("titleKind" in message)) ||
      ("title" in message && !validTitle(message.title)) ||
      ("titleKind" in message && !["player", "structured", "video", "link", "page"].includes(message.titleKind)) ||
      ("durationSeconds" in message && !validSeconds(message.durationSeconds)) ||
      ("posterUrl" in message && !safeUrl(message.posterUrl)) ||
      ("posterKind" in message && (!("posterUrl" in message) ||
        !["element", "card", "player", "structured"].includes(message.posterKind))) ||
      ("width" in message && (!Number.isSafeInteger(message.width) ||
        message.width < 1 || message.width > 16384)) ||
      ("height" in message && (!Number.isSafeInteger(message.height) ||
        message.height < 1 || message.height > 8640))) return false;
  if (sender.frameId === 0) {
    const root = documents.get(sender.tab.id);
    return !!root && !root.invalidated && (typeof sender.url !== "string" || ownerSender(root, sender));
  }
  const frame = frames.get(frameId(sender));
  const root = documents.get(sender.tab.id);
  return !!frame && !!root && !root.invalidated && ownerSender(frame, sender) &&
    frame.pageUrl === root.pageUrl && frame.documentKey === root.documentKey;

}
browser.runtime.onMessage.addListener((message, sender) => {
  if (message && message.event === "document_started") return Promise.resolve(startDocument(message, sender));
  if (message && message.event === "opaque_frames_changed") {
    const root = isMainSender(sender) && documents.get(sender.tab.id);
    const valid = root?.ready && !root.invalidated && ownerSender(root, sender) &&
      message.schemaVersion === 1 && message.documentKey === root.documentKey &&
      Object.keys(message).sort().join(",") === "documentKey,event,schemaVersion";
    if (valid) scheduleOpaqueFrames(sender.tab.id, root);
    return Promise.resolve(!!valid);
  }
  if (message && message.event === "frame_identity") {
    if (!isExtensionSender(sender) || message.schemaVersion !== 1 ||
        Object.keys(message).sort().join(",") !== "event,schemaVersion") return Promise.resolve(null);
    const root = documents.get(sender.tab.id);
    const frame = sender.frameId === 0 ? root : frames.get(frameId(sender));
    const valid = root?.ready && !root.invalidated && frame?.ready &&
      ownerSender(frame, sender);
    return Promise.resolve(valid ? { documentKey: root.documentKey, frameKey: sender.frameId === 0 ? 0 : frame.frameKey } : null);
  }
  if (message && message.event === "document_ready") {
    const root = isMainSender(sender) && documents.get(sender.tab.id);
    const valid = !!root && !root.invalidated && message.schemaVersion === 1 &&
      Object.keys(message).sort().join(",") === "documentKey,event,schemaVersion" &&
      message.documentKey === root.documentKey && (!root.geckoDocumentId || root.geckoDocumentId === sender.documentId);
    if (valid) {
      root.ready = true; bindEarly(sender.tab.id, 0); scheduleOpaqueFrames(sender.tab.id, root);
      Promise.resolve(browser.tabs?.sendMessage?.(sender.tab.id, { schemaVersion: 1, event: "discovery_rescan", kind: "root_ready" })).catch(() => {});
    }
    return Promise.resolve(valid);
  }
  if (message && message.event === "frame_started") return Promise.resolve(startFrame(message, sender));
  if (message && message.event === "frame_ready") {
    const frame = isExtensionSender(sender) && frames.get(frameId(sender));
    const valid = !!frame && message.schemaVersion === 1 &&
      Object.keys(message).sort().join(",") === "event,frameKey,schemaVersion" &&
      message.frameKey === frame.frameKey && ownerSender(frame, sender) &&
      (!frame.geckoDocumentId || sender.documentId === frame.geckoDocumentId);
    if (valid) {
      frame.ready = true; bindEarly(sender.tab.id, sender.frameId);
      Promise.resolve(browser.tabs?.sendMessage?.(sender.tab.id,
        { schemaVersion: 1, event: "discovery_identity_ready" }, { frameId: sender.frameId })).catch(() => {});
    }
    return Promise.resolve(valid);
  }
  if (message && message.event === "frame_ended") {
    const frame = isExtensionSender(sender) && frames.get(frameId(sender));
    const valid = !!frame && sender.frameId > 0 && ownerSender(frame, sender) &&
      message.schemaVersion === 1 && Object.keys(message).sort().join(",") === "event,frameKey,schemaVersion" &&
      message.frameKey === frame.frameKey && (!frame.geckoDocumentId || frame.geckoDocumentId === sender.documentId);
    if (valid) { cancelFrameTasks(frame.tabId, frame.frameKey); retireNativeFrame(frame); frames.delete(frameId(sender)); }
    return Promise.resolve(valid);
  }
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
    cancelManifestTasks(id); clearEarly(id);
    documents.delete(id);
    for (const [key, value] of frames) if (value.tabId === id) frames.delete(key);
  });
}

function manifestKey(task) {
  return task.tabId + ":" + task.documentKey + ":" + (task.frameKey || 0) + ":" + String(task.isPrivate) + ":" + task.url;
}
function retireNativeFrame(frame) {
  const root = documents.get(frame.tabId);
  if (!root?.ready || root.documentKey !== frame.documentKey) return;
  Promise.resolve(browser.tabs?.sendMessage?.(frame.tabId, { schemaVersion: 1,
    event: "discovery_frame_retired", documentKey: frame.documentKey,
    frameId: frame.frameId, frameKey: frame.frameKey, frameUrl: frame.frameUrl }, { frameId: 0 })).catch(() => {});
}
function cancelFrameTasks(tabId, frameKey) {
  for (let i = manifestQueue.length - 1; i >= 0; i--)
    if (manifestQueue[i].tabId === tabId && manifestQueue[i].frameKey === frameKey) manifestQueue.splice(i, 1);
  for (const value of manifestInflight.values())
    if (value.tabId === tabId && value.frameKey === frameKey) value.controller?.abort();
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
  for (const key of manifestVariantRates.keys())
    if (key.startsWith(tabId + ":")) manifestVariantRates.delete(key);
}

// onBeforeRequest is passive metadata capture only. No bodies or credentials.
// Only bounded document/response metadata is retained. A response from a previous
// document carries the old key and is rejected by the new content script.
function requestKey(details) {
  if (!details || !Number.isInteger(details.tabId) || details.tabId < 0 ||
      !["string", "number"].includes(typeof details.requestId)) return null;
  return details.tabId + ":" + String(details.requestId);
}
function onBefore(details) {
  const key = requestKey(details);
  if (!key || details.type === "speculative" ||
      (details.frameId !== undefined && (!Number.isInteger(details.frameId) || details.frameId < 0))) return;
  const doc = documents.get(details.tabId);
  if (!doc || !doc.ready || doc.invalidated) { captureEarly(details); return; }
  let frame = null;
  if (Number.isInteger(details.frameId) && details.frameId > 0) {
    frame = frames.get(details.tabId + ":" + details.frameId);
    if (!frame || !frame.ready || frame.documentKey !== doc.documentKey)
      { captureEarly(details); return; }
  }
  const owner = frame || doc;
  if (!ownsRequestDocument(owner, details.documentId, details.documentUrl) ||
      (frame && details.parentDocumentId && frame.parentDocumentId && details.parentDocumentId !== frame.parentDocumentId) ||
      (typeof doc.isPrivate === "boolean" && details.incognito !== doc.isPrivate) ||
      (doc.cookieStoreId && details.cookieStoreId && doc.cookieStoreId !== details.cookieStoreId)) { captureEarly(details); return; }
  if (requests.has(key)) return; // A redirect retains its original document/sequence.
  requests.set(key, { documentKey: doc.documentKey, geckoDocumentId: owner.geckoDocumentId,
    frameKey: frame ? frame.frameKey : null, frameUrl: frame ? frame.frameUrl : null,
    frameId: frame ? frame.frameId : 0, requestSequence: ++nextRequestSequence });
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
// Anonymous categories are deliberately NOT attributed to any profile/tab.
const discoveryStats = new Map();
let discoveryTimer = null;
function discovery(code, isPrivate, count = 1) {
  if (typeof isPrivate !== "boolean") return;
  const key = code + ":" + isPrivate;
  discoveryStats.set(key, Math.min(100000, (discoveryStats.get(key) || 0) + count));
  if (discoveryTimer !== null) return;
  discoveryTimer = setTimeout(() => {
    discoveryTimer = null;
    for (const [key, count] of discoveryStats) {
      const [code, privacy] = key.split(":");
      Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", {
        schemaVersion: 1, event: "discovery_stats", code, count, isPrivate: privacy === "true",
      })).catch(() => {});
    }
    discoveryStats.clear();
  }, 1000);
}
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
  // onHeadersReceived also fires for redirects. Keep ownership until the final response.
  if (typeof details.statusCode === "number" && details.statusCode >= 300 && details.statusCode < 400) return;
  if (key && !requestDocumentKey && earlyRequests.has(key)) {
    const entry = earlyRequests.get(key);
    if (Date.now() - entry.at >= EARLY_TTL) { earlyRequests.delete(key); discovery("early_request_expired", entry.isPrivate); return; }
    if (safeUrl(details.url)) entry.response = { tabId: details.tabId, requestId: details.requestId,
      frameId: details.frameId, documentId: details.documentId, documentUrl: details.documentUrl,
      url: details.url, type: details.type, incognito: details.incognito, statusCode: details.statusCode,
      responseHeaders: (details.responseHeaders || []).filter(h => h && typeof h.name === "string" &&
        ["content-type", "content-length", "content-range", "content-encoding"].includes(h.name.toLowerCase()))
        .slice(0, 4).map(h => ({ name: h.name, value: typeof h.value === "string" ? h.value.slice(0, 128) : "" })) };
    return;
  }
  if (key) requests.delete(key);
  if (["image", "imageset", "ping", "beacon", "script", "stylesheet", "font", "csp_report"].includes(details.type)) return;
  if (!probeSent && typeof details.url === "string" &&
      details.url.startsWith("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4")) {
    probeSent = true;
    try { Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector",
      { schemaVersion: 1, event: "network_probe" })).catch(() => {}); } catch (_) {}
  }
  const success = typeof details.statusCode !== "number" ||
    (details.statusCode >= 200 && details.statusCode < 300);
  const relevantFailure = [401, 403, 404, 410].includes(details.statusCode);
  if (!success && !relevantFailure) return;
  if (!safeUrl(details.url) || typeof details.incognito !== "boolean") return;
  let contentType = null;
  if (Array.isArray(details.responseHeaders)) {
    const header = details.responseHeaders.find(h => h && typeof h.name === "string" &&
      h.name.toLowerCase() === "content-type");
    if (header && safeMime(header.value)) contentType = header.value;
    else if (header) return;
  }
  // Error pages often report text/html; the request path is still valuable for
  // marking an already-known manifest as expired/unauthorized.
  const format = classify(details.url, success ? contentType : null);
  if (format === "segment") { skipSegment(details.incognito); return; }
  if (!format) return;
  const tabId = Number.isInteger(details.tabId) && details.tabId >= 0 ? details.tabId : -1;
  const doc = documents.get(tabId);
  // No guessing: request must have started in a known document, and
  // response initiator must still match that document's page URL.
  const requestFrame = requestDocumentKey && requestDocumentKey.frameKey !== null ?
    frames.get(tabId + ":" + requestDocumentKey.frameId) : null;
  if (!doc || doc.invalidated || !requestDocumentKey || doc.documentKey !== requestDocumentKey.documentKey ||
      (requestDocumentKey.geckoDocumentId && requestDocumentKey.geckoDocumentId !== details.documentId) ||
      (requestDocumentKey.frameKey !== null &&
        (!requestFrame || requestFrame.frameKey !== requestDocumentKey.frameKey ||
          requestFrame.frameUrl !== requestDocumentKey.frameUrl)) ||
      !ownsRequestDocument(requestFrame || doc, details.documentId, details.documentUrl)) {
    anonymous("network_unassigned", details.incognito);
    discovery("frame_mismatch", details.incognito);
    return;
  }
  const now = Date.now();
  const sizeBytes = contentSize(details, format);
  const dedupKey = tabId + ":" + doc.documentKey + ":" + (requestFrame?.frameKey || 0) + ":" + String(details.incognito) +
    ":" + details.url + ":" + format + ":" + details.statusCode + ":" + (contentType || "");
  const previously = seen.get(dedupKey);
  if (previously && now - previously.at < RECENT_MS &&
      (sizeBytes === null || previously.sizeBytes === sizeBytes)) return;
  const rateKey = tabId + ":" + String(details.incognito);
  const slot = routedRate.get(rateKey);
  if (!slot || now - slot.since >= 5000) routedRate.set(rateKey, { since: now, count: 1 });
  else if (slot.count >= MAX_ROUTED_PER_5S) { discovery("queue_limit", details.incognito); discovery("queue_network_rate", details.incognito); return; }
  else slot.count++;
  while (routedRate.size > 64) routedRate.delete(routedRate.keys().next().value);
  seen.delete(dedupKey);
  seen.set(dedupKey, { at: now, sizeBytes });
  while (seen.size > MAX_SEEN) seen.delete(seen.keys().next().value);
  const message = {
    schemaVersion: 1, event: "media_candidate", source: "network", url: details.url,
    isPrivate: details.incognito, documentKey: doc.documentKey, pageUrl: doc.pageUrl,
    provenance: "observed", verification: success ? "manifest_observed" :
      (details.statusCode === 401 || details.statusCode === 403 ? "auth_error" :
        (details.statusCode === 404 || details.statusCode === 410 ? "expired" : "unplayable")),
    httpStatus: details.statusCode
  };
  message.requestSequence = requestDocumentKey.requestSequence;
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
        else if (success && (format === "HLS" || format === "DASH")) queueManifest({
          url: details.url, format, tabId, documentKey: doc.documentKey,
          pageUrl: doc.pageUrl, isPrivate: details.incognito, groupId: message.groupId,
          frameKey: requestFrame?.frameKey, frameUrl: requestFrame?.frameUrl,
          requestSequence: requestDocumentKey.requestSequence,
          ...(manifestVariantRates.get(streamGroupKey(tabId, doc.documentKey,
            details.incognito, requestFrame?.frameKey, details.url)) || {}),
        });
      })
      .catch(() => anonymous("network_unassigned", details.incognito));
  } catch (_) { anonymous("network_unassigned", details.incognito); }
}

async function rejectManifest(task) {
  if (!currentTask(task)) return;
  const message = { schemaVersion: 1, event: "media_candidate", source: "network",
    url: task.url, contentType: task.format === "HLS" ? "application/vnd.apple.mpegurl" : "application/dash+xml",
    provenance: "observed", verification: "unplayable", isPrivate: task.isPrivate,
    documentKey: task.documentKey, pageUrl: task.pageUrl, groupId: task.groupId };
  if (task.frameKey && task.frameUrl) { message.frameKey = task.frameKey; message.frameUrl = task.frameUrl; }
  if (Number.isSafeInteger(task.requestSequence)) message.requestSequence = task.requestSequence;
  await browser.tabs.sendMessage(task.tabId, message, { frameId: 0 });
}
function currentTask(task) {
  const doc = documents.get(task.tabId);
  if (!doc || doc.documentKey !== task.documentKey || doc.pageUrl !== task.pageUrl) return false;
  if (!task.frameKey) return true;
  return [...frames.values()].some(frame => frame.tabId === task.tabId &&
    frame.documentKey === task.documentKey && frame.frameKey === task.frameKey &&
    frame.frameUrl === task.frameUrl);
}
function queueManifest(task) {
  if (typeof fetch !== "function" || !globalThis.NiraManifestParser) return;
  const now = Date.now();
  const key = manifestKey(task);
  const cached = manifestCache.get(key);
  if (cached && now - cached < MANIFEST_TTL_MS) return;
  if (manifestInflight.has(key) || manifestQueue.some(item => manifestKey(item) === key)) return;
  if (manifestQueue.length >= MAX_MANIFEST_QUEUE) { discovery("queue_limit", task.isPrivate); discovery("queue_manifest_capacity", task.isPrivate); return; }
  manifestQueue.push(task);
  drainManifests();
}
function drainManifests() {
  while (manifestActive < MAX_MANIFEST_ACTIVE && manifestQueue.length) {
    const task = manifestQueue.shift();
    const current = documents.get(task.tabId);
    if (!currentTask(task)) continue;
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
  manifestInflight.set(key, { tabId: task.tabId, frameKey: task.frameKey, controller });
  let response = null, reader = null, segment = null;
  const timeout = setTimeout(() => { if (controller) controller.abort(); }, 4000);
  try {
    response = await fetch(task.url, {
      method: "GET", credentials: "omit", redirect: "follow", cache: "no-store",
      referrer: safeUrl(task.frameUrl) ? task.frameUrl : task.pageUrl,
      signal: controller ? controller.signal : undefined,
    });
    if (!response || !response.ok) {
      discovery("unavailable", task.isPrivate);
      discovery(response && (response.status === 401 || response.status === 403) ? "manifest_auth_failed" : "manifest_fetch_failed", task.isPrivate);
      return;
    }
    const finalUrl = safeUrl(response.url) ? response.url : task.url;
    const responseType = response.headers && response.headers.get("content-type");
    if (classify(finalUrl, responseType) !== task.format) { discovery("manifest_invalid", task.isPrivate); return; }
    const declared = response.headers && response.headers.get("content-length");
    if (declared && (!/^\d+$/.test(declared) || Number(declared) > MAX_MANIFEST_BYTES)) {
      discovery("manifest_too_large", task.isPrivate); return;
    }
    if (!response.body || typeof response.body.getReader !== "function" ||
        typeof TextDecoder !== "function") { discovery("manifest_body_unavailable", task.isPrivate); return; }
    reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8", { fatal: false });
    let bytes = 0, text = "";
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      if (!chunk.value || !Number.isSafeInteger(chunk.value.byteLength) ||
          chunk.value.byteLength < 0) { await reader.cancel(); return; }
      bytes += chunk.value.byteLength;
      if (bytes > MAX_MANIFEST_BYTES) { discovery("manifest_too_large", task.isPrivate); await reader.cancel(); return; }
      text += decoder.decode(chunk.value, { stream: true });
      if (text.length > MAX_MANIFEST_BYTES) { discovery("manifest_too_large", task.isPrivate); await reader.cancel(); return; }
    }
    text += decoder.decode();
    discovery("manifest_kib", task.isPrivate, Math.max(1, Math.ceil(bytes / 1024)));
    const parsed = task.format === "HLS" ?
      NiraManifestParser.parseHls(text, finalUrl, 8) :
      NiraManifestParser.parseDash(text, finalUrl, 8);
    const current = documents.get(task.tabId);
    if (!currentTask(task) || controller?.signal.aborted) return;
    discovery(task.format === "HLS" && parsed.kind === "media" ? "manifest_valid_media" : "manifest_valid_master", task.isPrivate);
    if (!parsed.variants?.length && !parsed.durationSeconds && !parsed.bitrate && !parsed.exactSizeBytes) discovery("manifest_no_technical", task.isPrivate);
    const verified = { schemaVersion: 1, event: "media_candidate", source: "network",
      provenance: "observed", verification: "manifest_verified",
      httpStatus: Number.isInteger(response.status) ? response.status : 200,
      url: task.url, isPrivate: task.isPrivate, documentKey: task.documentKey,
      pageUrl: task.pageUrl, groupId: task.groupId,
      // Parser-observed metadata is attached to exactly this document and manifest.
      isLive: !!parsed.live,
      contentType: task.format === "HLS" ? "application/vnd.apple.mpegurl" : "application/dash+xml" };
    if (Array.isArray(parsed.variants) && parsed.variants.length > 0) {
      verified.variantCount = parsed.variants.length;
      // Advertised choices, never the active playback resolution.
      const heights = [...new Set(parsed.variants.filter(v =>
        Number.isSafeInteger(v.width) && Number.isSafeInteger(v.height) &&
        v.width > 0 && v.height >= 144 && v.height <= 8640)
        .map(v => v.height + "p"))].slice(0, 16);
      if (heights.length) verified.availableQualities = heights.join(",");
    }
    // DASH master metadata is not the current quality unless uniquely determined.
    if (task.format === "DASH" && parsed.variants.length === 1) {
      const only = parsed.variants[0];
      if (Number.isSafeInteger(only.width) && only.width > 0 && only.width <= 16384)
        verified.width = only.width;
      if (Number.isSafeInteger(only.height) && only.height > 0 && only.height <= 8640) {
        verified.height = only.height;
        verified.quality = only.height + "p";
      }
      if (Number.isFinite(only.frameRate) && only.frameRate > 0 && only.frameRate <= 240)
        verified.frameRate = only.frameRate;
      if (typeof only.codecs === "string" && only.codecs.length <= 160)
        verified.codecs = only.codecs;
    }
    if (task.format === "HLS" && parsed.kind === "media" &&
        Number.isSafeInteger(parsed.exactSizeBytes) && parsed.exactSizeBytes > 0)
      verified.sizeBytes = parsed.exactSizeBytes;
    if (!parsed.live && Number.isSafeInteger(parsed.durationSeconds) &&
        parsed.durationSeconds > 0 && parsed.durationSeconds <= 604800)
      verified.durationSeconds = parsed.durationSeconds;
    // DASH single-representation total A/V bitrate, or bitrate inherited
    // from the exact HLS child variant being verified; never sum all qualities.
    const parsedBitrate = task.format === "DASH" ? parsed.bitrate : task.bitrate;
    if (Number.isSafeInteger(parsedBitrate) && parsedBitrate > 0 && parsedBitrate <= 1000000000) {
      verified.bitrate = parsedBitrate;
      // HLS inherited bitrate cannot be assumed to be an average.
      verified.bitrateKind = task.format === "DASH" ? "declared" : (task.bitrateKind || "peak");
    }
    if (Number.isSafeInteger(task.requestSequence)) verified.requestSequence = task.requestSequence;
    if (task.frameKey && task.frameUrl) {
      verified.frameKey = task.frameKey;
      verified.frameUrl = task.frameUrl;
    }
    if (parsed.drm) {
      discovery("drm", task.isPrivate);
      verified.verification = "unplayable";
      await browser.tabs.sendMessage(task.tabId, verified, { frameId: 0 });
      return;
    }
    discovery("manifest_valid", task.isPrivate);
    // A validated MPD/master is still NEEDS_VERIFICATION, never stream-confirmed.
    if (task.format === "DASH")
      await browser.tabs.sendMessage(task.tabId, verified, { frameId: 0 });
    if (task.format === "HLS" && parsed.kind === "media" && safeUrl(parsed.firstSegmentUrl)) {
      await browser.tabs.sendMessage(task.tabId, verified, { frameId: 0 });
      segment = await fetch(parsed.firstSegmentUrl, {
        method: "GET", credentials: "omit", redirect: "follow", cache: "no-store",
        referrer: safeUrl(task.frameUrl) ? task.frameUrl : task.pageUrl, headers: { Range: "bytes=0-0" },
        signal: controller ? controller.signal : undefined,
      });
      const segmentType = segment?.headers?.get?.("content-type")?.split(";")[0].trim().toLowerCase();
      const finalSegmentPath = segment && safeUrl(segment.url) ? pathOf(segment.url) : "";
      const plausibleSegment = segmentType ?
        (segmentType.startsWith("video/") || segmentType.startsWith("audio/") ||
          segmentType === "application/octet-stream" || segmentType === "binary/octet-stream") :
        segmentPath(finalSegmentPath);
      if (currentTask(task) && !controller?.signal.aborted && segment && (segment.ok || segment.status === 206) && plausibleSegment &&
          !/\.(?:m3u8|mpd)$/i.test(finalSegmentPath)) {
        if (segment.body && typeof segment.body.cancel === "function")
          await segment.body.cancel().catch(() => {});
        verified.url = task.url;
        verified.verification = "stream_verified";
        verified.httpStatus = Number.isInteger(segment.status) ? segment.status : 200;
        await browser.tabs.sendMessage(task.tabId, verified, { frameId: 0 });
      }
    }
    // DASH representations can refer to initialization/segment templates, not
    // playable MP4 files. Keep the observed MPD; never export fake direct videos.
    for (const variant of (task.format === "HLS" ? parsed.variants || [] : [])) {
      if (!currentTask(task) || controller?.signal.aborted) return;
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
      if (Number.isSafeInteger(variant.width) && variant.width > 0 && variant.width <= 16384)
        message.width = variant.width;
      if (Number.isSafeInteger(variant.height) && variant.height > 0 && variant.height <= 8640) {
        message.height = variant.height;
        message.quality = variant.height + "p";
      }
      if (Number.isFinite(variant.frameRate) && variant.frameRate > 0 && variant.frameRate <= 240)
        message.frameRate = variant.frameRate;
      if (Number.isSafeInteger(variant.bitrate) && variant.bitrate > 0 && variant.bitrate <= 1000000000)
        { message.bitrate = variant.bitrate;
          message.bitrateKind = variant.bitrateKind; }
      if (typeof variant.codecs === "string" && variant.codecs.length <= 160)
        message.codecs = variant.codecs;
      await browser.tabs.sendMessage(task.tabId, message, { frameId: 0 });
      // Keep a bounded per-document bandwidth hint. The child playlist is
      // inspected only if Gecko already requests it; no extra network probes.
      if (Number.isSafeInteger(variant.bitrate) && variant.bitrate > 0 &&
          variant.bitrate <= 1000000000) {
        manifestVariantRates.set(lineageKey, {
          bitrate: variant.bitrate, bitrateKind: variant.bitrateKind || "peak"
        });
        while (manifestVariantRates.size > 128)
          manifestVariantRates.delete(manifestVariantRates.keys().next().value);
      }
    }
    manifestCache.set(key, Date.now());
    while (manifestCache.size > 32) manifestCache.delete(manifestCache.keys().next().value);
  } catch (error) {
    if (currentTask(task) && !controller?.signal.aborted) {
      discovery("manifest_invalid", task.isPrivate);
      discovery(error && (error.name === "AbortError" || error.name === "TypeError") ? "manifest_fetch_failed" : "manifest_parse_failed", task.isPrivate);
      if (/^(?:invalid_hls|invalid_dash)$/.test(error?.message || "")) await rejectManifest(task);
    }
  } finally {
    clearTimeout(timeout);
    try { if (reader) { await reader.cancel().catch(() => {}); reader.releaseLock?.(); } } catch (_) {}
    for (const body of [response?.body, segment?.body])
      try { await body?.cancel?.().catch(() => {}); } catch (_) {}
    if (manifestInflight.get(key)?.controller === controller) manifestInflight.delete(key);
  }
}
if (browser.webRequest && browser.webRequest.onHeadersReceived) {
  browser.webRequest.onHeadersReceived.addListener(onHeaders,
    { urls: ["http://*/*", "https://*/*"] }, ["responseHeaders"]);
}

for (const event of [browser.webRequest?.onCompleted, browser.webRequest?.onErrorOccurred])
  if (event && typeof event.addListener === "function") event.addListener(details => {
    const key = requestKey(details); if (key) requests.delete(key);
    if (details.error || !earlyRequests.get(key)?.response) earlyRequests.delete(key);
  }, { urls: ["http://*/*", "https://*/*"] });

if (browser.webNavigation?.onBeforeNavigate) browser.webNavigation.onBeforeNavigate.addListener(details => {
  const owner = details.frameId === 0 ? documents.get(details.tabId) : frames.get(details.tabId + ":" + details.frameId);
  // Gecko's event timestamp precedes content registration even if IPC delivery
  // reaches this listener later. A stale start must not erase the new epoch.
  if (owner && Number.isFinite(details.timeStamp) && details.timeStamp <= owner.registeredAt) return;
  clearEarly(details.tabId, details.frameId === 0 ? undefined : details.frameId);
  if (details.frameId === 0) {
    cancelManifestTasks(details.tabId); documents.delete(details.tabId);
    for (const [key, frame] of frames) if (frame.tabId === details.tabId) frames.delete(key);
  } else {
    const key = details.tabId + ":" + details.frameId, frame = frames.get(key);
    if (frame) { cancelFrameTasks(details.tabId, frame.frameKey); retireNativeFrame(frame); }
    frames.delete(key);
  }
});
if (browser.webNavigation?.onHistoryStateUpdated) browser.webNavigation.onHistoryStateUpdated.addListener(details => {
  const root = documents.get(details.tabId);
  const owner = details.frameId === 0 ? root : frames.get(details.tabId + ":" + details.frameId);
  if (!root || !owner || !geckoId(details.documentId) || details.documentId !== owner.geckoDocumentId ||
      !safeUrl(details.url) || sameRoute(details.url, details.frameId === 0 ? root.pageUrl : owner.frameUrl)) return;
  clearEarly(details.tabId, details.frameId === 0 ? undefined : details.frameId);
  cancelManifestTasks(details.tabId);
  if (details.frameId === 0) root.invalidated = true;
  Promise.resolve(browser.tabs?.sendMessage?.(details.tabId,
    { schemaVersion: 1, event: "discovery_rescan", kind: "history" }, { frameId: details.frameId })).catch(() => {});
});
