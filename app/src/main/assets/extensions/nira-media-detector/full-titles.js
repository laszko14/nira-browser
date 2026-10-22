"use strict";

// Nira v1104 — reveal original (already present) titles next to video thumbnails.
// Pure presentation: no external requests, no page script execution, no title invention.
(() => {
  const CLASS = "nira-full-video-title";
  const CSS = "." + CLASS + " { white-space: normal !important; text-overflow: clip !important;" +
    " overflow: visible !important; -webkit-line-clamp: unset !important;" +
    " line-clamp: unset !important; max-height: none !important;" +
    " height: auto !important; overflow-wrap: anywhere !important; }";
  const VIDEO_HINT = /(?:video|film|movie|episode|serial|watch|player|thumb|related|recommend|suggest)/i;
  const TITLE_SELECTOR = [
    "[data-video-title]", "[itemprop='name']", ".video-title", ".video-name",
    ".movie-title", ".film-title", "[class*='videoTitle']",
    "[class*='video-title']", "[class*='thumb-title']",
    "h2", "h3", "h4", "[title]", "a"
  ].join(",");
  let scheduled = false;
  function apply() {
    scheduled = false;
    if (!document.documentElement) return;
    if (!document.getElementById("nira-full-titles-style")) {
      const style = document.createElement("style");
      style.id = "nira-full-titles-style";
      style.textContent = CSS;
      (document.head || document.documentElement).appendChild(style);
    }
    const images = document.querySelectorAll("img");
    for (let i = 0; i < Math.min(images.length, 180); i++) {
      const img = images[i];
      const card = img.closest("article,li,figure,[class*='video'],[class*='movie'],[class*='thumb'],[class*='item']");
      if (!card || card.closest("header,nav,footer") || !img.isConnected) continue;
      const link = img.closest("a[href]") || card.querySelector("a[href]");
      const href = link && link.getAttribute("href") || "";
      const hint = [card.className, card.id, link && link.className, href].filter(v => typeof v === "string").join(" ");
      if (!VIDEO_HINT.test(hint)) continue;
      const titles = card.querySelectorAll(TITLE_SELECTOR);
      for (let n = 0; n < Math.min(titles.length, 8); n++) {
        const element = titles[n];
        // Do not expand the entire card or an icon-only link.
        const text = (element.textContent || "").trim();
        if (text.length < 16 || text.length > 1200 || element.contains(img)) continue;
        if (element.tagName === "A" && element.querySelector("img")) continue;
        if (element.closest("button,nav,footer") || element.isContentEditable) continue;
        element.classList.add(CLASS);
      }
    }
  }
  const queue = () => {
    if (!scheduled) {
      scheduled = true;
      setTimeout(apply, 350);
    }
  };
  const observer = new MutationObserver(mutations => {
    // Ignore our own style/class modifications to avoid an observer loop.
    if (mutations.some(m => m.type === "childList" &&
        Array.from(m.addedNodes).some(n => n.nodeType === 1 &&
          n.id !== "nira-full-titles-style"))) queue();
  });
  observer.observe(document, { childList: true, subtree: true });
  document.addEventListener("DOMContentLoaded", queue, { once: true });
  window.addEventListener("pageshow", queue, { passive: true });
  queue();
})();
