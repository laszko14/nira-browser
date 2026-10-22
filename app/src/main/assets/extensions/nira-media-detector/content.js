"use strict";

(() => {
  let lastSentAt = -Infinity;
  document.addEventListener("playing", async event => {
    if (!event.isTrusted || !(event.target instanceof HTMLVideoElement)) return;
    const video = event.target;
    const source = video.currentSrc;
    const now = performance.now();
    if (video.paused || !source || source.length > 8192 || now - lastSentAt < 1000) return;
    lastSentAt = now;
    try {
      const validated = await browser.runtime.sendMessage({
        schemaVersion: 1, event: "media_candidate", source: "dom", url: source
      });
      // The background handler has no native session. Return through this frame's
      // session-bound native handler rather than guessing a native ID from tabId.
      if (validated && validated.event === "media_candidate" &&
          document.defaultView && video.isConnected && video.currentSrc === source) {
        await browser.runtime.sendNativeMessage("nira.media.detector", validated);
      }
    } catch (_) { /* Fail closed; never log payloads or native error details. */ }
  }, { capture: true, passive: true });
})();
