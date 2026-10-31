"use strict";

// GeckoView/Firefox 128+ MV2 MAIN-world bridge. This script must not obtain
// WebExtension privileges or access URLs/cookies. An event is only a rescan
// hint; isolated content.js independently verifies the real open root.
(() => {
  if (typeof Element === "undefined" || typeof EventTarget === "undefined") return;
  const prototype = Element.prototype;
  const descriptor = Object.getOwnPropertyDescriptor(prototype, "attachShadow");
  if (!descriptor || !descriptor.configurable || typeof descriptor.value !== "function") return;
  const original = descriptor.value;
  const marker = Symbol.for("nira.media.open-shadow-hook.v1");
  if (original[marker]) return;
  const dispatch = EventTarget.prototype.dispatchEvent;
  const OriginalEvent = Event;
  function attachedShadow(init) {
    // Exactly preserve the native return and exceptions, including closed roots.
    const root = Reflect.apply(original, this, arguments);
    if (root && root.mode === "open") {
      try {
        Reflect.apply(dispatch, this, [
          new OriginalEvent("nira-media-shadow-root-open", { bubbles: true, composed: true })
        ]);
      } catch (_) {
        // Never change page attachShadow behavior just because a hint failed.
      }
    }
    return root;
  }
  Object.defineProperty(attachedShadow, marker, { value: true });
  try {
    Object.defineProperty(prototype, "attachShadow", { ...descriptor, value: attachedShadow });
  } catch (_) {
    // Some pages may lock the property. Normal DOM/visible events still work.
  }
})();
