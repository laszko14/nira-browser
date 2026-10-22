"use strict";

// Fix a common touch regression on video grids: a play overlay nested inside
// a link navigates to the film page instead of starting its already-present
// muted HTML5 thumbnail preview. Intercept ONLY explicit play controls when
// a playable inline preview is present. Ordinary links remain untouched.
(() => {
  function playPreview(event) {
    if (!event.isTrusted || !event.cancelable || event.defaultPrevented ||
        event.button > 0 || !event.target ||
        typeof event.target.closest !== "function") return;
    const control = event.target.closest(
      "button,[role='button'],[class*='preview-play'],[class*='play-preview']"
    );
    if (!control || control.closest("nav,header,footer") ||
        /(?:^|[\s_-])(?:next|prev|close|menu|download)(?:[\s_-]|$)/i.test(String(control.className || ""))) return;
    const label = [control.getAttribute("aria-label"), control.getAttribute("title"),
      control.textContent, control.className].filter(v => typeof v === "string").join(" ");
    if (!/(?:play|preview|podgląd|odtwórz|odtworz)/i.test(label)) return;
    const card = control.closest(
      "article,li,figure,[class*='thumb'],[class*='Thumb'],[class*='card'],[class*='Card'],[class*='video']"
    );
    if (!card || card.closest("nav,header,footer")) return;
    const video = card.querySelector("video");
    if (!video || !video.paused || !video.isConnected ||
        video.readyState < 2 || !video.querySelectorAll) return;
    const source = video.currentSrc || video.src ||
      (video.querySelector("source[src]") || {}).src;
    if (!source || !/^(?:https?:|blob:)/i.test(source)) return;
    // Only preview-oriented muted/looping inline video, not full players.
    if (!(video.muted || video.defaultMuted || video.loop ||
        video.hasAttribute("playsinline"))) return;
    if (!control.closest("a[href]") && !card.querySelector("a[href]")) return;
    // Run play synchronously within the user's click gesture.
    video.muted = true;
    video.playsInline = true;
    let result;
    try { result = video.play(); } catch (_) { return; }
    event.preventDefault();
    event.stopImmediatePropagation();
    if (result && typeof result.catch === "function") {
      result.catch(() => {
        // A failed play attempt must not trigger synthetic navigation.
        // Keep the thumbnail in place so the user can choose its real link.
      });
    }
  }
  document.addEventListener("click", playPreview, { capture: true });
})();
