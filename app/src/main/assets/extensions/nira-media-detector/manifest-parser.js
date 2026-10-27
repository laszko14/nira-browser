"use strict";

// Small, dependency-free metadata parser. It never downloads segments and
// intentionally supports only the bounded subset surfaced in the UI.
globalThis.NiraManifestParser = (() => {
  const MAX_CHARS = 512 * 1024;
  const number = value => /^\d+$/.test(value || "") ? Number(value) : null;
  const url = (value, base) => {
    try {
      const parsed = new URL(value, base);
      return ["http:", "https:"].includes(parsed.protocol) && parsed.hostname &&
        !parsed.username && !parsed.password ? parsed.href : null;
    } catch (_) { return null; }
  };
  function attributes(value) {
    const result = {};
    const pattern = /([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))(?:,|$)/gi;
    let match;
    while ((match = pattern.exec(value || ""))) result[match[1].toUpperCase()] = match[2] ?? match[3];
    return result;
  }
  function duration(value) {
    const match = /^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$/i.exec(value || "");
    if (!match) return null;
    const seconds = Number(match[1] || 0) * 86400 + Number(match[2] || 0) * 3600 +
      Number(match[3] || 0) * 60 + Number(match[4] || 0);
    return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
  }
  function bounded(text, limit) {
    if (typeof text !== "string" || text.length > MAX_CHARS) throw new Error("manifest_too_large");
    return Math.max(1, Math.min(Number.isSafeInteger(limit) ? limit : 16, 32));
  }
  function parseHls(text, base, limit) {
    limit = bounded(text, limit);
    const lines = text.replace(/\r/g, "").split("\n").map(line => line.trim());
    if (lines[0] !== "#EXTM3U") throw new Error("invalid_hls");
    const variants = [], audioTracks = [], subtitleTracks = [];
    let seconds = 0, firstSegmentUrl = null, drm = false, endList = false, segments = 0;
    for (let index = 0; index < lines.length; index++) {
      const line = lines[index];
      if (/^#EXT-X-(?:SESSION-)?KEY:/.test(line)) {
        const key = attributes(line.slice(line.indexOf(":") + 1));
        if (key.METHOD && key.METHOD !== "NONE" &&
            (key.METHOD !== "AES-128" || key.KEYFORMAT && key.KEYFORMAT !== "identity")) drm = true;
      }
      if (line === "#EXT-X-ENDLIST") endList = true;
      if (line.startsWith("#EXTINF:")) {
        const value = Number(line.slice(8).split(",")[0]);
        if (Number.isFinite(value) && value > 0) { seconds += value; segments++; }
        let next = index + 1;
        while (next < lines.length && (!lines[next] || lines[next].startsWith("#"))) next++;
        if (!firstSegmentUrl && next < lines.length) firstSegmentUrl = url(lines[next], base);
      }
      if (line.startsWith("#EXT-X-MEDIA:")) {
        const data = attributes(line.slice(13));
        const target = data.TYPE === "AUDIO" ? audioTracks :
          data.TYPE === "SUBTITLES" ? subtitleTracks : null;
        const resolved = data.URI && url(data.URI, base);
        if (target && resolved && target.length < limit) target.push({
          url: resolved, language: data.LANGUAGE || null, name: data.NAME || null,
          group: data["GROUP-ID"] || null,
        });
      }
      if (line.startsWith("#EXT-X-STREAM-INF:") && variants.length < limit) {
        const data = attributes(line.slice(18));
        let next = index + 1;
        while (next < lines.length && (!lines[next] || lines[next].startsWith("#"))) next++;
        const resolved = next < lines.length ? url(lines[next], base) : null;
        if (resolved) {
          const resolution = /^(\d+)x(\d+)$/i.exec(data.RESOLUTION || "");
          variants.push({ url: resolved, width: resolution ? number(resolution[1]) : null,
            height: resolution ? number(resolution[2]) : null,
            bitrate: number(data["AVERAGE-BANDWIDTH"] || data.BANDWIDTH),
            codecs: data.CODECS || null, audioGroup: data.AUDIO || null });
        }
      }
    }
    if (!variants.length && !firstSegmentUrl && !lines.some(line => line.startsWith("#EXT-X-STREAM-INF:")))
      throw new Error("invalid_hls");
    const media = variants.length === 0;
    // A non-final playlist has only a moving window; never call that a movie duration.
    return { protocol: "HLS", drm, kind: media ? "media" : "master", variants,
      audioTracks, subtitleTracks, live: media && !endList,
      durationSeconds: media && endList && segments > 0 ? Math.ceil(seconds) : null,
      firstSegmentUrl };
  }
  function xmlAttr(tag, name) {
    const match = new RegExp("\\b" + name + "\\s*=\\s*['\"]([^'\"]*)['\"]", "i").exec(tag);
    return match ? match[1] : null;
  }
  function textTag(xml, name) {
    const match = new RegExp("<" + name + "(?:\\s[^>]*)?>([^<]+)</" + name + ">", "i").exec(xml);
    return match ? match[1].trim().replace(/&(amp|lt|gt|quot|apos|#\d+|#x[0-9a-f]+);/gi,
      (_, entity) => {
        const named = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };
        const lower = entity.toLowerCase();
        if (named[lower]) return named[lower];
        const value = lower.startsWith("#x") ? parseInt(lower.slice(2), 16) : parseInt(lower.slice(1), 10);
        return Number.isInteger(value) && value > 0 && value <= 0x10ffff ? String.fromCodePoint(value) : "";
      }) : null;
  }
  function parseDash(text, base, limit) {
    limit = bounded(text, limit);
    const mpd = /<MPD\b[^>]*>/i.exec(text)?.[0];
    if (!mpd || /<!DOCTYPE|<!ENTITY/i.test(text) ||
        !(/<\/MPD\s*>\s*$/i.test(text) || /<MPD\b[^>]*\/>\s*$/i.test(text))) throw new Error("invalid_dash");
    const rootBase = url(textTag(text, "BaseURL") || base, base) || base;
    const variants = [], audioTracks = [], subtitleTracks = [];
    const sets = /<AdaptationSet\b[^>]*>[\s\S]*?<\/AdaptationSet>/gi;
    let set;
    while ((set = sets.exec(text))) {
      const setTag = /<AdaptationSet\b[^>]*>/i.exec(set[0])?.[0] || "";
      const mime = xmlAttr(setTag, "mimeType") || "";
      const content = xmlAttr(setTag, "contentType") || mime.split("/")[0];
      const language = xmlAttr(setTag, "lang");
      const codecs = xmlAttr(setTag, "codecs");
      const setBaseValue = textTag(set[0].replace(/<Representation\b[\s\S]*?<\/Representation>/gi, ""), "BaseURL");
      const setBase = setBaseValue ? url(setBaseValue, rootBase) || rootBase : rootBase;
      const reps = /<Representation\b[^>]*(?:\/>|>[\s\S]*?<\/Representation>)/gi;
      let rep;
      while ((rep = reps.exec(set[0]))) {
        const tag = /<Representation\b[^>]*>/i.exec(rep[0])?.[0] || rep[0];
        const repBase = textTag(rep[0], "BaseURL");
        const resolved = repBase ? url(repBase, setBase) : null;
        const item = { url: resolved, width: number(xmlAttr(tag, "width")),
          height: number(xmlAttr(tag, "height")), bitrate: number(xmlAttr(tag, "bandwidth")),
          codecs: xmlAttr(tag, "codecs") || codecs || null, language: language || null };
        const target = content === "video" ? variants : content === "audio" ? audioTracks :
          content === "text" || /subtitle/i.test(mime) ? subtitleTracks : null;
        if (target && resolved && target.length < limit) target.push(item);
      }
    }
    const dynamic = /\btype\s*=\s*['"]dynamic['"]/i.test(mpd);
    const periodDurations = [...text.matchAll(/<Period\b[^>]*>/gi)]
      .map(match => duration(xmlAttr(match[0], "duration")));
    const totalPeriods = periodDurations.length > 0 && periodDurations.every(value => value !== null)
      ? periodDurations.reduce((a, b) => a + b, 0) : null;
    const mediaDuration = duration(xmlAttr(mpd, "mediaPresentationDuration")) || totalPeriods;
    // Only a single video representation plus at most one audio track can be
    // combined without guessing which resolution the player selected.
    const chosenBitrate = variants.length === 1 && audioTracks.length <= 1 &&
      [variants[0], ...audioTracks].every(item => Number.isSafeInteger(item.bitrate) && item.bitrate > 0)
      ? variants[0].bitrate + (audioTracks[0]?.bitrate || 0) : null;
    return { protocol: "DASH", drm: /<ContentProtection\b/i.test(text), dynamic,
      live: dynamic, durationSeconds: dynamic ? null : mediaDuration,
      bitrate: chosenBitrate, variants, audioTracks, subtitleTracks };
  }
  return { parseHls, parseDash };
})();
