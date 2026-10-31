"use strict";
/**
 * Take COPY of the G2 video page's pre#events JSONL on a developer computer.
 * Output ONLY whitelisted event labels and a SHA256 of the input. Never upload
 * any input automatically; does not communicate with device/ADB/Gecko.
 */
const fs = require("node:fs");
const crypto = require("node:crypto");
const videoEvents = new Set([
  "loadedmetadata", "before-B-assignment", "after-B-assignment",
  "error", "source-list-attached", "blob-B-assigned",
]);
function extractG2(raw) {
  if (typeof raw !== "string" || Buffer.byteLength(raw) > 131072)
    throw Error("input invalid");
  const events = [];
  let lastSequence = 0;
  let lastMs = -1;
  for (const line of raw.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const value = JSON.parse(line);
    if (value === null || Array.isArray(value) || typeof value !== "object")
      throw Error("bad event object");
    const { seq, ms, event, currentSrc, src } = value;
    if (!Number.isSafeInteger(seq) || seq <= lastSequence ||
        !Number.isSafeInteger(ms) || ms < lastMs ||
        typeof event !== "string" || !/^[A-Za-z-]+$/.test(event))
      throw Error("bad event format");
    lastSequence = seq;
    lastMs = ms;
    if (!videoEvents.has(event)) continue;
    const label = [currentSrc, src].find(v => v === "A" || v === "B" || v === "BAD") || "OTHER";
    let kind;
    if (event === "loadedmetadata")
      kind = label === "A" ? "A_loadedmetadata" : label === "B" ? "B_loadedmetadata" : null;
    else if (event === "before-B-assignment") kind = "before_B_assignment";
    else if (event === "after-B-assignment") kind = "after_B_assignment";
    else if (event === "error" && label === "BAD") kind = "invalid_B";
    else if (event === "source-list-attached") kind = "source_list";
    else if (event === "blob-B-assigned") kind = "blob_B";
    if (kind) events.push({ seq, elapsedMs: ms, source: "DOM", kind });
  }
  return {
    schema: "zavro.geckoview.g3c-g2-extract/1",
    provenance: "UNVERIFIED_BROWSER_TEXT",
    inputSha256: crypto.createHash("sha256").update(raw, "utf8").digest("hex"),
    events,
    warning: "Partial DOM timeline only. No actual Gecko/native facts or G5 verdict.",
  };
}
function main(args) {
  if (args.length !== 1) {
    process.stderr.write("Usage: node tools/gecko-fixture-g3c/extract-g2.cjs G2-VIDEO-LOG.txt\n");
    return 2;
  }
  try {
    const stat = fs.statSync(args[0]);
    if (!stat.isFile() || stat.size > 131072) throw Error("size limit");
    const result = extractG2(fs.readFileSync(args[0], "utf8"));
    process.stdout.write(JSON.stringify(result, null, 2) + "\n");
    return 0;
  } catch (_) {
    process.stderr.write("G2 log not parseable. No raw data printed.\n");
    return 2;
  }
}
if (require.main === module) process.exitCode = main(process.argv.slice(2));
module.exports = { extractG2 };
