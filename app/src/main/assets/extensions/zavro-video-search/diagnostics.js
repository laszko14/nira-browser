/*
 * Zavro Video Search diagnostics v1 - read-only WebExtension observations.
 * Dormant until a verified native-port diag_start. No network, persistence,
 * new permissions, automatic page actions or collection of form values.
 */
(function () {
  "use strict";
  if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
  const MAX_EVENTS = 256, MAX_BYTES = 192 * 1024, MAX_MS = 45 * 60 * 1000;
  const idPattern = /^[0-9a-f]{32}$/, sessionPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const sensitive = /(?:password|passwd|pwd|secret|token|api[_-]?key|auth|authorization|cookie|session|csrf|nonce|jwt|sid|otp|code|state)/i;
  const pair = /\b(password|passwd|pwd|secret|access_token|refresh_token|id_token|api_key|authorization|cookie|session(?:id)?|csrf(?:token)?)\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s&;<>]+)/gi;
  const bearer = /\bBearer\s+[^\s"'<>]+/gi;
  const jwt = /\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?:\.[A-Za-z0-9_-]+)?\b/g;
  const stages = new Set(["FORM","RESULTS_REGION","CARDS","METADATA","SPA","PAGINATION","BRIDGE"]);
  const decisions = new Set(["SEEN","ACCEPTED","REJECTED","UNCERTAIN","WAITING","FAILED","CANCELLED","INTERRUPTED"]);
  const fieldKeys = new Set(["PAGE_URL","FILM_URL","SEARCH_QUERY","TITLE","DURATION","DATE","SELECTOR","DOM_FRAGMENT","DETAIL"]);
  const countKeys = new Set(["VISIBLE_CARDS","CANDIDATES","ACCEPTED_FILMS","UNIQUE_FILMS","SAVED_FILMS","DISPLAYED_FILMS","PAGES","REQUESTS"]);
  let active = null, serial = 0, dropped = 0, bytes = 0, records = [];
  const safeText = (value, max = 150) => String(value ?? "")
    .replace(bearer, "Bearer [REDACTED]")
    .replace(pair, (_m, key) => key + "=[REDACTED]")
    .replace(jwt, "[REDACTED]")
    .replace(/[\x00-\x08\x0b-\x1f\x7f]/g, " ").slice(0, max);
  function safeUrl(text) {
    try {
      const url = new URL(text, location.href);
      if (!["http:","https:"].includes(url.protocol)) return "[INVALID_URL]";
      url.username = ""; url.password = ""; url.hash = "";
      for (const name of Array.from(url.searchParams.keys())) {
        if (sensitive.test(name)) url.searchParams.set(name, "[REDACTED]");
      }
      return safeText(url.href, 1200);
    } catch (_) { return "[INVALID_URL]"; }
  }
  function cleanFields(value) {
    const fields = {};
    if (!value || typeof value !== "object") return fields;
    for (const [key, text] of Object.entries(value).slice(0, 9)) {
      if (!fieldKeys.has(key) || typeof text !== "string") continue;
      fields[key] = key.endsWith("_URL") ? safeUrl(text) : safeText(text,
        key === "DOM_FRAGMENT" ? 768 : 300);
    }
    return fields;
  }
  function cleanCounts(value) {
    const counts = {};
    if (!value || typeof value !== "object") return counts;
    for (const [key, number] of Object.entries(value).slice(0, 8)) {
      if (countKeys.has(key) && Number.isSafeInteger(number) && number >= 0)
        counts[key] = number;
    }
    return counts;
  }
  function encodeSize(record) {
    const value = JSON.stringify(record);
    return typeof TextEncoder === "function" ?
      new TextEncoder().encode(value).length : value.length * 3;
  }
  function expire() {
    if (active && Date.now() - active.since >= MAX_MS) {
      active.stopped = true;
      active.stopReason = "TIME_LIMIT";
    }
  }
  function observe(value) {
    if (!active || active.stopped) return false;
    expire();
    if (active.stopped || !value || !stages.has(value.stage) ||
        !decisions.has(value.decision)) return false;
    serial++;
    const event = {
      schemaVersion: 1, sequence: serial, timeMs: Date.now(),
      documentId: active.documentId, navigationRevision: active.revision,
      requestId: idPattern.test(value.requestId || "") ? value.requestId : null,
      stage: value.stage, claim: "OBSERVATION",
      decision: value.decision, reason: safeText(value.reason, 80),
      counts: cleanCounts(value.counts), fields: cleanFields(value.fields)
    };
    const size = encodeSize(event);
    if (records.length >= MAX_EVENTS || bytes + size > MAX_BYTES) {
      dropped++;
      return false;
    }
    records.push(event);
    bytes += size;
    return true;
  }
  function start(sessionId, documentId, revision) {
    if (!sessionPattern.test(sessionId || "") || !idPattern.test(documentId || "") ||
        !Number.isInteger(revision) || revision < 0 || revision > 1_000_000)
      return false;
    // A different diagnostic session cannot silently inherit buffered evidence.
    if (active && active.sessionId !== sessionId) return false;
    if (active?.stopped) return false;
    if (!active) {
      active = {sessionId, documentId, revision, since:Date.now(), stopped:false};
      serial = 0; dropped = 0; bytes = 0; records = [];
    } else { active.documentId = documentId; active.revision = revision; }
    return true;
  }
  function route(documentId, revision, url, reason) {
    if (!active || active.stopped) return;
    observe({stage:"SPA",decision:"SEEN",reason:reason || "SAME_ORIGIN_ROUTE_CHANGE",
      fields:{PAGE_URL:safeUrl(url)}});
    if (idPattern.test(documentId) && Number.isInteger(revision)) {
      active.documentId = documentId; active.revision = revision;
    }
  }
  // At most four events per read, so bridge transport stays below 9000 chars.
  // Cursor never clears data; a later stage will own exact delivery/ACK.
  function read(cursor, documentId) {
    if (!active || documentId !== active.documentId ||
        !Number.isSafeInteger(cursor) || cursor < 0) return null;
    expire();
    const events = [];
    let budget = 900;
    for (const event of records) {
      if (event.sequence <= cursor) continue;
      const size = encodeSize(event);
      if (events.length === 4 || budget + size > 7000) break;
      events.push(event);
      budget += size;
    }
    return {schemaVersion:1,sessionId:active.sessionId,
      attempted:serial,captured:records.length,dropped,limited:dropped > 0,
      oldestSequence:records[0]?.sequence ?? null,
      latestSequence:serial,stopped:active.stopped,
      stopReason:active.stopReason || null,events};
  }
  function stop(sessionId) {
    if (!active || active.sessionId !== sessionId || active.stopped) return false;
    active.stopped = true; active.stopReason = "NATIVE_STOP";
    return true;
  }
  function close() {
    active = null; serial = 0; dropped = 0; bytes = 0; records = [];
  }
  // No innerHTML, clone, full document text, input/value, hidden node, script,
  // session attributes, inline handlers or password-bearing ancestors.
  function domFragment(node, depth = 0) {
    if (!node || !node.tagName || depth > 2 ||
        node.closest?.('input,textarea,script,style,[hidden],[inert],[aria-hidden="true"],[type="password"],[data-private]'))
      return "";
    const tag = String(node.tagName).toLowerCase();
    if (!/^(form|main|section|article|div|ul|ol|li|a|button|h[1-6]|nav|span)$/.test(tag))
      return "";
    const attrs = [];
    for (const name of ["id","class","role","data-testid"]) {
      const raw = node.getAttribute?.(name);
      if (typeof raw === "string" && raw && !sensitive.test(name) &&
          !sensitive.test(raw)) attrs.push(name + '="' + safeText(raw, 45).replace(/["<>]/g, "") + '"');
    }
    if (tag === "a") {
      const href = node.getAttribute?.("href");
      if (typeof href === "string") attrs.push('href="' + safeUrl(href).replace(/["<>]/g, "") + '"');
    }
    // Only structure, not textContent: forms/cards can include page secrets.
    const children = depth < 2 ? Array.from(node.children || []).slice(0, 3)
      .map(child => domFragment(child, depth+1)).filter(Boolean).join("") : "";
    return ("<" + tag + (attrs.length ? " " + attrs.join(" ") : "") + ">" +
      children + "</" + tag + ">").slice(0, 768);
  }
  function snapshot(documentId, requestId, source) {
    if (!active || active.stopped || documentId !== active.documentId ||
        !idPattern.test(requestId || "")) return false;
    const samples = [
      ["FORM", 'form:not([method="post"]):not(:has(input[type="password"]))'],
      ["RESULTS_REGION", '[data-search-results],.search-results,.results,[role="list"]'],
      ["PAGINATION", '[data-pagination],nav[aria-label],.pagination']
    ];
    for (const [stage, selector] of samples) {
      let node = null;
      try { node = source.querySelector(selector); } catch (_) { /* optional */ }
      if (!node || node.closest?.('[hidden],[inert],[aria-hidden="true"]')) continue;
      observe({stage,decision:"UNCERTAIN",reason:"ERROR_DOM_SAMPLE",
        requestId,fields:{DOM_FRAGMENT:domFragment(node),DETAIL:"STRUCTURE_ONLY"}});
    }
    return true;
  }
  Object.defineProperty(window, "__zavroVideoDiagnostics", {
    configurable:false, enumerable:false, writable:false,
    value:Object.freeze({start,observe,read,stop,close,route,snapshot,
      active:() => !!active && !active.stopped, session:() => active?.sessionId || null})
  });
})();
