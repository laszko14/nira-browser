"use strict";

(() => {
  // Only messages from this extension, delivered to this specific Gecko tab,
  // can be forwarded into its native EngineSession-bound handler.
  browser.runtime.onMessage.addListener((message, sender) => {
    if (!sender || sender.id !== browser.runtime.id || !message ||
        message.schemaVersion !== 1 || message.event !== "media_candidate" ||
        message.source !== "network" || typeof message.url !== "string" ||
        ![4, 5].includes(Object.keys(message).length)) return Promise.resolve(false);
    return Promise.resolve(browser.runtime.sendNativeMessage("nira.media.detector", message))
      .then(value => value === true).catch(() => false);
  });

  const sent = new WeakMap();
  let scanScheduled = false;

  function mimeOf(video, url) {
    // Prefer the type of the selected <source>; never infer a MIME from page text.
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

    // Background validates before the native session-bound message.
    Promise.resolve().then(() => browser.runtime.sendMessage(message)).then(validated => {
      if (!validated || validated.event !== "media_candidate" ||
          validated.url !== url || validated.contentType !== message.contentType ||
          !video.isConnected || video.paused || location.href !== navigation ||
          (video.currentSrc || video.src) !== url) return;
      return browser.runtime.sendNativeMessage("nira.media.detector", validated);
    }).catch(() => { /* Never log signed URLs or native message contents. */ });
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
    // Bounded rescan catches SPA replacement and src/<source> mutations.
    let inspected = 0;
    for (const video of document.querySelectorAll("video")) {
      if (++inspected > 48) break;
      inspect(video);
    }
  }

  function scheduleScan() {
    if (scanScheduled) return;
    scanScheduled = true;
    setTimeout(() => {
      scanScheduled = false;
      scan();
    }, 120);
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
  scan();
})();
