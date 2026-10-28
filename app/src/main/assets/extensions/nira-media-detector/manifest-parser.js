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
    const variants = [], audioTracks = [], subtitleTracks = [], trickplayTracks = [];
    let seconds = 0, firstSegmentUrl = null, drm = false, endList = false, segments = 0;
    let rangedSegments = 0, rangeBytes = 0, hasInitialization = false;
    for (let index = 0; index < lines.length; index++) {
      const line = lines[index];
      if (/^#EXT-X-(?:SESSION-)?KEY:/.test(line)) {
        const key = attributes(line.slice(line.indexOf(":") + 1));
        if (key.METHOD && key.METHOD !== "NONE" &&
            (key.METHOD !== "AES-128" || key.KEYFORMAT && key.KEYFORMAT !== "identity")) drm = true;
      }
      if (line === "#EXT-X-ENDLIST") endList = true;
      if (line.startsWith("#EXT-X-MAP:")) hasInitialization = true;
      if (line.startsWith("#EXT-X-BYTERANGE:")) {
        const match = /^#EXT-X-BYTERANGE:(\d+)(?:@\d+)?$/.exec(line);
        if (match && Number.isSafeInteger(Number(match[1]))) {
          rangedSegments++;
          rangeBytes += Number(match[1]);
        }
      }
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
      if (line.startsWith("#EXT-X-I-FRAME-STREAM-INF:") && trickplayTracks.length < limit) {
        const data = attributes(line.slice("#EXT-X-I-FRAME-STREAM-INF:".length));
        const source = data.URI && url(data.URI, base);
        const res = /^(\d+)x(\d+)$/i.exec(data.RESOLUTION || "");
        if (source) trickplayTracks.push({url:source, width:res?number(res[1]):null,
          height:res?number(res[2]):null, bitrate:number(data.BANDWIDTH),
          codecs:data.CODECS || null, role:"trickplay"});
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
            bitrateKind: number(data["AVERAGE-BANDWIDTH"]) ? "average" : "peak",
            averageBitrate: number(data["AVERAGE-BANDWIDTH"]),
            peakBitrate: number(data.BANDWIDTH),
            frameRate: /^(?:\d{1,3})(?:\.\d{1,3})?$/.test(data["FRAME-RATE"] || "") ? Number(data["FRAME-RATE"]) : null,
            codecs: data.CODECS || null, audioGroup: data.AUDIO || null,
            subtitleGroup: data.SUBTITLES || null });
        }
      }
    }
    if (!variants.length && !firstSegmentUrl && !lines.some(line => line.startsWith("#EXT-X-STREAM-INF:")))
      throw new Error("invalid_hls");
    const media = variants.length === 0;
    // A non-final playlist has only a moving window; never call that a movie duration.
    return { protocol: "HLS", drm, kind: media ? "media" : "master", variants,
      audioTracks, subtitleTracks, trickplayTracks, live: media && !endList,
      durationSeconds: media && endList && segments > 0 ? Math.ceil(seconds) : null,
      // Exact only for a complete single-rendition VOD in which EVERY segment
      // has an explicit byte range and no unknown initialization segment.
      exactSizeBytes: media && endList && segments > 0 && rangedSegments === segments &&
        !hasInitialization && Number.isSafeInteger(rangeBytes) && rangeBytes > 0 &&
        rangeBytes <= 1000000000000 ? rangeBytes : null,
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
  // Bounded XML tree: works in MV2 backgrounds without depending on DOMParser.
  // A manifest is data, never executable XML (external DTD/entities rejected).
  function mpdTree(xml) {
    if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error("invalid_dash");
    const tokens = xml.replace(/<!--[\s\S]*?-->/g, "").replace(/<\?[\s\S]*?\?>/g, "")
      .match(/<[^>]*>|[^<]+/g) || [];
    if (tokens.length > 15000) throw new Error("invalid_dash");
    const stack = [];
    let root = null, count = 0;
    for (const token of tokens) {
      if (token.startsWith("</")) {
        const name = /^<\/([\w:.-]+)\s*>$/.exec(token)?.[1]?.split(":").pop();
        if (!stack.length || stack.pop().name !== name) throw new Error("invalid_dash");
      } else if (token.startsWith("<")) {
        const match = /^<([\w:.-]+)(?:\s[^<>]*)?\/?>$/.exec(token);
        if (!match || ++count > 5000 || stack.length > 16) throw new Error("invalid_dash");
        const node = { name: match[1].split(":").pop(), tag: token, text: "", children: [] };
        if (stack.length) stack[stack.length-1].children.push(node);
        else if (root) throw new Error("invalid_dash");
        else root = node;
        if (!token.endsWith("/>")) stack.push(node);
      } else if (stack.length && stack[stack.length-1].text.length < 2048)
        stack[stack.length-1].text += token.slice(0, 2048);
      else if (!stack.length && token.trim()) throw new Error("invalid_dash");
    }
    if (stack.length || root?.name !== "MPD") throw new Error("invalid_dash");
    return root;
  }
  const nodes = (parent, name) => parent.children.filter(node => node.name === name);
  const first = (parent, name) => nodes(parent, name)[0] || null;
  const attr = (node, name) => node ? xmlAttr(node.tag, name) : null;
  const xmlDecode = s => s.replace(/&amp;/gi, "&").replace(/&quot;/gi, '"')
    .replace(/&apos;/gi, "'").replace(/&lt;/gi, "<").replace(/&gt;/gi, ">");
  function scopeBase(node, base) {
    const value = first(node, "BaseURL")?.text?.trim();
    return value ? url(xmlDecode(value), base) || base : base;
  }
  function fps(raw) {
    const m = /^(\d{1,4})(?:\/(\d{1,4}))?$/.exec(raw || "");
    if (!m || Number(m[2] || 1) === 0) return null;
    const n = Number(m[1]) / Number(m[2] || 1);
    return n > 0 && n <= 240 ? n : null;
  }
  function parseDash(text, base, limit) {
    limit = bounded(text, limit);
    const root = mpdTree(text), periods = nodes(root, "Period");
    if (!periods.length) throw new Error("invalid_dash");
    const rootBase = scopeBase(root, base);
    const variants = [], audioTracks = [], subtitleTracks = [];
    let periodDuration = 0, completeDuration = true;
    const dynamic = (attr(root, "type") || "").toLowerCase() === "dynamic";
    for (let periodIndex = 0; periodIndex < periods.length; periodIndex++) {
      const period = periods[periodIndex];
      const d = duration(attr(period, "duration"));
      if (d === null) completeDuration = false; else periodDuration += d;
      const basePeriod = scopeBase(period, rootBase);
      const segmentedPeriod = !!(first(period, "SegmentTemplate") || first(period, "SegmentList"));
      for (const set of nodes(period, "AdaptationSet")) {
        const baseSet = scopeBase(set, basePeriod);
        const segmentedSet = segmentedPeriod ||
          !!(first(set, "SegmentTemplate") || first(set, "SegmentList") || first(set, "SegmentBase"));
        for (const rep of nodes(set, "Representation")) {
          const mime = attr(rep, "mimeType") || attr(set, "mimeType") || "";
          const type = attr(rep, "contentType") || attr(set, "contentType") || mime.split("/")[0];
          const dest = type === "video" ? variants : type === "audio" ? audioTracks :
            (type === "text" || /subtitle|ttml|vtt/i.test(mime)) ? subtitleTracks : null;
          if (!dest || dest.length >= limit) continue;
          const segmented = segmentedSet ||
            !!(first(rep, "SegmentTemplate") || first(rep, "SegmentList") || first(rep, "SegmentBase"));
          const direct = first(rep, "BaseURL")?.text?.trim();
          const frameRateRaw = attr(rep, "frameRate") || attr(set, "frameRate");
          dest.push({
            url: direct && !segmented ? url(xmlDecode(direct), baseSet) : null,
            width: number(attr(rep, "width") || attr(set, "width")),
            height: number(attr(rep, "height") || attr(set, "height")),
            frameRate: fps(frameRateRaw), frameRateRaw: frameRateRaw || null,
            bitrate: number(attr(rep, "bandwidth") || attr(set, "bandwidth")),
            bitrateKind: "declared",
            codecs: attr(rep, "codecs") || attr(set, "codecs") || null,
            language: attr(rep, "lang") || attr(set, "lang") || null,
            periodIndex, representationId: attr(rep, "id") || null, segmented,
            role: dest === variants ? "video" : dest === audioTracks ? "audio" : "subtitle"
          });
        }
      }
    }
    const rates = [...variants, ...audioTracks];
    const bitrate = periods.length === 1 && variants.length === 1 &&
      audioTracks.length <= 1 && rates.every(x => Number.isSafeInteger(x.bitrate) && x.bitrate > 0)
      ? rates.reduce((a, x) => a + x.bitrate, 0) : null;
    return { protocol: "DASH", drm: /<ContentProtection\b/i.test(text), dynamic,
      live: dynamic, durationSeconds: dynamic ? null :
        (duration(attr(root, "mediaPresentationDuration")) || (completeDuration ? periodDuration : null)),
      bitrate, variants, audioTracks, subtitleTracks, trickplayTracks: [], periodCount: periods.length };
  }
  return { parseHls, parseDash };
})();
