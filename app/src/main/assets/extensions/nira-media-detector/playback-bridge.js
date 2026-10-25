"use strict";

/**
 * v1132: frame + element scoped native handoff.
 * Automatic offers require a trusted user interaction, real HTMLVideoElement,
 * visible long-form direct media and explicit AUTO mode on the Android side.
 * This script never navigates, clicks Play, bypasses EME or touches cookies.
 */
(() => {
  if (!location) return;
  const secureDocument = /^https?:$/.test(location.protocol);
  const inheritedBlank = location.href === "about:blank";
  if (!secureDocument && !inheritedBlank) return;
  const PORT = "nira.media.playback";
  const MAX_VIDEOS = 64;
  // Diagnostics use a second Gecko transport. Port handoff remains mandatory
  // for any actual pause/auto-play to avoid losing per-frame identity.
  function oneShot(code) {
    try {
      const reply = browser.runtime.sendNativeMessage(PORT, {
        version:1, event:"diagnostic", code
      });
      if (reply && typeof reply.catch === "function") reply.catch(() => {});
    } catch (_) {}
  }
  function protocolOneShot(event, fields = {}) {
    // A Gecko port challenge binds this message to its particular frame.
    // No challenge, no AUTO/ACK; all protocol data stays transient in Gecko.
    if (!probeNonce || !["probe_ack", "auto_offer", "handoff_ack", "inline_layout", "iframe_geometry"].includes(event)) return;
    try {
      const reply = browser.runtime.sendNativeMessage(PORT,
        {version:1, event, probeNonce, ...fields});
      if (reply && typeof reply.catch === "function") reply.catch(() => {});
    } catch (_) {}
  }
  const MAX_URL_LENGTH = 8192;
  const holds = new Map();
  const elementKeys = new WeakMap();
  const offeredAt = new WeakMap();
  let port = null, connectAttempts = 0, lastGesture = null, probeNonce = null;
  const GESTURE_WINDOW_MS = 10_000;
  const lastAutoOffer = new WeakMap();
  let inlineLease = null, lastLayout = 0, trackedFrame = null;
  function uniqueIframe(frameUrl) {
    if (typeof window === "undefined" || window !== window.top ||
        typeof document.querySelectorAll !== "function") return null;
    const matches = Array.from(document.querySelectorAll("iframe")).filter(f => {
      if (!f.isConnected || !f.src) return false;
      try { return new URL(f.src, location.href).href === frameUrl; }
      catch (_) { return false; }
    });
    return matches.length === 1 ? matches[0] : null;
  }
  function reportIframeGeometry(nonce, frameUrl) {
    const iframe = uniqueIframe(frameUrl);
    const r = iframe && rectOf(iframe);
    if (!r || r.w < 320 || r.h < 180 || r.x < -5000 || r.y < -5000) return;
    const data = {nonce,frameUrl,...r};
    send({version:1,event:"iframe_geometry",...data});
    protocolOneShot("iframe_geometry",data);
  }
  function trackIframe() {
    if (!trackedFrame || Date.now()-lastLayout < 90) return;
    lastLayout = Date.now();
    reportIframeGeometry(trackedFrame.nonce,trackedFrame.frameUrl);
  }
  function rectOf(video) {
    if (!video || !video.isConnected) return null;
    const r = video.getBoundingClientRect();
    const dpr = Number(typeof window !== "undefined" ? (window.devicePixelRatio || 1) : 1);
    if (![r.left,r.top,r.width,r.height,dpr].every(Number.isFinite) ||
        r.width < 1 || r.height < 1 || dpr < .5 || dpr > 5) return null;
    return {x:r.left,y:r.top,w:r.width,h:r.height,dpr};
  }
  function layoutUpdate() {
    if (!inlineLease || !holds.has(inlineLease.nonce) ||
        Date.now() - lastLayout < 90) return;
    lastLayout = Date.now();
    const r = rectOf(inlineLease.video);
    const data = {nonce:inlineLease.nonce,frameKey,
      elementKey:elementKey(inlineLease.video),
      ...(r || {x:0,y:0,w:0,h:0,dpr:1})};
    send({version:1,event:"inline_layout",...data});
    protocolOneShot("inline_layout",data);
  }

  function uuid() {
    try {
      if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
      if (typeof crypto !== "undefined" && crypto.getRandomValues) {
        const b = crypto.getRandomValues(new Uint8Array(16));
        b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128;
        const s = Array.from(b, x => x.toString(16).padStart(2, "0")).join("");
        return s.slice(0,8)+"-"+s.slice(8,12)+"-"+s.slice(12,16)+"-"+s.slice(16,20)+"-"+s.slice(20);
      }
    } catch (_) {}
    return null; // No entropy: fail closed for automatic identity.
  }
  const frameKey = uuid();
  function elementKey(video) {
    if (!elementKeys.has(video)) elementKeys.set(video, uuid());
    return elementKeys.get(video);
  }
  function mediaUrl(video) {
    if (!video || video.srcObject || video.mediaKeys) return null;
    const value = video.currentSrc || video.src;
    if (typeof value !== "string" || value.length > MAX_URL_LENGTH) return null;
    try {
      const uri = new URL(value, location.href);
      return ["https:", "http:"].includes(uri.protocol) &&
        !!uri.hostname && !uri.username && !uri.password ? uri.href : null;
    } catch (_) { return null; }
  }
  function send(value) { try { if (port) port.postMessage(value); } catch (_) {} }
  function sendAck(nonce, ok) {
    send({version:1, event:"handoff_ack", nonce, ok});
    protocolOneShot("handoff_ack", {nonce, ok});
  }
  const diagTimes = new Map();
  function diagnostic(code) {
    const now = Date.now();
    if (now - (diagTimes.get(code) || 0) < 2000) return;
    if (diagTimes.size > 36) diagTimes.clear();
    diagTimes.set(code, now);
    // Only whitelisted code constants, never addresses/element IDs/credentials.
    if (port) send({version:1,event:"diagnostic",code});
    // If Gecko's native Port.onPortMessage is silent, the one-shot
    // fallback tells us whether the content script itself executed.
    oneShot(code);
  }

  function eligible(video, url) {
    if (!url || video.paused || video.ended || video.error || !video.isConnected ||
        video.muted || video.loop || video.mediaKeys || video.srcObject ||
        (Number.isFinite(video.duration) ? video.duration < 180 :
          !/\.m3u8(?:\?|$)/i.test(url) || video.readyState < 2)) return false;
    // Refuse generic endpoints, MSE/blobs and typical ad assets. Heuristics
    // alone cannot guarantee ad identification: AUTO remains user opt-in.
    let parsed;
    try { parsed = new URL(url); } catch (_) { return false; }
    if (!/\.(?:mp4|m4v|webm|m3u8)(?:$)/i.test(parsed.pathname) ||
        /(?:^|[./_-])(?:ads?|vast|vpaid|preroll|midroll|doubleclick)(?:[./_-]|$)/i.test(parsed.hostname + parsed.pathname)) return false;
    if (typeof video.getBoundingClientRect !== "function") return false;
    const r = video.getBoundingClientRect();
    return r.width >= 320 && r.height >= 180;
  }

  function prepare(request) {
    if (typeof request.mediaUrl !== "string" || request.mediaUrl.length > MAX_URL_LENGTH ||
        request.pageUrl !== location.href || holds.size ||
        !/^[0-9a-f-]{36}$/.test(request.nonce)) return;
    const targeted = request.frameKey != null || request.elementKey != null;
    if (targeted && (!frameKey || request.frameKey !== frameKey ||
        typeof request.elementKey !== "string")) {
      sendAck(request.nonce, false);
      return;
    }
    let matches = [], inspected = 0;
    try {
      for (const video of document.querySelectorAll("video")) {
        if (++inspected > MAX_VIDEOS || matches.length > 1) break;
        if (!(video instanceof HTMLVideoElement) || !video.isConnected ||
            video.ended || video.error || video.mediaKeys || video.srcObject) continue;
        const matchingSource = request.sourceKind === "blob"
          ? typeof (video.currentSrc || video.src) === "string" &&
            (video.currentSrc || video.src).startsWith("blob:")
          : mediaUrl(video) === request.mediaUrl;
        if (matchingSource &&
            (!targeted || elementKey(video) === request.elementKey)) matches.push(video);
      }
    } catch (_) { return; }
    if (matches.length !== 1) {
      sendAck(request.nonce, false);
      return;
    }
    const video = matches[0], wasPlaying = !video.paused;
    const blobSource = typeof (video.currentSrc || video.src) === "string" &&
      (video.currentSrc || video.src).startsWith("blob:");
    if (request.sourceKind === "blob" &&
        (!targeted || !blobSource || video.mediaKeys || video.srcObject ||
         !Number.isFinite(video.duration) || video.duration < 180 ||
         video.muted || video.loop || !rectOf(video))) {
      sendAck(request.nonce, false);
      return;
    }
    if (targeted && !wasPlaying) {
      sendAck(request.nonce, false);
      return;
    }
    try {
      video.pause();
      if (!video.paused) throw new Error("not paused");
      holds.set(request.nonce, {video,
        src:request.sourceKind === "blob" ? (video.currentSrc || video.src) : request.mediaUrl,
        sourceKind:request.sourceKind, page:location.href, wasPlaying});
      sendAck(request.nonce, true);
    } catch (_) {
      sendAck(request.nonce, false);
    }
  }
  function release(request, resume) {
    const state = holds.get(request.nonce);
    if (!state) return;
    holds.delete(request.nonce);
    if (inlineLease && inlineLease.nonce === request.nonce) inlineLease = null;
    if (!resume || !state.wasPlaying || state.page !== location.href ||
        !state.video.isConnected || state.video.ended ||
        (state.sourceKind === "blob"
          ? (state.video.currentSrc || state.video.src) !== state.src
          : mediaUrl(state.video) !== state.src)) return;
    try {
      const result = state.video.play();
      if (result && typeof result.catch === "function") result.catch(() => {});
    } catch (_) {}
  }

  // 'play' is an asynchronously dispatched media event, not itself a trusted
  // pointer/key event. Requiring playEvent.isTrusted excluded many real pages.
  // Player controls can be siblings of the video, so require an actual gesture
  // in this frame and one unique eligible visible video instead.
  function userGesture(event) {
    if (!event || event.isTrusted !== true) return;
    if (event.type === "keydown" && !["Enter", " ", "Spacebar"].includes(event.key)) return;
    lastGesture = {at:Date.now(), target:event.target, page:location.href};
    diagnostic("gesture");
  }

  function videoPlayed(event) {
    const video = event.target;
    if (!(video instanceof HTMLVideoElement) || !port) return;
    for (const hold of holds.values()) {
      if (hold.video === video) { try { video.pause(); } catch (_) {} return; }
    }
    diagnostic("video_play");
    if (!secureDocument || !frameKey || !lastGesture || lastGesture.page !== location.href ||
        Date.now() - lastGesture.at > GESTURE_WINDOW_MS) {
      diagnostic("no_gesture");
      return;
    }
    const url = mediaUrl(video);
    const blob = !url && !video.mediaKeys && !video.srcObject &&
      typeof (video.currentSrc || video.src) === "string" &&
      (video.currentSrc || video.src).startsWith("blob:");
    if (!url && !blob) {
      diagnostic(video.mediaKeys ? "source_drm" : "no_direct_url");
      return;
    }
    if (blob) diagnostic("source_blob");
    // Blob is only offered as an element identity, never as a playable URL.
    // Native must independently find exactly one matching network HLS source.
    const blobEligible = blob && Number.isFinite(video.duration) &&
      video.duration >= 180 && !video.muted && !video.loop &&
      video.readyState >= 2 && !!rectOf(video) &&
      rectOf(video).w >= 320 && rectOf(video).h >= 180 &&
      typeof window !== "undefined";
    if (!(blob ? blobEligible : eligible(video, url))) {
      const r = typeof video.getBoundingClientRect === "function" ?
        video.getBoundingClientRect() : {width:0,height:0};
      diagnostic(video.muted || video.loop ? "source_muted" :
        Number.isFinite(video.duration) && video.duration < 180 ? "source_short" :
        r.width < 320 || r.height < 180 ? "source_small" : "source_invalid");
      return;
    }

    // If several actively playing videos are present, selecting by URL or
    // DOM order would risk opening an advertisement. Do nothing.
    let activeCount = 0, inspected = 0;
    try {
      for (const other of document.querySelectorAll("video")) {
        if (++inspected > MAX_VIDEOS) return;
        if (other instanceof HTMLVideoElement && !other.paused &&
            other.isConnected && !other.muted && other !== video &&
            mediaUrl(other)) activeCount++;
      }
    } catch (_) { return; }
    if (activeCount !== 0) { diagnostic("multiple_players"); return; }
    const key = elementKey(video);
    if (!key || Date.now() - (lastAutoOffer.get(video) || 0) < 15_000) return;
    const t = lastGesture.target;
    let related = (video === t);
    for (let parent = video.parentElement, depth = 0; parent && depth < 6;
         parent = parent.parentElement, depth++) {
      if (typeof parent.contains === "function" && parent.contains(t)) {
        related = true; break;
      }
    }
    // Some embedded players put their overlay button outside the video tree.
    // Permit only when there is exactly one active non-muted player, playback
    // is visibly underway, and a trusted gesture just happened in this frame.
    if (!related && video.currentTime <= 0 && video.readyState < 3) return;
    lastAutoOffer.set(video,Date.now());
    lastGesture = null;
    diagnostic("auto_offered");
    // Only top-level video has unambiguous coordinates in GeckoView.
    const r = rectOf(video);
    const offer = {frameKey, elementKey:key, pageUrl:location.href,
      mediaUrl:url || "", sourceKind:blob ? "blob" : "direct",
      topFrame:typeof window !== "undefined" && window === window.top,
      viewportW:typeof window !== "undefined" ? window.innerWidth : 0,
      viewportH:typeof window !== "undefined" ? window.innerHeight : 0,
      ...(r || {})};
    send({version:1,event:"auto_offer",...offer});
    protocolOneShot("auto_offer",offer);
  }

  function connect() {
    if (port || connectAttempts >= 4) return;
    connectAttempts++;
    try {
      const connected = browser.runtime.connectNative(PORT);
      if (!connected) return;
      port = connected;
      diagnostic("bridge_ready");
      connected.onMessage.addListener(message => {
        if (port !== connected || !message || message.version !== 1 ||
            typeof message.event !== "string") return;
        if (message.event === "probe" && typeof message.nonce === "string" &&
            /^[0-9a-f-]{36}$/.test(message.nonce)) {
          probeNonce = message.nonce;
          send({version:1,event:"probe_ack",nonce:message.nonce});
          protocolOneShot("probe_ack");
          diagnostic("probe_received");
          return;
        }
        if (typeof message.nonce !== "string") return;
        if (message.event === "prepare") prepare(message);
        if (message.event === "iframe_geometry_request" &&
            typeof message.frameUrl === "string" &&
            /^[0-9a-f-]{36}$/.test(message.nonce)) {
          reportIframeGeometry(message.nonce,message.frameUrl);
        }
        if (message.event === "iframe_track" &&
            typeof message.frameUrl === "string" &&
            /^[0-9a-f-]{36}$/.test(message.nonce)) {
          trackedFrame = {nonce:message.nonce,frameUrl:message.frameUrl};
          reportIframeGeometry(message.nonce,message.frameUrl);
        }
        if (message.event === "inline_start" && holds.has(message.nonce) &&
            typeof window !== "undefined" && window === window.top) {
          inlineLease = {nonce:message.nonce,video:holds.get(message.nonce).video};
          layoutUpdate();
        }
        if (message.event === "restore") release(message,true);
        if (message.event === "cancel") release(message,false);
      });
      connected.onDisconnect.addListener(() => {
        if (port !== connected) return;
        port = null;
        probeNonce = null;
        holds.clear();
        inlineLease = null;
        trackedFrame = null;
        if (connectAttempts < 4) setTimeout(connect,800);
      });
    } catch (_) {
      port = null;
      if (connectAttempts < 4) setTimeout(connect,800);
    }
  }
  oneShot("script_started");
  document.addEventListener("pointerup",userGesture,true);
  document.addEventListener("click",userGesture,true);
  document.addEventListener("keydown",userGesture,true);
  document.addEventListener("play",videoPlayed,true);
  document.addEventListener("playing",videoPlayed,true);
  document.addEventListener("loadedmetadata",videoPlayed,true);
  document.addEventListener("durationchange",videoPlayed,true);
  document.addEventListener("pageshow", () => diagnostic("document_visible"), true);
  document.addEventListener("scroll",layoutUpdate,true);
  document.addEventListener("scroll",trackIframe,true);
  if (typeof window !== "undefined") window.addEventListener("scroll",layoutUpdate,true);
  if (typeof window !== "undefined") window.addEventListener("resize",layoutUpdate,true);
  if (typeof window !== "undefined") window.addEventListener("scroll",trackIframe,true);
  if (typeof window !== "undefined") window.addEventListener("resize",trackIframe,true);
  connect();
  if (document.readyState === "loading")
    document.addEventListener("DOMContentLoaded",connect,{once:true});
})();
