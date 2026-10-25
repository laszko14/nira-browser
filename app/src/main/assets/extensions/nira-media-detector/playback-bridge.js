"use strict";

/**
 * v1131, deliberately manual-only Gecko → MPV handoff.
 *
 * One privileged native port per frame. BrowserStore chooses the target
 * EngineSession and source URL; this script will only pause a UNIQUE matching
 * HTMLVideoElement in the CURRENT document. It never clicks a site's Play,
 * injects page-world JavaScript, captures credentials or blocks navigation.
 */
(() => {
  if (!location || !/^https?:$/.test(location.protocol)) return;

  const PORT = "nira.media.playback";
  const MAX_VIDEOS = 64;
  const MAX_URL_LENGTH = 8192;
  const holds = new Map();
  let port = null;
  let lastPort = null;

  function mediaUrl(video) {
    const value = video && (video.currentSrc || video.src);
    if (typeof value !== "string" || value.length > MAX_URL_LENGTH) return null;
    try {
      const uri = new URL(value, location.href);
      return ["https:", "http:"].includes(uri.protocol) &&
        !!uri.hostname && !uri.username && !uri.password ? uri.href : null;
    } catch (_) { return null; }
  }

  function send(value) {
    try { if (port) port.postMessage(value); } catch (_) {}
  }

  function prepare(request) {
    if (typeof request.mediaUrl !== "string" || request.mediaUrl.length > MAX_URL_LENGTH ||
        request.pageUrl !== location.href || holds.size ||
        !/^[0-9a-f-]{36}$/.test(request.nonce)) return;
    let matches = [];
    try {
      for (const video of document.querySelectorAll("video")) {
        if (matches.length > 1) break;
        if (!(video instanceof HTMLVideoElement) || !video.isConnected ||
            video.ended || video.error || video.mediaKeys) continue;
        if (mediaUrl(video) === request.mediaUrl) matches.push(video);
        if (matches.length >= MAX_VIDEOS) break;
      }
    } catch (_) { return; }
    if (matches.length !== 1) {
      send({ version: 1, event: "handoff_ack", nonce: request.nonce, ok: false });
      return;
    }
    const video = matches[0];
    const wasPlaying = !video.paused;
    try {
      video.pause();
      if (!video.paused) throw new Error("video not paused");
      holds.set(request.nonce, {
        video, src: request.mediaUrl, page: location.href, wasPlaying,
      });
      send({ version: 1, event: "handoff_ack", nonce: request.nonce, ok: true });
    } catch (_) {
      send({ version: 1, event: "handoff_ack", nonce: request.nonce, ok: false });
    }
  }

  function restore(request) {
    const state = holds.get(request.nonce);
    if (!state) return;
    holds.delete(request.nonce);
    // Another navigation, new source, detached element or a prior paused state:
    // NEVER issue Play against the replacement document/element.
    if (!state.wasPlaying || state.page !== location.href ||
        !state.video.isConnected || state.video.ended ||
        mediaUrl(state.video) !== state.src) return;
    try {
      const result = state.video.play();
      if (result && typeof result.catch === "function") result.catch(() => {});
    } catch (_) {}
  }

  function connect() {
    if (port) return;
    try {
      const connected = browser.runtime.connectNative(PORT);
      if (!connected) return;
      port = connected;
      lastPort = connected;
      connected.onMessage.addListener(message => {
        if (port !== connected || !message || message.version !== 1 ||
            typeof message.event !== "string" ||
            typeof message.nonce !== "string") return;
        if (message.event === "prepare") prepare(message);
        if (message.event === "restore") restore(message);
      });
      connected.onDisconnect.addListener(() => {
        if (port !== connected) return;
        port = null;
        // No automatic resumption after disconnect: the browser/user owns
        // playback state and a stale frame must never trigger site autoplay.
        holds.clear();
      });
    } catch (_) { port = null; }
  }

  connect();
  // Gecko can attach the native handler after document_start. One bounded
  // retry on DOM readiness covers this without a permanent reconnect loop.
  if (document.readyState === "loading")
    document.addEventListener("DOMContentLoaded", connect, { once: true });
})();
