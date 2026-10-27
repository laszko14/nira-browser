"use strict";

// Assist only user-initiated inline thumbnail previews. Ordinary links must
// still navigate when no actual preview media is available.
(() => {
  const CARD = "article,li,figure,[class*='thumb'],[class*='Thumb']," +
    "[class*='card'],[class*='Card'],[class*='video'],[class*='Video']";
  const PREVIEW_ATTRS = ["data-preview-video", "data-preview-src",
    "data-preview-url", "data-video-preview", "data-trailer", "data-hover-video"];
  const failed = new WeakSet();

  function previewUrl(card) {
    const nodes = [card, ...Array.from(card.querySelectorAll(
      "[data-preview-video],[data-preview-src],[data-preview-url]," +
      "[data-video-preview],[data-trailer],[data-hover-video]"
    )).slice(0, 12)];
    for (const node of nodes) {
      if (!node || !node.getAttribute) continue;
      for (const name of PREVIEW_ATTRS) {
        const value = node.getAttribute(name);
        if (typeof value !== "string" || !value || value.length > 2048) continue;
        try {
          const url = new URL(value, document.baseURI || location.href);
          if (/^https?:$/.test(url.protocol) &&
              /\.(?:mp4|webm)(?:$)/i.test(url.pathname)) return url.href;
        } catch (_) {}
      }
    }
    return null;
  }

  function findPreviewVideo(card, source) {
    let video = card.querySelector("video");
    if (video) return video;
    if (!source || !document.createElement) return null;
    const image = card.querySelector("img");
    if (!image || !image.parentElement) return null;
    const layer = image.parentElement;
    // A real, declared video preview can be played on demand without sending
    // the click to the movie page or starting a hidden video download.
    video = document.createElement("video");
    video.src = source;
    video.muted = true;
    video.defaultMuted = true;
    video.loop = true;
    video.playsInline = true;
    video.setAttribute("playsinline", "");
    video.style.cssText = "position:absolute;inset:0;width:100%;height:100%;" +
      "object-fit:cover;pointer-events:none;z-index:1;";
    if (getComputedStyle(layer).position === "static") {
      layer.style.position = "relative";
    }
    layer.appendChild(video);
    video.dataset.niraPreviewCreated = "1";
    return video;
  }

  function playPreview(event) {
    if (!event.isTrusted || !event.cancelable || event.defaultPrevented ||
        event.button > 0 || !event.target ||
        typeof event.target.closest !== "function") return;
    const control = event.target.closest(
      "button,[role='button'],[class*='preview-play'],[class*='play-preview']"
    );
    if (control && control.closest("nav,header,footer")) return;
    const label = control ? [
      control.getAttribute("aria-label"), control.getAttribute("title"),
      control.textContent, control.className
    ].filter(v => typeof v === "string").join(" ") : "";
    const explicitPlay = !!control && /(?:play|preview|podgląd|odtwórz|odtworz)/i.test(label);
    const clickedThumb = event.target.closest("img,video,[class*='thumb'],[class*='Thumb']");
    if (!explicitPlay && !clickedThumb) return;
    const card = (control || clickedThumb).closest(CARD) ||
      (clickedThumb && clickedThumb.closest("a[href]") &&
       clickedThumb.closest("a[href]").parentElement);
    if (!card || card.closest("nav,header,footer") || !card.querySelector) return;
    const link = (control || clickedThumb).closest("a[href]") || card.querySelector("a[href]");
    if (!link) return;
    const source = card.querySelectorAll ? previewUrl(card) : null;
    const video = findPreviewVideo(card, source);
    if (!video || !video.paused || video.isConnected === false || failed.has(video) ||
        typeof video.play !== "function") return;
    const videoSrc = video.currentSrc || video.src ||
      (video.querySelector && (video.querySelector("source[src]") || {}).src);
    if (!videoSrc || !/^(?:https?:|blob:)/i.test(videoSrc)) return;
    if (!(source || video.muted || video.defaultMuted || video.loop ||
          (video.hasAttribute && video.hasAttribute("playsinline")))) return;
    video.muted = true;
    video.playsInline = true;
    let result;
    try { result = video.play(); } catch (_) { failed.add(video); return; }
    // Intercept only after a usable preview has been found and play attempted.
    event.preventDefault();
    event.stopImmediatePropagation();
    if (result && typeof result.catch === "function") {
      result.catch(() => {
        failed.add(video);
        if (video.dataset && video.dataset.niraPreviewCreated === "1" &&
            typeof video.remove === "function") video.remove();
        // On the next tap the original link works normally.
      });
    }
  }
  document.addEventListener("click", playPreview, { capture: true });
})();
