/*
 * Zavro preview diagnostics stage 2. DORMANT until a native START.
 * No raw DOM, text, source URLs, credentials, media playback or requests.
 * Runs in the extension isolated world; observes only opt-in interactions.
 */
(function () {
  "use strict";
  const APP = "zavro.preview.forensics";
  const SCRIPT_VERSION = "1.0.5"; // Must match manifest and native handshake.
  const MAX_EVENTS = 160, MAX_NODES = 40, MAX_SUBFRAME_NODES = 16;
  const MAX_ROOTS = 6, MAX_INSERTED_SCAN = 48, MAX_INSERTED_SAMPLES = 3;
  const MAX_DURATION = 15 * 60 * 1000;
  const PAGE_EVENTS = ["pointerdown","pointerover","touchstart","click",
    "play","pause","playing","waiting","stalled","loadedmetadata","error"];
  const MUTATION_CONFIG = {subtree:true,childList:true,attributes:true,
    attributeFilter:["style","class","hidden","poster","src","controls","autoplay","role"]};
  const sessionPattern = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const metricsKeys = new Set(["frame","shadow","readyState","networkState","paused",
    "muted","autoplay","controls","loop","duration","currentTime","errorCode","width",
    "height","x","y","z","display","visibility","pointerEvents","poster","preload",
    "playsInline","dropped","count","node","parent","hasId","hasClass","hasAria","playHint","previewHint","helperMarker"]);
  const PREVIEW_ATTRS = ["data-preview-video","data-preview-src",
    "data-preview-url","data-video-preview","data-trailer","data-hover-video"];
  const monitored = "video,button,[role='button'],a,iframe,source," +
    PREVIEW_ATTRS.map(a=>`[${a}]`).join(",");
  // Only test attribute existence. Never read the preview media URL.
  function isPreviewCandidate(node) {
    let current = node;
    for (let depth=0; current && depth<4; depth++,current=current.parentElement) {
      try {
        if (PREVIEW_ATTRS.some(name=>current.hasAttribute?.(name))) return true;
      } catch (_) {}
    }
    return false;
  }
  const allowedDisplay = new Set(["NONE","BLOCK","INLINE","INLINE_BLOCK","FLEX","GRID","CONTENTS","OTHER"]);
  const allowedVisibility = new Set(["VISIBLE","HIDDEN","COLLAPSE","OTHER"]);
  const allowedPointer = new Set(["AUTO","NONE","OTHER"]);
  const safeNumber = value => Number.isFinite(value) ? Math.max(0, Math.min(32768, Math.round(value))) : 0;
  // A failed native connect is retried passively with bounded frequency.
  // Successful READY resets the backoff, even after many separate disconnects.
  const RECONNECT_BASE_MS = 250, RECONNECT_MAX_MS = 30000;
  let retryPending = false, connectFailures = 0;
  let port, active = null, observer = null, listeners = [], serial = 0, picked = false, lost = 0;
  let stoppedId = null;
  let lastActiveId = null;
  let elementIds = new WeakMap(), nextElementId = 0;
  let shadowRoots = [], seenEvents = new WeakSet();
  function elementId(element) {
    if (!element || typeof element !== "object") return 0;
    if (!elementIds.has(element)) elementIds.set(element, ++nextElementId);
    return elementIds.get(element);
  }
  const documentId = Array.from(crypto.getRandomValues(new Uint8Array(16)),
    x => x.toString(16).padStart(2,"0")).join("");
  function category(value, allowed) {
    const key = String(value || "").toUpperCase().replace(/-/g,"_");
    return allowed.has(key) ? key : "OTHER";
  }
  function targetName(element) {
    if (!element || typeof element.tagName !== "string") return "UNKNOWN";
    const tag = element.tagName.toUpperCase();
    return /^(VIDEO|SOURCE|BUTTON|A|IFRAME|DIV|SPAN|IMG|PICTURE|SECTION|ARTICLE)$/.test(tag)
      ? tag : "OTHER";
  }
  function safeMetrics(raw) {
    const clean = {};
    if (!raw || typeof raw !== "object") return clean;
    // Object insertion order is NOT a semantic priority: CSS snapshots may
    // contain more fields than the transport budget. Preserve HTML5 evidence.
    const video = Object.prototype.hasOwnProperty.call(raw, "readyState");
    const priority = video
      ? ["frame","node","shadow","poster","paused","autoplay","controls",
         "readyState","networkState","errorCode","duration","currentTime",
         "width","helperMarker","display","visibility"]
      : ["frame","node","parent","count","dropped","playHint","hasId",
         "hasClass","hasAria","previewHint","shadow","x","y","width","height",
         "display","visibility"];
    for (const name of priority) {
      if (Object.keys(clean).length >= 16) break;
      if (!metricsKeys.has(name) || !Object.prototype.hasOwnProperty.call(raw,name)) continue;
      const value = raw[name];
      if (typeof value === "boolean") clean[name] = value;
      else if (typeof value === "number" && Number.isFinite(value))
        clean[name] = Math.max(0, Math.min(32768, Math.round(value)));
      else if (typeof value === "string" && /^[A-Z_]{2,24}$/.test(value))
        clean[name] = value;
    }
    return clean;
  }
  function send(event, target, detail, metrics) {
    if (!active || Date.now() - active.since > MAX_DURATION) { stop(); return; }
    if (serial >= MAX_EVENTS) { lost++; return; }
    if (serial === MAX_EVENTS - 1) {
      event = "GAP"; target = "OTHER"; detail = "EVENT_LIMIT"; metrics = {dropped:1};
    }
    serial++;
    try {
      port.postMessage({schemaVersion:2,event:"observation",sessionId:active.id,
        documentId,sequence:serial,kind:event,target,detail,
        metrics:safeMetrics(Object.assign({frame:window.top === window ? "TOP" : "SUBFRAME"},metrics))});
    } catch (_) { losePort(port); }
  }
  function snapshotElement(node, reason) {
    if (!node || !active) return;
    let data = {};
    try {
      const box = node.getBoundingClientRect();
      const css = getComputedStyle(node);
      data = {node:elementId(node),parent:elementId(node.parentElement),
        hasId:!!node.id,hasClass:!!node.className,
        hasAria:!!node.hasAttribute?.("aria-label"),
        playHint:/\b(play|preview|odtw[oó]rz|start)\b/i.test(node.getAttribute?.("aria-label") || ""),
        previewHint:isPreviewCandidate(node),
        x:safeNumber(box.x),y:safeNumber(box.y),width:safeNumber(box.width),
        height:safeNumber(box.height),z:safeNumber(Number(css.zIndex)),
        display:category(css.display,allowedDisplay),
        visibility:category(css.visibility,allowedVisibility),
        pointerEvents:category(css.pointerEvents,allowedPointer),
        shadow:node.getRootNode() !== document};
    } catch (_) {}
    if (node.tagName === "VIDEO") {
      try {
        Object.assign(data,{readyState:node.readyState,networkState:node.networkState,
          paused:node.paused,muted:node.muted,autoplay:node.autoplay,controls:node.controls,
          // Existing assist sets this marker on helper-created videos.
          // A page can spoof it: this is an OBSERVED hint, not owner proof.
          helperMarker:node.dataset?.niraPreviewCreated === "1",
          loop:node.loop,poster:!!node.poster,playsInline:node.playsInline,
          duration:safeNumber(node.duration),currentTime:safeNumber(node.currentTime),
          errorCode:node.error ? node.error.code : 0,
          preload:category(node.preload,new Set(["NONE","AUTO","METADATA"]))});
      } catch (_) {}
    }
    send(node.tagName === "VIDEO" ? "MEDIA" : "DOM",targetName(node),reason,data);
  }
  function observeOpenRoot(root) {
    if (!active || !root || shadowRoots.includes(root)) return;
    // Closed roots are intentionally inaccessible. Never patch attachShadow
    // or execute code in the page world to discover them.
    if (shadowRoots.length >= MAX_ROOTS - 1) {
      send("GAP","OTHER","SHADOW_LIMIT",{count:shadowRoots.length});
      return;
    }
    shadowRoots.push(root);
    try {
      for (const name of PAGE_EVENTS) listen(root,name,handler,true);
      observer?.observe(root,MUTATION_CONFIG);
    } catch (_) { send("GAP","OTHER","DOM_UNAVAILABLE",{}); }
  }
  function snapshot(reason) {
    if (!active) return;
    const perFrameMax = window.top === window ? MAX_NODES : MAX_SUBFRAME_NODES;
    let count = 0, roots = [document], scanned = 0, limited = false;
    for (let index = 0; index < roots.length; index++) {
      const root = roots[index];
      try {
        if (count < perFrameMax) {
          for (const node of root.querySelectorAll(monitored)) {
            if (count >= perFrameMax) { limited = true; break; }
            snapshotElement(node,reason); count++;
          }
        }
        const walker = document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT);
        let element;
        while ((element = walker.nextNode()) && scanned < 300) {
          scanned++;
          if (!element.shadowRoot || roots.includes(element.shadowRoot)) continue;
          if (roots.length >= MAX_ROOTS) { limited = true; break; }
          roots.push(element.shadowRoot);
          observeOpenRoot(element.shadowRoot);
        }
        if (scanned >= 300) limited = true;
      } catch (_) { send("GAP","OTHER","DOM_UNAVAILABLE",{}); }
    }
    send("DOM","OTHER","SNAPSHOT_FINISHED",{count,shadow:roots.length>1});
    if (limited || count >= perFrameMax)
      send("GAP","OTHER","SCAN_LIMIT",{count});
  }
  // Sample a new element (including descendants and open shadow roots)
  // without querying every page node or reading any DOM text/URL.
  function sampleInserted(node) {
    if (!active || !node || typeof node.tagName !== "string") return;
    let sampled = 0, visited = 0;
    const queue = [node], queued = new Set([node]);
    function queueShadow(root) {
      if (!root || queued.has(root)) return;
      queued.add(root);
      observeOpenRoot(root);
      if (queued.size <= MAX_ROOTS) queue.push(root);
    }
    for (let i = 0; i < queue.length && visited < MAX_INSERTED_SCAN; i++) {
      const root = queue[i];
      queueShadow(root.shadowRoot);
      try {
        const walker = document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT);
        let element;
        const elements = root.tagName ? [root] : [];
        while ((element = walker.nextNode()) && visited + elements.length < MAX_INSERTED_SCAN) {
          elements.push(element);
        }
        for (const child of elements) {
          visited++;
          queueShadow(child.shadowRoot);
          if (sampled < MAX_INSERTED_SAMPLES &&
              (/^(VIDEO|SOURCE|BUTTON|A|IFRAME)$/.test(targetName(child)) ||
               isPreviewCandidate(child))) {
            snapshotElement(child,"NODE_INSERTED"); sampled++;
          }
        }
      } catch (_) { send("GAP","OTHER","DOM_UNAVAILABLE",{}); }
    }
    if (visited >= MAX_INSERTED_SCAN) send("GAP","OTHER","SCAN_LIMIT",{count:visited});
  }
  function handler(event) {
    if (!active || serial >= MAX_EVENTS || seenEvents.has(event)) return;
    // Composed events can hit both shadow and document listeners. Keep one
    // record, but capture non-composed media events inside open shadow roots.
    seenEvents.add(event);
    const type = event.type;
    if (picked && type === "pointerdown" && event.pointerType === "touch") {
      picked = false;
      // Only the explicitly requested picker consumes one tap.
      event.preventDefault();
      event.stopImmediatePropagation();
      let node = event.target;
      try { node = node?.closest?.(monitored) || node; } catch (_) {}
      snapshotElement(node,"PICKED_ELEMENT");
      send("PICK",targetName(node),"USER_SELECTED",
        {node:elementId(node),shadow:node?.getRootNode?.() !== document});
      return;
    }
    if (type === "unhandledrejection") {
      send("ERROR","OTHER","PROMISE_REJECTION",{}); return;
    }
    if (type === "error" && event.target === window) {
      send("ERROR","OTHER","JS_ERROR",{}); return;
    }
    if (type === "visibilitychange") {
      send("EVENT","OTHER","DOCUMENT_VISIBILITY",{});
      return;
    }
    let node = event.target;
    try { node = node?.closest?.(monitored) || node; } catch (_) {}
    if (!node || !/^(VIDEO|BUTTON|A|IFRAME|SOURCE)$/.test(targetName(node)) &&
        node.getAttribute?.("role") !== "button" &&
        !isPreviewCandidate(node)) return;
    if (type === "pointerover" && node === handler.hovered) return;
    if (type === "pointerover") handler.hovered = node;
    send(["play","pause","playing","waiting","stalled","loadedmetadata",
      "error"].includes(type) ? "MEDIA" : "EVENT",targetName(node),
      "ON_" + type.toUpperCase(),
      {shadow:node.getRootNode?.() !== document,
       previewHint:isPreviewCandidate(node)});
    if (type === "play" || type === "error") snapshotElement(node,"VIDEO_STATE");
  }
  function mutation(batch) {
    if (!active || serial >= MAX_EVENTS) return;
    let attrs = 0, nodes = 0, sampled = 0, inserted = 0;
    // The event identifies WHICH node changed, not the new attribute value.
    // Preserve causality of an animated thumbnail without leaking src/poster.
    for (const record of batch.slice(0,40)) {
      if (record.type === "attributes") {
        attrs++;
        const name = record.attributeName;
        if (sampled < 3 && typeof name === "string" &&
            /^(?:style|class|hidden|poster|src|controls|autoplay|role)$/.test(name)) {
          send("MUTATION",targetName(record.target),"ATTRIBUTE_" + name.toUpperCase(),
            {node:elementId(record.target)});
          snapshotElement(record.target,"ATTRIBUTE_STATE");
          sampled++;
        }
      }
      if (record.type === "childList") {
        nodes += Math.min(10,(record.addedNodes || []).length);
        for (const node of Array.from(record.addedNodes || []).slice(0,3)) {
          if (inserted++ >= MAX_INSERTED_SAMPLES) break;
          sampleInserted(node);
        }
      }
    }
    if (attrs > sampled)
      send("MUTATION","OTHER","ATTRIBUTE_BATCH",{count:attrs - sampled});
    if (nodes) send("MUTATION","OTHER","NODES_ADDED",{count:nodes});
  }
  function listen(target,type,fn,opts) {
    target.addEventListener(type,fn,opts);
    listeners.push(() => target.removeEventListener(type,fn,opts));
  }
  function stop() {
    active = null; picked = false;
    if (observer) { observer.disconnect(); observer = null; }
    for (const remove of listeners.splice(0)) { try { remove(); } catch (_) {} }
    shadowRoots = [];
    seenEvents = new WeakSet();
  }
  function start(id) {
    if (!sessionPattern.test(id || "")) return;
    if (id === stoppedId) return;
    if (active?.id === id) {
      // Native may retry when its first ACK was lost. Re-emit the baseline
      // after each real ACK attempt, without duplicating page listeners.
      if (acknowledge(id)) snapshot("BASELINE");
      return;
    }
    stop();
    if (lastActiveId !== id) {
      serial = 0;
      elementIds = new WeakMap(); nextElementId = 0;
    }
    lastActiveId = id;
    active = {id,since:Date.now()}; lost = 0;
    if (!acknowledge(id)) return;
    handler.hovered = null;
    for (const name of PAGE_EVENTS) listen(document,name,handler,true);
    listen(window,"unhandledrejection",handler,false);
    listen(window,"error",handler,true);
    listen(document,"visibilitychange",handler,true);
    observer = new MutationObserver(mutation);
    observer.observe(document,MUTATION_CONFIG);
    snapshot("BASELINE");
  }
  // Only enum metadata and a monotonic command ID cross the Gecko bridge.
  function confirmControl(action, id, controlId) {
    if (!Number.isInteger(controlId) || controlId <= 0) return;
    try {
      port.postMessage({schemaVersion:2,event:"control_ack",sessionId:id,
        documentId,frame:window.top === window ? "TOP" : "SUBFRAME",
        action,controlId});
    } catch (_) { losePort(port); }
  }
  function acknowledge(id) {
    try {
      port.postMessage({schemaVersion:2,event:"start_ack",sessionId:id,
        documentId,frame:window.top === window ? "TOP" : "SUBFRAME"});
      return true;
    } catch (_) { losePort(port); return false; }
  }
  function scheduleReconnect() {
    if (retryPending || port) return;
    retryPending = true;
    // Limit the frequency, not the number, of retries. A native handler can
    // become available long after the original document was created.
    const delay = Math.min(RECONNECT_MAX_MS,
      RECONNECT_BASE_MS * (2 ** Math.min(connectFailures, 7)));
    connectFailures = Math.min(connectFailures + 1, 8);
    setTimeout(() => {
      retryPending = false;
      if (!port) connect();
    }, delay);
  }
  function losePort(current) {
    if (!current || port !== current) return;
    port = null;
    stop();
    try { current.disconnect?.(); } catch (_) {}
    scheduleReconnect();
  }
  function connect() {
    if (port) return;
    let candidate;
    try { candidate = browser.runtime.connectNative(APP); } catch (_) { candidate = null; }
    if (!candidate) { scheduleReconnect(); return; }
    port = candidate;
    candidate.onMessage.addListener(command => {
      // A late callback from a retired Port cannot resurrect an old session.
      if (port !== candidate) return;
      if (!command || command.schemaVersion !== 2 || typeof command.sessionId !== "string") return;
      if (command.action === "start" && Object.keys(command).length === 3) {
        start(command.sessionId);
      } else if ((active?.id === command.sessionId ||
                   !active && command.action === "stop" &&
                   sessionPattern.test(command.sessionId)) &&
                 (Object.keys(command).length === 3 ||
                  Object.keys(command).length === 4 &&
                  Number.isInteger(command.controlId) && command.controlId > 0)) {
        const action = command.action, id = command.sessionId;
        if (action === "snapshot") {
          snapshot("USER_MARK");
          confirmControl(action,id,command.controlId);
        } else if (action === "pick") {
          picked = true;
          confirmControl(action,id,command.controlId);
        } else if (action === "cancel_pick") {
          picked = false;
          confirmControl(action,id,command.controlId);
        } else if (action === "stop") {
          stoppedId = id;
          stop();
          confirmControl(action,id,command.controlId);
        }
      }
    });
    candidate.onDisconnect.addListener(() => {
      // Idempotent: ignore late/duplicate disconnects of an old port.
      if (port !== candidate) return;
      port = null;
      stop();
      scheduleReconnect();
    });
    // READY does not mean START succeeded. This packet is emitted only
    // after our command listener is registered, without any raw page data.
    try {
      candidate.postMessage({schemaVersion:2,event:"ready",documentId,
        frame:window.top === window ? "TOP" : "SUBFRAME",scriptVersion:SCRIPT_VERSION});
      connectFailures = 0;
    } catch (_) {
      losePort(candidate);
    }
  }
  connect();
})();
