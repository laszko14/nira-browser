"use strict";
// G2 fixture only. Log labels, never full URLs, cookies, credentials or context IDs.
(() => {
  const page = document.body.dataset.page;
  const logEl = document.getElementById("events");
  let seq = 0;
  const key = "zv_g2";
  const markers = new Set(["P", "Q"]);
  const events = [];
  const labelMarker = value => markers.has(value) ? value : "EMPTY_OR_OTHER";
  const emit = (event, data = {}) => {
    events.push({ seq: ++seq, ms: Math.round(performance.now()), event, ...data });
    if (events.length > 160) events.shift();
    logEl.textContent = events.map(entry => JSON.stringify(entry)).join("\n");
  };
  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open("zv_g2_fixture", 1);
      request.onupgradeneeded = () => request.result.createObjectStore("markers");
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(new Error("idb-open"));
    });
  }
  async function idbPut(value) {
    const db = await openDb();
    try {
      await new Promise((resolve, reject) => {
        const tx = db.transaction("markers", "readwrite");
        tx.objectStore("markers").put(value, "current");
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(new Error("idb-write"));
      });
    } finally { db.close(); }
  }
  async function idbRead() {
    const db = await openDb();
    try {
      return await new Promise((resolve, reject) => {
        const req = db.transaction("markers", "readonly").objectStore("markers").get("current");
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(new Error("idb-read"));
      });
    } finally { db.close(); }
  }
  function cookieMarker() {
    const part = document.cookie.split(";").map(x => x.trim()).find(x => x.startsWith(key + "="));
    return part ? part.substring(key.length + 1) : null;
  }
  async function readStorage() {
    const observed = {
      cookie: labelMarker(cookieMarker()),
      localStorage: labelMarker(localStorage.getItem(key)),
      indexedDB: labelMarker(await idbRead())
    };
    document.getElementById("storage-result").textContent = JSON.stringify(observed);
    emit("storage-read", observed);
  }
  async function writeStorage() {
    const value = document.getElementById("marker").value;
    if (!markers.has(value)) throw new Error("bad-fixture-marker");
    document.cookie = key + "=" + value + "; Path=/; Secure; SameSite=Strict; Max-Age=86400";
    localStorage.setItem(key, value);
    await idbPut(value);
    emit("storage-write", { marker: value });
    await readStorage();
  }
  function onClick(id, callback) {
    document.getElementById(id).addEventListener("click", () => {
      try { Promise.resolve(callback()).catch(() => emit("fixture-action-error", { action: id })); }
      catch (_) { emit("fixture-action-error", { action: id }); }
    });
  }
  if (page === "storage" || page === "popup") {
    onClick("read", readStorage);
    if (page === "storage") {
      onClick("write", writeStorage);
      onClick("open-window", () => {
        const child = window.open("/popup.html", "_blank");
        emit("popup-request", { mode: "opener", opened: Boolean(child) });
      });
      onClick("open-noopener", () => {
        window.open("/popup.html", "_blank", "noopener");
        emit("popup-request", { mode: "noopener", opened: "not-observable" });
      });
    } else {
      const opener = window.opener !== null;
      document.getElementById("opener-result").textContent = "window.opener present: " + opener;
      emit("popup-loaded", { openerPresent: opener });
    }
  }
  if (page !== "video") return;

  const video = document.getElementById("probe");
  let objectUrl = null;
  const sources = { A: "/media/a.mp4", B: "/media/b.mp4", BAD: "/media/invalid-b.mp4" };
  function safeSource(raw) {
    if (!raw) return "EMPTY";
    if (raw.startsWith("blob:")) return "BLOB";
    try {
      const pathname = new URL(raw, location.href).pathname;
      if (pathname === sources.A) return "A";
      if (pathname === sources.B) return "B";
      if (pathname === sources.BAD) return "BAD";
    } catch (_) { /* Never emit an unrecognized URL. */ }
    return "OTHER";
  }
  function snapshot(event) {
    emit(event, {
      src: safeSource(video.src),
      currentSrc: safeSource(video.currentSrc),
      readyState: video.readyState,
      duration: Number.isFinite(video.duration) ? video.duration : "UNKNOWN",
      width: video.videoWidth, height: video.videoHeight,
      errorCode: video.error ? video.error.code : 0
    });
  }
  ["emptied", "loadstart", "loadedmetadata", "durationchange", "resize",
   "error", "stalled", "abort", "suspend", "canplay"].forEach(event => {
    video.addEventListener(event, () => snapshot(event));
  });
  const observer = new MutationObserver(() => snapshot("MutationObserver"));
  observer.observe(video, { attributes: true, attributeFilter: ["src"], childList: true });
  function reset() {
    video.pause();
    video.removeAttribute("src");
    Array.from(video.querySelectorAll("source")).forEach(node => node.remove());
    video.load();
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
      objectUrl = null;
    }
  }
  function load(which) {
    reset();
    video.src = sources[which];
    snapshot("source-assigned-" + which);
    video.load();
  }
  function waitForA() {
    if (video.readyState >= 1 && safeSource(video.currentSrc) === "A") return Promise.resolve();
    load("A");
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => { cleanup(); reject(new Error("metadata-timeout")); }, 8000);
      const ok = () => { cleanup(); resolve(); };
      const fail = () => { cleanup(); reject(new Error("metadata-error")); };
      const cleanup = () => {
        clearTimeout(timeout);
        video.removeEventListener("loadedmetadata", ok);
        video.removeEventListener("error", fail);
      };
      video.addEventListener("loadedmetadata", ok);
      video.addEventListener("error", fail);
    });
  }
  onClick("load-a", () => load("A"));
  onClick("load-b", () => load("B"));
  onClick("switch-b", async () => {
    await waitForA();
    snapshot("before-B-assignment");
    video.src = sources.B;
    snapshot("after-B-assignment");
    video.load();
  });
  onClick("invalid-b", async () => {
    await waitForA();
    snapshot("before-BAD-assignment");
    video.src = sources.BAD;
    snapshot("after-BAD-assignment");
    video.load();
  });
  onClick("source-list", () => {
    reset();
    ["A", "B"].forEach(id => {
      const source = document.createElement("source");
      source.src = sources[id];
      source.type = "video/mp4";
      video.appendChild(source);
    });
    snapshot("source-list-attached");
    video.load();
  });
  onClick("blob-b", async () => {
    const response = await fetch(sources.B, { cache: "no-store", credentials: "omit" });
    if (!response.ok) throw new Error("media-fetch-error");
    const blob = await response.blob();
    reset();
    objectUrl = URL.createObjectURL(blob);
    video.src = objectUrl;
    snapshot("blob-B-assigned");
    video.load();
  });
  snapshot("initial");
})();
