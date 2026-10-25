"use strict";

/**
 * v1132: frame + element scoped native handoff.
 * Automatic offers require a trusted user interaction, real HTMLVideoElement,
 * visible long-form direct media and explicit AUTO mode on the Android side.
 * This script never navigates, clicks Play, bypasses EME or touches cookies.
 */
(() => {
  if (!location || !/^https?:$/.test(location.protocol)) return;
  const PORT = "nira.media.playback";
  const MAX_VIDEOS = 64;
  const MAX_URL_LENGTH = 8192;
  const holds = new Map();
  const elementKeys = new WeakMap();
  const offeredAt = new WeakMap();
  let port = null, connectAttempts = 0, lastGesture = null;
  const GESTURE_WINDOW_MS = 10_000;
  const lastAutoOffer = new WeakMap();

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
      send({version:1, event:"handoff_ack", nonce:request.nonce, ok:false});
      return;
    }
    let matches = [], inspected = 0;
    try {
      for (const video of document.querySelectorAll("video")) {
        if (++inspected > MAX_VIDEOS || matches.length > 1) break;
        if (!(video instanceof HTMLVideoElement) || !video.isConnected ||
            video.ended || video.error || video.mediaKeys || video.srcObject) continue;
        if (mediaUrl(video) === request.mediaUrl &&
            (!targeted || elementKey(video) === request.elementKey)) matches.push(video);
      }
    } catch (_) { return; }
    if (matches.length !== 1) {
      send({version:1, event:"handoff_ack", nonce:request.nonce, ok:false});
      return;
    }
    const video = matches[0], wasPlaying = !video.paused;
    if (targeted && !wasPlaying) {
      send({version:1, event:"handoff_ack", nonce:request.nonce, ok:false});
      return;
    }
    try {
      video.pause();
      if (!video.paused) throw new Error("not paused");
      holds.set(request.nonce, {video, src:request.mediaUrl, page:location.href, wasPlaying});
      send({version:1, event:"handoff_ack", nonce:request.nonce, ok:true});
    } catch (_) {
      send({version:1, event:"handoff_ack", nonce:request.nonce, ok:false});
    }
  }
  function release(request, resume) {
    const state = holds.get(request.nonce);
    if (!state) return;
    holds.delete(request.nonce);
    if (!resume || !state.wasPlaying || state.page !== location.href ||
        !state.video.isConnected || state.video.ended ||
        mediaUrl(state.video) !== state.src) return;
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
  }

  function videoPlayed(event) {
    const video = event.target;
    if (!(video instanceof HTMLVideoElement) || !port) return;
    for (const hold of holds.values()) {
      if (hold.video === video) { try { video.pause(); } catch (_) {} return; }
    }
    if (!frameKey || !lastGesture || lastGesture.page !== location.href ||
        Date.now() - lastGesture.at > GESTURE_WINDOW_MS) return;
    const url = mediaUrl(video);
    if (!eligible(video, url)) return;

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
    if (activeCount !== 0) return;
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
    send({version:1,event:"auto_offer",frameKey,elementKey:key,
      pageUrl:location.href,mediaUrl:url});
  }

  function connect() {
    if (port || connectAttempts >= 4) return;
    connectAttempts++;
    try {
      const connected = browser.runtime.connectNative(PORT);
      if (!connected) return;
      port = connected;
      connected.onMessage.addListener(message => {
        if (port !== connected || !message || message.version !== 1 ||
            typeof message.event !== "string" || typeof message.nonce !== "string") return;
        if (message.event === "prepare") prepare(message);
        if (message.event === "restore") release(message,true);
        if (message.event === "cancel") release(message,false);
      });
      connected.onDisconnect.addListener(() => {
        if (port !== connected) return;
        port = null;
        holds.clear();
        if (connectAttempts < 4) setTimeout(connect,800);
      });
    } catch (_) {
      port = null;
      if (connectAttempts < 4) setTimeout(connect,800);
    }
  }
  document.addEventListener("pointerup",userGesture,true);
  document.addEventListener("click",userGesture,true);
  document.addEventListener("keydown",userGesture,true);
  document.addEventListener("play",videoPlayed,true);
  document.addEventListener("playing",videoPlayed,true);
  document.addEventListener("loadedmetadata",videoPlayed,true);
  document.addEventListener("durationchange",videoPlayed,true);
  connect();
  if (document.readyState === "loading")
    document.addEventListener("DOMContentLoaded",connect,{once:true});
})();
