/*
 * Zavro Video Search — stage 3B. Runs only in top-level HTTP(S) documents.
 * No automatic DOM scans, no network interception, no playback, no downloads.
 * User explicitly invokes inspect, then (only if configured) search.
 */
(function () {
  "use strict";
  if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
  const bytes = new Uint8Array(16);
  try { crypto.getRandomValues(bytes); } catch (_) { return; }
  const documentId = Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
  const startUrl = location.href;
  let ready = false, busy = false;
  const limited = 80;
  const idPattern = /^[0-9a-f]{32}$/;
  const blocked = /login|log-in|signin|sign-in|password|register|checkout|payment|purchase|captcha/i;
  const searchTerms = /search|szukaj|find|query|wyszuk|buscar|suche|recherche/i;
  let port;

  function safeForm(form) {
    if (!form || form.tagName !== "FORM" || form.querySelector('input[type="password"]')) return false;
    if (blocked.test((form.id || "") + " " + (form.className || "") + " " +
      (form.getAttribute("action") || ""))) return false;
    const method = (form.getAttribute("method") || "GET").toUpperCase();
    if (method !== "GET") return false; // Never submit POST forms.
    try {
      const action = new URL(form.getAttribute("action") || location.href, location.href);
      return action.origin === location.origin &&
        /^https?:$/.test(action.protocol) && !action.username && !action.password;
    } catch (_) { return false; }
  }
  function visible(input) {
    if (!input || input.disabled || input.readOnly ||
        input.closest('[aria-hidden="true"],[hidden],[inert]')) return false;
    const rect = input.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }
  function validInput(input) {
    if (!input || input.tagName !== "INPUT" || !visible(input)) return false;
    const type = (input.getAttribute("type") || "text").toLowerCase();
    if (!["search", "text"].includes(type)) return false;
    const form = input.form;
    if (form && !safeForm(form)) return false;
    const name = input.name || "", id = input.id || "",
      placeholder = input.placeholder || "";
    if (blocked.test(name + " " + id + " " + placeholder)) return false;
    const role = (form?.getAttribute("role") || "") === "search";
    return type === "search" || role ||
      searchTerms.test(name + " " + id + " " + placeholder) ||
      /^(q|s)$/i.test(name);
  }
  function score(input) {
    const form = input.form;
    const name = input.name || "", id = input.id || "",
      placeholder = input.placeholder || "";
    let n = input.type === "search" ? 8 : 0;
    if (form?.getAttribute("role") === "search") n += 7;
    if (searchTerms.test(name + " " + id)) n += 5;
    if (/^(q|s)$/i.test(name)) n += 4;
    if (searchTerms.test(placeholder)) n += 3;
    if (safeForm(form)) n += 3;
    if (!form) n -= 3;
    return n;
  }
  function selectorFor(element) {
    if (element.id) {
      const s = "#" + CSS.escape(element.id);
      if (document.querySelectorAll(s).length === 1) return s;
    }
    const tag = element.tagName.toLowerCase();
    const name = element.getAttribute("name");
    if (name && name.length < 90) {
      const s = tag + '[name="' + CSS.escape(name) + '"]';
      if (document.querySelectorAll(s).length === 1) return s;
    }
    if (tag === "input" && element.type === "search" &&
        document.querySelectorAll('input[type="search"]').length === 1)
      return 'input[type="search"]';
    return null; // Ambiguous nodes are never mapped with nth-child.
  }
  function searchButton(input) {
    const form = input.form;
    const area = form || input.closest('[role="search"]');
    if (!area) return null;
    const candidates = Array.from(area.querySelectorAll('button,input[type="submit"]')).slice(0, 15);
    const valid = candidates.filter(b => !b.disabled && !blocked.test(
      (b.innerText || b.value || "") + " " + (b.getAttribute("aria-label") || "")));
    const known = valid.filter(b => searchTerms.test(
      (b.innerText || b.value || "") + " " + (b.getAttribute("aria-label") || "")));
    const chosen = known.length === 1 ? known[0] : (valid.length === 1 ? valid[0] : null);
    if (!chosen) return null;
    const selector = selectorFor(chosen);
    return selector && (!form || chosen.form === form) ? selector : null;
  }
  function detectResults() {
    // Only propose a selector if two or more card-like elements contain page links.
    const selectors = ['[data-testid="search-result"]', '.search-result',
      '.movie-card', '.film-card', '.video-card', '.result-item', '.search-results article'];
    const candidates = [];
    for (const selector of selectors) {
      const nodes = Array.from(document.querySelectorAll(selector)).slice(0, 12);
      const valid = nodes.filter(n => n.querySelector('a[href]')).length;
      if (valid >= 2) candidates.push(selector);
    }
    return candidates.length === 1 ? candidates[0] : null;
  }
  function detectPagination() {
    for (const [selector, kind] of [
      ['a[rel="next"]', 'NEXT_LINK'],
      ['.pagination a.next', 'NEXT_LINK'],
      ['button.load-more', 'LOAD_MORE']
    ]) if (document.querySelectorAll(selector).length === 1)
      return { pagination: kind, nextPageSelector: selector };
    return { pagination: "NONE", nextPageSelector: null };
  }
  function detect() {
    const candidates = Array.from(document.querySelectorAll("input")).slice(0, limited)
      .filter(validInput).map(input => ({ input, score: score(input) }))
      .filter(c => c.score >= 8).sort((a, b) => b.score - a.score);
    if (!candidates.length || (candidates[1] && candidates[1].score === candidates[0].score))
      return { status: "missing" };
    const input = candidates[0].input;
    const inputSelector = selectorFor(input);
    if (!inputSelector) return { status: "missing" };
    const button = searchButton(input);
    const form = input.form;
    // Search only with a safe GET form or an explicitly detected search button.
    if (!safeForm(form) && !button) return { status: "partial" };
    const result = detectResults();
    const submit = button ? "CLICK_SUBMIT" : "FORM_SUBMIT";
    const page = detectPagination();
    return {
      status: result ? "ready" : "partial",
      inputSelector, resultsCardSelector: result,
      submission: submit, submitSelector: button,
      pagination: page.pagination, nextPageSelector: page.nextPageSelector
    };
  }
  function post(event, requestId, extra) {
    if (!ready || location.href !== startUrl) return;
    try { port.postMessage(Object.assign({
      schemaVersion: 1, event, requestId, documentId
    }, extra)); } catch (_) { ready = false; }
  }
  function runSearch(command) {
    const query = command.query;
    if (typeof query !== "string" || !query.trim() || query.length > 250 ||
        /[\u0000-\u001f\u007f]/.test(query)) return "rejected";
    const field = document.querySelector(command.inputSelector);
    if (!validInput(field) || selectorFor(field) !== command.inputSelector) return "stale";
    const form = field.form;
    if (!safeForm(form) && command.submission !== "CLICK_SUBMIT") return "unsafe";
    let button = null;
    if (command.submission === "CLICK_SUBMIT") {
      button = document.querySelector(command.submitSelector);
      if (!button || button.disabled || searchButton(field) !== command.submitSelector)
        return "stale";
    } else if (command.submission !== "FORM_SUBMIT" || !safeForm(form)) return "unsafe";
    // Use native setter for sites with controlled JS input state.
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
    setter.call(field, query);
    field.dispatchEvent(new Event("input", { bubbles: true }));
    field.dispatchEvent(new Event("change", { bubbles: true }));
    // The acknowledgment precedes GET navigation, which destroys the port.
    post("submitted", command.requestId, {});
    setTimeout(function () {
      if (location.href !== startUrl || !document.contains(field)) return;
      try {
        if (button) button.click();
        else if (form.requestSubmit) form.requestSubmit();
        else form.submit();
      } catch (_) { /* No retries; site may need manual configuration. */ }
    }, 50);
    return "submitted";
  }
  try {
    port = browser.runtime.connectNative("zavro.video.search");
    if (!port) return;
    port.onMessage.addListener(function (message) {
      if (!message || typeof message !== "object" || Array.isArray(message) ||
          message.schemaVersion !== 1 || message.documentId !== documentId ||
          location.href !== startUrl) return;
      const keys = Object.keys(message).sort().join(",");
      if (message.event === "bound" && keys === "documentId,event,schemaVersion") {
        ready = true;
      } else if (message.event === "ping" && ready &&
                 keys === "documentId,event,schemaVersion") {
        try { port.postMessage({ schemaVersion: 1, event: "pong", documentId }); }
        catch (_) { ready = false; }
      } else if (ready && !busy && idPattern.test(message.requestId || "")) {
        if (message.event === "inspect" && keys === "documentId,event,requestId,schemaVersion") {
          busy = true;
          const found = detect();
          post("inspection", message.requestId, found);
          busy = false;
        } else if (message.event === "search" &&
            keys === "documentId,event,inputSelector,query,requestId,schemaVersion,submission,submitSelector") {
          busy = true;
          const result = runSearch(message);
          if (result !== "submitted") post("search_failed", message.requestId, { reason: result });
          busy = false;
        }
      }
    });
    port.onDisconnect.addListener(function () { ready = false; });
    port.postMessage({ schemaVersion: 1, event: "hello", documentId, pageUrl: startUrl });
  } catch (_) { ready = false; }
})();
