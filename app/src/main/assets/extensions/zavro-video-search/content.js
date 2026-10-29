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
  let ready = false, busy = false, picker = null, manuallySelectedInput = null;
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
    // Stable unique class, but never build an nth-child/positional selector.
    for (const cls of Array.from(element.classList || []).slice(0, 6)) {
      if (cls.length > 60) continue;
      const candidate = tag + "." + CSS.escape(cls);
      try {
        if (document.querySelectorAll(candidate).length === 1) return candidate;
      } catch (_) { /* invalid selector */ }
    }
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
  // The optional, user-operated selector picker is active only while a
  // specific native "pick" request is pending. No global DOM listeners idle.
  function manualInput(input) {
    if (!input || input.tagName !== "INPUT" || !visible(input)) return false;
    if (!["text", "search"].includes((input.type || "").toLowerCase())) return false;
    const form = input.form;
    if (form && !safeForm(form)) return false;
    if (blocked.test((input.name || "") + " " +
        (input.id || "") + " " + (input.placeholder || ""))) return false;
    // Explicitly chosen SPA search fields are allowed only inside a
    // role=search container or when their input type is search.
    return !!form || input.type === "search" || !!input.closest('[role="search"]');
  }
  function manualSubmit(button, input) {
    if (!input || !manualInput(input) || !button || !button.matches(
      'button,input[type="submit"]')) return false;
    if (button.disabled) return false;
    if (input.form) {
      if (!safeForm(input.form) || button.form !== input.form) return false;
    } else {
      const searchArea = input.closest('[role="search"]');
      if (!searchArea || !searchArea.contains(button) || button.form) return false;
    }
    if (blocked.test((button.innerText || button.value || "") + " " +
        (button.getAttribute("aria-label") || ""))) return false;
    return !!selectorFor(button);
  }
  function manualCard(node) {
    let element = node;
    for (let level = 0; element && level < 6; level++, element = element.parentElement) {
      if (!element.querySelector || !element.querySelector("a[href]")) continue;
      const tag = element.tagName.toLowerCase();
      for (const cls of Array.from(element.classList || []).slice(0, 5)) {
        if (cls.length > 60) continue;
        const selector = tag + "." + CSS.escape(cls);
        try {
          const matches = Array.from(document.querySelectorAll(selector)).slice(0, 25);
          // Manual choice may legitimately point to the only film card
          // visible on a sparsely populated results page.
          if (matches.length >= 1 && matches.filter(e => e.querySelector("a[href]")).length >= 1)
            return selector;
        } catch (_) {}
      }
    }
    return null;
  }
  function manualNext(node) {
    const el = node.closest('a[href],button');
    if (!el || !visible(el) || el.disabled) return null;
    const selector = selectorFor(el);
    if (!selector) return null;
    if (el.tagName === "A") {
      try {
        const url = new URL(el.href);
        return url.origin === location.origin && !url.username && !url.password ?
          { selector, pagination: "NEXT_LINK" } : null;
      } catch (_) { return null; }
    }
    if (el.tagName === "BUTTON" && el.type !== "submit")
      return { selector, pagination: "LOAD_MORE" };
    return null;
  }
  function cancelPicker() {
    const state = picker;
    if (!state) return;
    picker = null;
    clearTimeout(state.timeout);
    document.removeEventListener("click", state.choose, true);
    document.removeEventListener("pointerdown", state.blockPageHandlers, true);
    document.removeEventListener("touchstart", state.blockPageHandlers, true);
    document.removeEventListener("keydown", state.key, true);
    state.banner.remove();
  }
  function pick(command) {
    if (picker || busy || !ready || location.href !== startUrl) return;
    const kind = command.pickKind;
    const titles = {
      input: "Wskaż pole wyszukiwania",
      submit: "Wskaż przycisk Szukaj",
      cards: "Wskaż kartę filmu w wynikach",
      next: "Wskaż przycisk następnej strony"
    };
    if (!Object.prototype.hasOwnProperty.call(titles, kind)) return;
    const banner = document.createElement("div");
    banner.setAttribute("data-zavro-picker", "true");
    banner.style.cssText = "position:fixed;z-index:2147483647;top:8px;left:8px;right:8px;" +
      "background:#16242d;color:white;padding:12px;font:14px sans-serif;" +
      "border:2px solid #33c9bd;border-radius:10px;box-shadow:0 3px 15px #0008;";
    const title = document.createElement("div");
    title.textContent = "Zavro — " + titles[kind] + ". Dotknij elementu na stronie.";
    banner.appendChild(title);
    const controls = document.createElement("div");
    controls.style.cssText = "display:flex;gap:8px;margin-top:8px";
    function action(label, event) {
      const button = document.createElement("button");
      button.textContent = label;
      button.style.cssText = "font:14px sans-serif;padding:7px 12px;min-height:36px;";
      button.addEventListener("click", e => {
        e.preventDefault(); e.stopPropagation();
        post(event, command.requestId, { pickKind: kind });
        cancelPicker();
      });
      controls.appendChild(button);
    }
    if (kind !== "input") action("Pomiń", "pick_skipped");
    action("Anuluj", "pick_cancelled");
    banner.appendChild(controls);
    document.documentElement.appendChild(banner);

    const choose = function (event) {
      if (!picker || location.href !== startUrl || !event.isTrusted) return;
      if (event.target.closest("[data-zavro-picker]")) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      const element = event.target;
      let selector = null, formSafe = false, pagination = "NONE";
      if (kind === "input") {
        const input = element.closest("input");
        if (manualInput(input)) {
          selector = selectorFor(input);
          formSafe = safeForm(input.form);
          if (selector) manuallySelectedInput = input;
        }
      } else if (kind === "submit") {
        const button = element.closest('button,input[type="submit"]');
        if (manualSubmit(button, manuallySelectedInput)) selector = selectorFor(button);
      } else if (kind === "cards") {
        selector = manualCard(element);
      } else if (kind === "next") {
        const next = manualNext(element);
        if (next) { selector = next.selector; pagination = next.pagination; }
      }
      if (!selector || selector.length > 160) {
        title.textContent = "Nie można jednoznacznie wskazać elementu. Wybierz inny.";
        return;
      }
      post("picked", command.requestId, { pickKind: kind, selector, formSafe, pagination });
      cancelPicker();
    };
    const key = function (event) {
      if (event.key === "Escape") {
        event.preventDefault();
        post("pick_cancelled", command.requestId, { pickKind: kind });
        cancelPicker();
      }
    };
    const timeout = setTimeout(function () {
      post("pick_cancelled", command.requestId, { pickKind: kind });
      cancelPicker();
    }, 90_000);
    const blockPageHandlers = function (event) {
      if (picker && !event.target.closest("[data-zavro-picker]"))
        event.stopImmediatePropagation();
    };
    picker = { banner, choose, key, timeout, blockPageHandlers };
    // Prevent sites' pointerdown listeners from acting before our click
    // capture, without preventing the browser from generating the click.
    document.addEventListener("pointerdown", blockPageHandlers, true);
    document.addEventListener("touchstart", blockPageHandlers, true);
    document.addEventListener("click", choose, true);
    document.addEventListener("keydown", key, true);
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
    if ((!validInput(field) && !manualInput(field)) ||
        selectorFor(field) !== command.inputSelector) return "stale";
    const form = field.form;
    if (!safeForm(form) && command.submission !== "CLICK_SUBMIT") return "unsafe";
    let button = null;
    if (command.submission === "CLICK_SUBMIT") {
      button = document.querySelector(command.submitSelector);
      if (!button || button.disabled ||
          (searchButton(field) !== command.submitSelector &&
           !manualSubmit(button, field))) return "stale";
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
  // An explicit, read-only health check. No clicks, network, or DOM edits.
  // Limited to three short observations so SPA hydration has a fair chance.
  function checkConfiguration(command) {
    function bySelector(selector) {
      if (typeof selector !== "string" || !selector || selector.length > 160)
        return null;
      try { return Array.from(document.querySelectorAll(selector)).slice(0, 31); }
      catch (_) { return null; }
    }
    function result(status, reason, cards) {
      return { status, reason, matchedCards: Math.min(30, cards || 0) };
    }
    const inputs = bySelector(command.inputSelector);
    if (!inputs) return result("stale", "invalid_selector", 0);
    if (inputs.length === 0) return result("stale", "input_missing", 0);
    if (inputs.length !== 1) return result("stale", "input_ambiguous", 0);
    const input = inputs[0];
    if (!input || !input.matches("input") ||
        !["search", "text"].includes((input.type || "").toLowerCase()))
      return result("stale", "invalid_selector", 0);
    if (input.form && !safeForm(input.form))
      return result("unsafe", "unsafe_form", 0);
    if (!validInput(input) && !manualInput(input))
      return result("stale", "input_missing", 0);

    if (command.submission === "FORM_SUBMIT") {
      if (!safeForm(input.form)) return result("unsafe", "unsafe_form", 0);
    } else if (command.submission === "CLICK_SUBMIT") {
      const buttons = bySelector(command.submitSelector);
      if (!buttons) return result("stale", "invalid_selector", 0);
      if (buttons.length !== 1 || !manualSubmit(buttons[0], input))
        return result("stale", "submit_missing", 0);
    } else return result("unsafe", "unsafe_form", 0);

    // Home/search form pages often have ZERO results before searching.
    // Never mark them broken just because their result cards are absent.
    if (command.resultsCardSelector === null)
      return result("unverified", "no_results", 0);
    const cards = bySelector(command.resultsCardSelector);
    if (!cards) return result("stale", "cards_invalid", 0);
    const linked = cards.filter(node => node.querySelector &&
        node.querySelector("a[href]")).length;
    if (!linked) return result("unverified", "no_results", 0);
    if (command.pagination !== "NONE") {
      const pagination = bySelector(command.nextPageSelector);
      if (!pagination) return result("stale", "invalid_selector", linked);
      if (pagination.length === 0)
        return result("unverified", "pagination_unknown", linked);
    }
    return result("valid", "ok", linked);
  }
  async function validateStored(command) {
    const timings = [0, 350, 800]; // 3 attempts, <= 1.2 seconds total
    let last, attempts = 0;
    for (let i = 0; i < timings.length; i++) {
      if (!ready || location.href !== startUrl || picker) return;
      if (timings[i]) await new Promise(resolve => setTimeout(resolve, timings[i]));
      if (!ready || location.href !== startUrl || picker) return;
      last = checkConfiguration(command);
      attempts++;
      // Do not retry on valid, unsafe, malformed, or ambiguous selectors.
      if (last.status === "valid" || last.status === "unsafe" ||
          ["invalid_selector", "input_ambiguous", "cards_invalid"].includes(last.reason))
        break;
    }
    if (last) post("validation", command.requestId,
      Object.assign({ attempts }, last));
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
        } else if (message.event === "pick" &&
            keys === "documentId,event,pickKind,requestId,schemaVersion") {
          pick(message);
        } else if (message.event === "validate" &&
            keys === "documentId,event,inputSelector,nextPageSelector,pagination,requestId," +
                    "resultsCardSelector,schemaVersion,submission,submitSelector" && !picker) {
          busy = true;
          validateStored(message).catch(function () {
            // Invalid DOM or page unload: no success/repair claim.
          }).finally(function () { busy = false; });
        } else if (message.event === "search" &&
            keys === "documentId,event,inputSelector,query,requestId,schemaVersion,submission,submitSelector") {
          busy = true;
          const result = runSearch(message);
          if (result !== "submitted") post("search_failed", message.requestId, { reason: result });
          busy = false;
        }
      }
    });
    port.onDisconnect.addListener(function () { ready = false; cancelPicker(); });
    window.addEventListener("pagehide", cancelPicker, { once: true });
    port.postMessage({ schemaVersion: 1, event: "hello", documentId, pageUrl: startUrl });
  } catch (_) { ready = false; }
})();
