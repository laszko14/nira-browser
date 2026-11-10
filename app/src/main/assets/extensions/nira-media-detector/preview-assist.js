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
  // Hover previews are transient and owned by this helper only.
  const hoverSessions = new WeakMap(); // card -> playback owned by hover
  let touchTrack = null;
  let ignoredTouchClick = null;


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

  // Only a real hovering pointer may start an automatic *muted* preview.
  // Touch can synthesize pointerover at contact: it is NOT hover.
  function stopHover(card, session) {
    if (hoverSessions.get(card) !== session) return;
    hoverSessions.delete(card); // Invalidates late play() rejection.
    const video = session.video;
    const current = video.currentSrc || video.src;
    if (current !== session.source) return; // Site changed media owner.
    if (typeof video.pause === "function") {
      try { video.pause(); } catch (_) {}
    }
    if (session.created) {
      if (typeof video.remove === "function") video.remove();
    } else {
      video.muted = session.originalMuted;
      video.playsInline = session.originalInline;
    }
  }

  function onPointerOver(event) {
    if (!event.isTrusted || event.pointerType !== "mouse" ||
        (typeof event.buttons === "number" && event.buttons !== 0) ||
        !event.target || typeof event.target.closest !== "function" ||
        event.target.closest("video") ||
        event.target.closest("button,[role='button']")) return;
    const thumb = event.target.closest("img,[class*='thumb'],[class*='Thumb']");
    const card = thumb && thumb.closest(CARD);
    if (!card || card.closest("nav,header,footer") || !card.querySelector ||
        !card.querySelectorAll || hoverSessions.has(card)) return;
    if (event.relatedTarget && typeof card.contains === "function" &&
        card.contains(event.relatedTarget)) return;
    const source = previewUrl(card);
    if (!source) return; // Never guess media from an unrelated link.
    const native = card.querySelector("video");
    if (native && native.autoplay && native.muted && native.playsInline) return;
    const video = findPreviewVideo(card, source);
    if (!video || !video.paused || video.isConnected === false ||
        typeof video.play !== "function") return;
    const videoSrc = video.currentSrc || video.src;
    if (videoSrc !== source || failedVideos.get(video) === videoSrc) return;
    const session = {
      video, source, originalMuted: video.muted,
      originalInline: video.playsInline,
      created: !!(video.dataset && video.dataset.niraPreviewCreated === "1")
    };
    hoverSessions.set(card, session);
    video.muted = true;
    video.playsInline = true;
    let result;
    try { result = video.play(); } catch (_) { stopHover(card, session); return; }
    if (result && typeof result.catch === "function") {
      result.catch(() => stopHover(card, session));
    }
  }

  function onPointerOut(event) {
    if (event.pointerType !== "mouse" || !event.target ||
        typeof event.target.closest !== "function") return;
    const card = event.target.closest(CARD);
    if (!card) return;
    const session = hoverSessions.get(card);
    if (!session || (event.relatedTarget &&
        typeof card.contains === "function" && card.contains(event.relatedTarget))) return;
    stopHover(card, session);
  }

  // Never cancel touchstart/touchmove/touchend: preserve site scrolling,
  // long-press context menus, pinch zoom and native synthetic click behavior.
  // A short stationary tap is handled by the existing click path.
  function changedTouch(event, id) {
    return Array.from(event.changedTouches || []).find(t => t.identifier === id);
  }
  function onTouchStart(event) {
    touchTrack = null;
    ignoredTouchClick = null;
    if (!event.isTrusted || !event.touches || event.touches.length !== 1 ||
        !event.target || typeof event.target.closest !== "function" ||
        event.target.closest("button,[role='button']")) return;
    const thumb = event.target.closest("img,[class*='thumb'],[class*='Thumb']");
    const card = thumb && thumb.closest(CARD);
    const point = (event.changedTouches || [])[0];
    if (!card || !point) return;
    touchTrack = {
      card, id: point.identifier, x: point.clientX, y: point.clientY,
      at: event.timeStamp, moved: false
    };
  }
  function onTouchMove(event) {
    if (!touchTrack) return;
    if (!event.touches || event.touches.length !== 1) {
      touchTrack.moved = true;
      return;
    }
    const point = changedTouch(event, touchTrack.id);
    if (point && ((point.clientX - touchTrack.x) ** 2 +
        (point.clientY - touchTrack.y) ** 2 > 144)) touchTrack.moved = true;
  }
  function finishTouch(event, cancelled) {
    if (!touchTrack) return;
    const track = touchTrack;
    touchTrack = null;
    const point = changedTouch(event, track.id);
    const moved = point && ((point.clientX - track.x) ** 2 +
      (point.clientY - track.y) ** 2 > 144);
    const duration = event.timeStamp - track.at;
    if (cancelled || track.moved || moved || duration > 650) {
      ignoredTouchClick = { card: track.card, at: event.timeStamp };
    }
  }
  function ignoreGestureClick(event) {
    if (!ignoredTouchClick) return false;
    const guard = ignoredTouchClick;
    ignoredTouchClick = null;
    const elapsed = event.timeStamp - guard.at;
    return Number.isFinite(elapsed) && elapsed >= 0 && elapsed <= 1000 &&
      event.target && typeof event.target.closest === "function" &&
      event.target.closest(CARD) === guard.card;
  }

  function playPreview(event) {
    if (ignoreGestureClick(event)) return;
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
    // A site's ordinary button/role=button owns its click, even when it
    // contains an image inside a card with a declared preview source.
    if (control && !explicitPreviewControl) return;
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
    // A muted autoplay preview is already owned by the website. When it is
    // temporarily paused/loading, do not steal its linked thumbnail click.
    // A deliberate Preview control remains an explicit independent action.
    if (!explicitPreviewControl && video.autoplay && video.muted &&
        video.playsInline) return;
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
  document.addEventListener("pointerover", onPointerOver, { capture: true });
  document.addEventListener("pointerout", onPointerOut, { capture: true });
  document.addEventListener("touchstart", onTouchStart, { capture: true, passive: true });
  document.addEventListener("touchmove", onTouchMove, { capture: true, passive: true });
  document.addEventListener("touchend", event => finishTouch(event, false), { capture: true, passive: true });
  document.addEventListener("touchcancel", event => finishTouch(event, true), { capture: true, passive: true });
})();
