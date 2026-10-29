/*
 * Zavro video SEARCH — transport-only content script (stage 3A).
 * No DOM scan, no form submission, no fetch, no storage and no page patches.
 * Page content cannot call the isolated extension's native port directly.
 */
(function () {
  "use strict";

  if (window.top !== window || !/^https?:$/.test(location.protocol)) return;

  // One unguessable ID per injected document. NEVER fall back to Math.random.
  var bytes = new Uint8Array(16);
  try {
    crypto.getRandomValues(bytes);
  } catch (_) {
    return;
  }
  var documentId = Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
  var pageUrl = location.href;
  var connected = false;
  var port;
  try {
    port = browser.runtime.connectNative("zavro.video.search");
    if (!port) return;

    port.onMessage.addListener(function (message) {
      if (!message || typeof message !== "object" || Array.isArray(message) ||
          message.schemaVersion !== 1 ||
          Object.keys(message).sort().join(",") !== "documentId,event,schemaVersion" ||
          message.documentId !== documentId) return;

      if (message.event === "bound") {
        connected = location.href === pageUrl;
      } else if (message.event === "ping" && connected && location.href === pageUrl) {
        try {
          port.postMessage({ schemaVersion: 1, event: "pong", documentId: documentId });
        } catch (_) { connected = false; }
      }
    });
    port.onDisconnect.addListener(function () { connected = false; });
    port.postMessage({
      schemaVersion: 1,
      event: "hello",
      documentId: documentId,
      pageUrl: pageUrl
    });
  } catch (_) {
    connected = false;
  }
})();
