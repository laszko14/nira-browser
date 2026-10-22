"use strict";

(() => {
  // Document keys come only from the privileged background extension.
  // Native code independently checks BrowserStore URL, EngineSession and privacy.
  let activePageUrl = location.href;
  let documentKey = null;
  let registering = null;
  const sent = new WeakMap();
  let scanScheduled = false;

  function ensureDocument() {
    const pageUrl = location.href;
    if (pageUrl !== activePageUrl) {
      activePageUrl = pageUrl;
      documentKey = null;
      registering = null;
    }
    if (documentKey !== null) return Promise.resolve(documentKey);
    if (registering) return registering;
    const operation = Promise.resolve().then(() => browser.runtime.sendMessage({
      schemaVersion: 1, event: "document_started", pageUrl
    })).then(reply => {
      if (pageUrl !== location.href || !reply ||
          !Number.isSafeInteger(reply.documentKey) || reply.documentKey <= 0) return null;
      const key = reply.documentKey;
      return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", {
        schemaVersion: 1, event: "media_document", pageUrl, documentKey: key
      })).then(accepted => {
        if (accepted !== true || pageUrl !== location.href) return null;
        documentKey = key;
        return key;
      });
    }).catch(() => null);
    registering = operation.then(result => { registering = null; return result; });
    return registering;
  }

  browser.runtime.onMessage.addListener((message, sender) => {
    if (!sender || sender.id !== browser.runtime.id || !message ||
        message.schemaVersion !== 1 || message.event !== "media_candidate" ||
        message.source !== "network" || typeof message.url !== "string" ||
        typeof message.isPrivate !== "boolean" ||
        !Number.isSafeInteger(message.documentKey) ||
        typeof message.pageUrl !== "string" ||
        ![7, 8].includes(Object.keys(message).length) ||
        message.pageUrl !== location.href) return Promise.resolve(false);
    return ensureDocument().then(key => {
      if (key === null || key !== message.documentKey || message.pageUrl !== location.href) return false;
      return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", message))
        .then(value => value === true).catch(() => false);
    });
  });

  function mimeOf(video, url) {
    for (const source of video.querySelectorAll("source")) {
      if (source.src === url && typeof source.type === "string" && source.type) {
        return source.type.trim();
      }
    }
    return null;
  }

  function inspect(video) {
    if (!(video instanceof HTMLVideoElement) || !video.isConnected ||
        video.paused || video.ended) return;
    const url = video.currentSrc || video.src || "";
    if (!url || url.length > 8192) return;
    const contentType = mimeOf(video, url);
    const navigation = location.href;
    const key = JSON.stringify([navigation, url, contentType]);
    if (sent.get(video) === key) return;
    sent.set(video, key);
    const message = { schemaVersion: 1, event: "media_candidate", source: "dom", url };
    if (contentType) message.contentType = contentType;

    ensureDocument().then(documentKey => {
      if (documentKey === null || location.href !== navigation) {
        sent.delete(video);
        return null;
      }
      return browser.runtime.sendMessage(message).then(validated => {
        if (!validated || validated.event !== "media_candidate" ||
            validated.url !== url || validated.contentType !== message.contentType ||
            !video.isConnected || video.paused || location.href !== navigation ||
            (video.currentSrc || video.src) !== url) {
          sent.delete(video);
          return null;
        }
        return browser.runtime.sendNativeMessage("nira.media.detector", {
          ...validated, pageUrl: navigation, documentKey
        }).then(accepted => { if (accepted !== true) sent.delete(video); });
      });
    }).catch(() => { sent.delete(video); /* Never log a signed URL. */ });
  }

  function onMediaEvent(event) {
    if (!event.isTrusted || !(event.target instanceof HTMLVideoElement)) return;
    if (event.type === "emptied") sent.delete(event.target);
    else inspect(event.target);
  }

  for (const type of ["play", "playing", "loadedmetadata", "emptied"]) {
    document.addEventListener(type, onMediaEvent, { capture: true, passive: true });
  }

  function scan() {
    // MutationObserver + BrowserStore location changes handle SPA route updates.
    // Only one scheduled scan per content-script instance; at most 48 videos.
    if (activePageUrl !== location.href) ensureDocument();
    let inspected = 0;
    for (const video of document.querySelectorAll("video")) {
      if (++inspected > 48) break;
      inspect(video);
    }
  }
  function scheduleScan() {
    if (scanScheduled) return;
    scanScheduled = true;
    setTimeout(() => { scanScheduled = false; scan(); }, 120);
  }

  const observer = new MutationObserver(scheduleScan);
  observer.observe(document, {
    childList: true, subtree: true, attributes: true, attributeFilter: ["src", "type"]
  });
  document.addEventListener("DOMContentLoaded", scheduleScan, { once: true });
  document.addEventListener("visibilitychange", scheduleScan, { passive: true });
  for (const type of ["pageshow", "popstate", "hashchange"]) {
    window.addEventListener(type, scheduleScan, { passive: true });
  }
  // Initial handshake is best-effort; failed Gecko/native registration retries
  // on subsequent media events and DOM changes, without a persistent timer.
  ensureDocument();
  scan();
})();
