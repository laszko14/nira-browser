"use strict";

// Reveal the original captions on video thumbnail grids. No requests,
// document rewriting, synthetic titles, or playback changes.
(() => {
  const TITLE = "nira-full-video-title";
  const WRAPPER = "nira-full-video-title-wrapper";
  const STYLE_ID = "nira-full-titles-style";
  const CSS = "." + TITLE + ",." + WRAPPER + " {" +
    "white-space: normal !important; text-overflow: clip !important;" +
    "overflow: visible !important; -webkit-line-clamp: unset !important;" +
    "line-clamp: unset !important; max-height: none !important;" +
    "height: auto !important; overflow-wrap: anywhere !important; }";
  const VIDEO_HINT = /(?:video|film|movie|episode|serial|watch|player|thumb|related|recommend|suggest|clip|odcinek|ogladaj|oglądaj)/i;
  const CARD_SELECTOR = "article,li,figure,[class*='video'],[class*='Video'],[class*='movie'],[class*='thumb'],[class*='Thumb'],[class*='card'],[class*='Card'],[class*='item'],[class*='Item']";
  const TITLE_SELECTOR = [
    "[data-video-title]", "[itemprop='name']", "[class*='title']",
    "[class*='Title']", "[class*='caption']", "[class*='Caption']",
    "[class*='name']", "h2", "h3", "h4", "a[href]", "figcaption", "[title]"
  ].join(",");
  let queued = false;
  let running = false;

  function cssTruncates(node) {
    try {
      const style = getComputedStyle(node);
      return style.textOverflow === "ellipsis" ||
        style.whiteSpace === "nowrap" ||
        (style.webkitLineClamp && style.webkitLineClamp !== "none" &&
          style.webkitLineClamp !== "unset") ||
        (style.overflow === "hidden" && node.scrollHeight > node.clientHeight + 1);
    } catch (_) { return false; }
  }

  function mediaCard(image) {
    const card = image.closest(CARD_SELECTOR) ||
      (image.closest("a[href]") && image.closest("a[href]").parentElement);
    if (!card || card.closest("header,nav,footer") || !card.isConnected) return null;
    const link = image.closest("a[href]") || card.querySelector("a[href]");
    const context = [card.className, card.id, link && link.className,
      link && link.getAttribute("href")].filter(x => typeof x === "string").join(" ");
    // Avoid altering unrelated product/search lists.
    const mediaLink = link && /\/(?:video|videos|watch|movie|movies|film|films|embed|play|view)\/|[?&](?:v|video|movie)=/i.test(link.getAttribute("href") || "");
    const timedCard = card.querySelector("video,source,[data-video-id],[class*='duration'],time");
    if (!VIDEO_HINT.test(context) && !mediaLink && !timedCard) return null;
    return card;
  }

  function originalTitle(element, image, card, visible) {
    // CSS cannot restore text that the site itself has replaced by ellipsis.
    // Only use an explicit longer original from this particular thumbnail card.
    const nodes = [element, element.closest("a[href]"), image,
      image.closest("a[href]"), card];
    let best = null;
    for (const node of nodes) {
      if (!node || typeof node.getAttribute !== "function") continue;
      for (const key of ["data-video-title", "data-title", "title", "aria-label", "alt"]) {
        const text = (node.getAttribute(key) || "").replace(/\s+/g, " ").trim();
        if (text.length < 12 || text.length > 600 || text.length <= visible.length + 5) continue;
        const prefix = visible.replace(/[.\\u2026]+$/g, "").trim().slice(0, 15).toLowerCase();
        if (prefix.length >= 8 && !text.toLowerCase().startsWith(prefix)) continue;
        if (!best || text.length > best.length) best = text;
      }
    }
    return best;
  }

  function expandTitle(element, image, card) {
    if (!element || element.contains(image) || element.isContentEditable ||
        element.closest("button,nav,footer") || element.querySelector("img,video")) return;
    const text = (element.textContent || "").trim();
    if (text.length < 8 || text.length > 1200) return;
    const original = originalTitle(element, image, card, text);
    if (original && element.children.length === 0 &&
        (/[.\\u2026]{2,}\s*$/.test(text) || text.length < original.length)) {
      element.textContent = original;
    }
    element.classList.add(TITLE);
    // Many sites clamp an OUTER title wrapper rather than the text node.
    // Expand only ancestors that do not also contain the thumbnail.
    let node = element.parentElement;
    for (let i = 0; node && node !== card && i < 7; i++, node = node.parentElement) {
      if (node.contains(image) || node.querySelector("img,video")) break;
      if (cssTruncates(node) || /(?:title|caption|name|truncate|ellipsis|line-clamp)/i.test(
        String(node.className || ""))) node.classList.add(WRAPPER);
    }
  }

  function apply() {
    queued = false;
    if (running || !document.documentElement) return;
    running = true;
    try {
      if (!document.getElementById(STYLE_ID)) {
        const style = document.createElement("style");
        style.id = STYLE_ID;
        style.textContent = CSS;
        (document.head || document.documentElement).appendChild(style);
      }
      const images = document.querySelectorAll("img,video[poster]");
      for (let i = 0; i < Math.min(images.length, 1000); i++) {
        const image = images[i];
        if (!image.isConnected) continue;
        const card = mediaCard(image);
        if (!card) continue;
        const titles = card.querySelectorAll(TITLE_SELECTOR);
        let handled = 0;
        for (const element of titles) {
          if (++handled > 24) break;
          // Nested matches must not cause the entire card to expand.
          if (element === card || element.contains(image)) continue;
          expandTitle(element, image, card);
        }
      }
    } finally {
      running = false;
    }
  }
  function queue() {
    if (queued) return;
    queued = true;
    setTimeout(apply, 300);
  }
  const observer = new MutationObserver(mutations => {
    if (mutations.some(m =>
      m.type === "characterData" ||
      (m.type === "attributes" && m.attributeName !== "class") ||
      (m.type === "childList" && Array.from(m.addedNodes).some(n =>
        n.nodeType === 1 && n.id !== STYLE_ID)))) queue();
  });
  observer.observe(document, {
    childList: true, subtree: true, characterData: true, attributes: true,
    attributeFilter: ["title", "aria-label", "data-title", "data-video-title", "alt"]
  });
  document.addEventListener("DOMContentLoaded", queue, { once: true });
  window.addEventListener("pageshow", queue, { passive: true });
  queue();
})();
