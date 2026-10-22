"use strict";

// No page-world bridge, storage, URLs in logs, or active network requests.
const byteLength = value => new TextEncoder().encode(value).length;
browser.runtime.onMessage.addListener((message, sender) => {
  if (sender.id !== browser.runtime.id || !sender.tab || sender.frameId !== 0 ||
      !message || typeof message !== "object" || Array.isArray(message)) {
    return Promise.resolve(null);
  }
  const keys = Object.keys(message).sort().join(",");
  if (message.schemaVersion !== 1) return Promise.resolve(null);
  if (keys !== "event,schemaVersion,source,url" || message.event !== "media_candidate" ||
      message.source !== "dom" || typeof message.url !== "string" ||
      byteLength(message.url) > 8192 || byteLength(JSON.stringify(message)) > 16384 ||
      /[\s\u0000-\u001f\u007f]/u.test(message.url)) return Promise.resolve(null);
  try {
    const url = new URL(message.url);
    if (!["https:", "http:"].includes(url.protocol) || !url.hostname || url.username || url.password) {
      return Promise.resolve(null);
    }
  } catch (_) { return Promise.resolve(null); }
  return Promise.resolve({ schemaVersion: 1, event: "media_candidate", source: "dom", url: message.url });
});

// Stage 3.0 compatibility probe only: no URL/header forwarding or network classification.
// Kept to the single public fixture; one signal per background lifetime.
let probeSent = false;
if (browser.webRequest && browser.webRequest.onHeadersReceived) {
  browser.webRequest.onHeadersReceived.addListener(() => {
    if (probeSent) return;
    probeSent = true;
    browser.runtime.sendNativeMessage("nira.media.detector", {
      schemaVersion: 1, event: "network_probe"
    }).catch(() => {});
  }, { urls: ["https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4*"] });
}
