"use strict";

// Assist only user-initiated inline thumbnail previews. Ordinary links must
// still navigate when no actual preview media is available.
(() => {
  const CARD = "article,li,figure,[class*='thumb'],[class*='Thumb']," +
    "[class*='card'],[class*='Card'],[class*='video'],[class*='Video']";
  const PREVIEW_ATTRS = ["data-preview-video", "data-preview-src",
    "data-preview-url", "data-video-preview", "data-trailer", "data-hover-video"];
  // DOM owners are weakly held. Failed preview attempts must not trap navigation
  // when a generated video node was removed after play() rejected.
  const failedVideos = new WeakMap(); // video -> source URL
  const failedCards = new WeakMap(); // card -> {source, link, href}
  const latestAttempts = new WeakMap(); // video -> current attempt token

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
        event.button !== 0 || event.ctrlKey || event.metaKey ||
        event.altKey || event.shiftKey || !event.target ||
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
    // Clicking the real video surface must remain under the page/player controls.
    const targetVideo = event.target.closest("video");
    if (event.target.tagName === "VIDEO" ||
        (targetVideo && targetVideo.tagName === "VIDEO")) return;
    const explicitPreviewControl = !!control &&
      /(?:preview|podgląd|trailer|zwiastun)/i.test(label);
    const clickedThumb = event.target.closest("img,video,[class*='thumb'],[class*='Thumb']");
    if (!explicitPlay && !clickedThumb) return;
    const card = (control || clickedThumb).closest(CARD) ||
      (clickedThumb && clickedThumb.closest("a[href]") &&
       clickedThumb.closest("a[href]").parentElement);
    if (!card || card.closest("nav,header,footer") || !card.querySelector) return;
    const source = card.querySelectorAll ? previewUrl(card) : null;
    // Neither playsinline/muted/loop nor a random card link proves preview ownership.
    // Only a local, declared preview source or an explicit preview control can
    // authorize taking over the click and muting playback.
    if (!source && !explicitPreviewControl) return;
    const clickedLink = (control || clickedThumb).closest("a[href]");
    if (!clickedLink && !explicitPreviewControl) return;
    const link = clickedLink || card.querySelector("a[href]");
    if (!link) return;
    // A failed generated preview has already consumed one click. For the same
    // card, preview source and link, let the next tap use native navigation.
    // A new source/link or a new DOM card has a separate preview lifecycle.
    const ownerFailure = failedCards.get(card);
    if (source && ownerFailure && ownerFailure.source === source &&
        ownerFailure.link === link && ownerFailure.href === link.href) return;
    const video = findPreviewVideo(card, source);
    if (!video || !video.paused || video.isConnected === false ||
        typeof video.play !== "function") return;
    const videoSrc = video.currentSrc || video.src ||
      (video.querySelector && (video.querySelector("source[src]") || {}).src);
    if (failedVideos.get(video) === videoSrc) return;
    if (!videoSrc || !/^(?:https?:|blob:)/i.test(videoSrc)) return;
    // The declared source must belong to this exact video, not a different
    // player in the same article/card. Never copy its sound state to a preview.
    if (source && videoSrc !== source) return;
    const originalMuted = video.muted;
    const originalPlaysInline = video.playsInline;
    const attempt = {};
    latestAttempts.set(video, attempt);
    function onFailure() {
      // An older play() rejection must not invalidate a later attempt.
      if (latestAttempts.get(video) !== attempt) return;
      failedVideos.set(video, videoSrc);
      if (source && previewUrl(card) === source) {
        failedCards.set(card, { source, link, href: link.href });
      }
      const activeSrc = video.currentSrc || video.src;
      if (activeSrc !== videoSrc) return;
      if (video.dataset && video.dataset.niraPreviewCreated === "1") {
        if (typeof video.remove === "function") video.remove();
      } else {
        // Return ownership of a real HTML5 player to the page after failure.
        video.muted = originalMuted;
        video.playsInline = originalPlaysInline;
      }
    }
    video.muted = true;
    video.playsInline = true;
    let result;
    try { result = video.play(); } catch (_) { onFailure(); return; }
    // An asynchronous failure can only be known after the initial click.
    // The next click on the same card/source/link will navigate normally.
    event.preventDefault();
    event.stopImmediatePropagation();
    if (result && typeof result.catch === "function") {
      result.catch(onFailure);
    }
  }
  document.addEventListener("click", playPreview, { capture: true });
})();
