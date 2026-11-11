/*
 * Zavro preview diagnostics stage 2. DORMANT until a native START.
 * No raw DOM, text, source URLs, credentials, media playback or requests.
 * Runs in the extension isolated world; observes only opt-in interactions.
 */
(function () {
  "use strict";
  const APP = "zavro.preview.forensics";
  const MAX_EVENTS = 160, MAX_NODES = 40, MAX_ROOTS = 6, MAX_DURATION = 15 * 60 * 1000;
  const sessionPattern = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  const metricsKeys = new Set(["frame","shadow","readyState","networkState","paused",
    "muted","autoplay","controls","loop","duration","currentTime","errorCode","width",
    "height","x","y","z","display","visibility","pointerEvents","poster","preload",
    "playsInline","dropped","count","node","parent","hasId","hasClass","hasAria","playHint","helperMarker"]);
  const monitored = "video,button,[role='button'],a,iframe,source";
  const allowedDisplay = new Set(["NONE","BLOCK","INLINE","INLINE_BLOCK","FLEX","GRID","CONTENTS","OTHER"]);
  const allowedVisibility = new Set(["VISIBLE","HIDDEN","COLLAPSE","OTHER"]);
  const allowedPointer = new Set(["AUTO","NONE","OTHER"]);
  const safeNumber = value => Number.isFinite(value) ? Math.max(0, Math.min(32768, Math.round(value))) : 0;
  let port, active = null, observer = null, listeners = [], serial = 0, picked = false, lost = 0;
  let stoppedId = null;
  let lastActiveId = null;
  let elementIds = new WeakMap(), nextElementId = 0;
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
      ? ["frame","node","parent","poster","paused","autoplay","controls",
         "readyState","networkState","errorCode","duration","currentTime",
         "width","helperMarker","display","visibility"]
      : ["frame","node","parent","count","dropped","playHint","hasId",
         "hasClass","hasAria","shadow","x","y","width","height",
         "display","visibility"];
    for (const name of priority) {
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
    } catch (_) { stop(); }
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
  function snapshot(reason) {
    if (!active) return;
    let count = 0, roots = [document], scanned = 0;
    for (let index = 0; index < roots.length && count < MAX_NODES; index++) {
      const root = roots[index];
      try {
        for (const node of root.querySelectorAll(monitored)) {
          if (count++ >= MAX_NODES) break;
          snapshotElement(node,reason);
        }
        const walker = document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT);
        let element;
        while ((element = walker.nextNode()) && scanned++ < 300 && roots.length < MAX_ROOTS) {
          if (element.shadowRoot) roots.push(element.shadowRoot);
        }
      } catch (_) { send("GAP","OTHER","DOM_UNAVAILABLE",{}); }
    }
    send("DOM","OTHER","SNAPSHOT_FINISHED",{count,shadow:roots.length>1});
    if (roots.length >= MAX_ROOTS || scanned >= 300 || count >= MAX_NODES)
      send("GAP","OTHER","SCAN_LIMIT",{count});
  }
  function handler(event) {
    if (!active || serial >= MAX_EVENTS) return;
    const type = event.type;
    if (picked && type === "pointerdown" && event.pointerType === "touch") {
      picked = false;
      // Only the explicitly requested picker consumes one tap.
      event.preventDefault();
      event.stopImmediatePropagation();
      let node = event.target;
      try { node = node?.closest?.(monitored) || node; } catch (_) {}
      snapshotElement(node,"PICKED_ELEMENT");
      send("PICK",targetName(node),"USER_SELECTED",{node:elementId(node)});
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
        node.getAttribute?.("role") !== "button") return;
    if (type === "pointerover" && node === handler.hovered) return;
    if (type === "pointerover") handler.hovered = node;
    send(type === "play" || type === "pause" || type === "loadedmetadata" ||
        type === "error" ? "MEDIA" : "EVENT",targetName(node),
      "ON_" + type.toUpperCase(),{});
    if (type === "play" || type === "error") snapshotElement(node,"VIDEO_STATE");
  }
  function mutation(batch) {
    if (!active || serial >= MAX_EVENTS) return;
    let attrs = 0, nodes = 0, sampled = 0;
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
      if (record.type === "childList")
        nodes += Math.min(10,(record.addedNodes || []).length);
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
    for (const name of ["pointerdown","pointerover","click","play","pause","loadedmetadata","error"]) {
      listen(document,name,handler,true);
    }
    listen(window,"unhandledrejection",handler,false);
    listen(window,"error",handler,true);
    listen(document,"visibilitychange",handler,true);
    observer = new MutationObserver(mutation);
    observer.observe(document,{subtree:true,childList:true,attributes:true,
      attributeFilter:["style","class","hidden","poster","src","controls","autoplay","role"]});
    snapshot("BASELINE");
  }
  function acknowledge(id) {
    try {
      port.postMessage({schemaVersion:2,event:"start_ack",sessionId:id,
        documentId,frame:window.top === window ? "TOP" : "SUBFRAME"});
      return true;
    } catch (_) { stop(); return false; }
  }
  function connect(attempt = 0) {
    try { port = browser.runtime.connectNative(APP); } catch (_) { port = null; }
    if (!port) {
      if (attempt < 3) setTimeout(() => connect(attempt + 1), 250 * (attempt + 1));
      return;
    }
    port.onMessage.addListener(command => {
      if (!command || command.schemaVersion !== 2 || typeof command.sessionId !== "string") return;
      if (command.action === "start" && Object.keys(command).length === 3) start(command.sessionId);
      else if (active?.id === command.sessionId && Object.keys(command).length === 3) {
        if (command.action === "snapshot") snapshot("USER_MARK");
        else if (command.action === "pick") picked = true;
        else if (command.action === "cancel_pick") picked = false;
        else if (command.action === "stop") { stoppedId = active.id; stop(); }
      }
    });
    port.onDisconnect.addListener(() => {
      // A disconnected idle channel must be restored for the next START,
      // including after STOP. Reconnecting is passive: no DOM listeners or
      // observations are installed until a distinct opt-in session starts.
      stop();
      if (attempt < 3)
        setTimeout(() => connect(attempt + 1), 250 * (attempt + 1));
    });
    // READY does not mean START succeeded. This packet is emitted only
    // after our command listener is registered, without any raw page data.
    try {
      port.postMessage({schemaVersion:2,event:"ready",documentId,
        frame:window.top === window ? "TOP" : "SUBFRAME"});
    } catch (_) {
      stop();
      if (attempt < 3) setTimeout(() => connect(attempt + 1), 250 * (attempt + 1));
    }
  }
  connect();
})();
